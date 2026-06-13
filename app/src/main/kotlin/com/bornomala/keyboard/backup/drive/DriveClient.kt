package com.bornomala.keyboard.backup.drive

import com.bornomala.keyboard.backup.BackupInfo
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Google Drive REST v3 client for a single backup file, hand-rolled over
 * [HttpURLConnection] (no heavy `google-api-services-drive` dependency — matches the OTA code).
 *
 * The backup lives at `headquarter/bornomala/backup.bin`. Uses the `drive.file` scope: the app
 * can only see and manage files/folders it created, so it owns these folders. All calls take a
 * short-lived OAuth access token (minted by [GoogleAuthManager]) and run on the IO dispatcher.
 */
@Singleton
class DriveClient @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    /** Finds the existing backup file, or null if the folders/file don't exist yet. */
    suspend fun findBackup(token: String): BackupInfo? = withContext(dispatchers.io) {
        val folderId = findBackupFolder(token) ?: return@withContext null
        val q = URLEncoder.encode("name = '$FILE_NAME' and '$folderId' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,size,modifiedTime)", "UTF-8")
        val body = request("$DRIVE/files?spaces=drive&q=$q&fields=$fields&pageSize=1", "GET", token)
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
        if (files.length() == 0) return@withContext null
        val f = files.getJSONObject(0)
        BackupInfo(
            fileId = f.getString("id"),
            sizeBytes = f.optString("size", "0").toLongOrNull() ?: 0L,
            modifiedAtMillis = parseRfc3339(f.optString("modifiedTime")),
        )
    }

    suspend fun download(token: String, fileId: String): ByteArray = withContext(dispatchers.io) {
        val conn = open("$DRIVE/files/$fileId?alt=media", "GET", token)
        try {
            requireOk(conn)
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Uploads [bytes] as the backup. Creates the folder path + file when [existingId] is null,
     * otherwise overwrites that file's content. Returns the file id.
     */
    suspend fun upload(token: String, bytes: ByteArray, existingId: String?): String =
        withContext(dispatchers.io) {
            if (existingId != null) {
                updateMedia(token, existingId, bytes)
            } else {
                createMultipart(token, bytes, ensureBackupFolder(token))
            }
        }

    suspend fun delete(token: String, fileId: String) = withContext(dispatchers.io) {
        val conn = open("$DRIVE/files/$fileId", "DELETE", token)
        try {
            if (conn.responseCode !in 200..299 && conn.responseCode != 404) {
                throw IOException("Drive delete failed: HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    // --- folders ----------------------------------------------------------------------

    /** Resolves `headquarter/bornomala`, returning null if either folder is absent. */
    private fun findBackupFolder(token: String): String? {
        val top = findFolder(token, FOLDER_TOP, "root") ?: return null
        return findFolder(token, FOLDER_SUB, top)
    }

    /** Resolves `headquarter/bornomala`, creating either folder if missing. */
    private fun ensureBackupFolder(token: String): String {
        val top = findFolder(token, FOLDER_TOP, "root") ?: createFolder(token, FOLDER_TOP, "root")
        return findFolder(token, FOLDER_SUB, top) ?: createFolder(token, FOLDER_SUB, top)
    }

    private fun findFolder(token: String, name: String, parentId: String): String? {
        val q = URLEncoder.encode(
            "name = '$name' and mimeType = '$FOLDER_MIME' and '$parentId' in parents and trashed = false",
            "UTF-8",
        )
        val fields = URLEncoder.encode("files(id)", "UTF-8")
        val files = JSONObject(request("$DRIVE/files?spaces=drive&q=$q&fields=$fields&pageSize=1", "GET", token))
            .optJSONArray("files") ?: return null
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    private fun createFolder(token: String, name: String, parentId: String): String {
        val conn = open("$DRIVE/files", "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put(parentId))
            .toString()
        try {
            conn.outputStream.use { it.write(meta.toByteArray(Charsets.UTF_8)) }
            requireOk(conn)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    // --- file upload ------------------------------------------------------------------

    private fun createMultipart(token: String, bytes: ByteArray, parentId: String): String {
        val conn = open("$UPLOAD/files?uploadType=multipart", "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        val meta = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", MIME)
            .put("parents", JSONArray().put(parentId))
            .toString()
        try {
            conn.outputStream.use { out ->
                out.write(("--$BOUNDARY\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").toByteArray())
                out.write(meta.toByteArray(Charsets.UTF_8))
                out.write(("\r\n--$BOUNDARY\r\nContent-Type: $MIME\r\n\r\n").toByteArray())
                out.write(bytes)
                out.write(("\r\n--$BOUNDARY--\r\n").toByteArray())
            }
            requireOk(conn)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("id")
        } finally {
            conn.disconnect()
        }
    }

    private fun updateMedia(token: String, fileId: String, bytes: ByteArray): String {
        val conn = open("$UPLOAD/files/$fileId?uploadType=media", "PATCH", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", MIME)
        try {
            conn.outputStream.use { it.write(bytes) }
            requireOk(conn)
            return fileId
        } finally {
            conn.disconnect()
        }
    }

    // --- http helpers -----------------------------------------------------------------

    private fun request(url: String, method: String, token: String): String {
        val conn = open(url, method, token)
        try {
            requireOk(conn)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            if (method == "PATCH") {
                requestMethod = "POST"
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                requestMethod = method
            }
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun requireOk(conn: HttpURLConnection) {
        if (conn.responseCode !in 200..299) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            throw IOException("Drive ${conn.requestMethod} failed: HTTP ${conn.responseCode} ${err.orEmpty()}")
        }
    }

    private fun parseRfc3339(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    companion object {
        const val FILE_NAME = "backup.bin"
        private const val FOLDER_TOP = "headquarter"
        private const val FOLDER_SUB = "bornomala"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val MIME = "application/octet-stream"
        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BOUNDARY = "bornomalaBackupBoundary7MA4YWxkTrZu0gW"
    }
}

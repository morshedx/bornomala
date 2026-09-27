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
 * Backups live in the Drive **app-data folder** (`drive.appdata` scope): a hidden, app-private
 * space that does not appear in the user's Drive file list, as WhatsApp does. Two names:
 * [PLAIN_FILE] (no passphrase; protected by the Google account) and [ENCRYPTED_FILE] (encrypted
 * with the user's passphrase before upload). Only one is kept at a time.
 *
 * Older versions stored an encrypted backup at `headquarter/bornomala/backup.bin` in the visible
 * Drive (`drive.file` scope, [findLegacy]); it is still found and restorable, never overwritten.
 *
 * All calls take a short-lived OAuth access token (minted by [GoogleAuthManager]) and run on
 * the IO dispatcher.
 */
@Singleton
class DriveClient @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    /** Finds the app-data backup called [name] ([PLAIN_FILE] or [ENCRYPTED_FILE]), or null. */
    suspend fun findAppData(token: String, name: String): BackupInfo? = withContext(dispatchers.io) {
        queryFile(token, "name = '$name' and 'appDataFolder' in parents and trashed = false", APPDATA_SPACE)
            ?.copy(encrypted = name == ENCRYPTED_FILE)
    }

    /** Finds the pre-app-data encrypted backup (`headquarter/bornomala/backup.bin`), or null. */
    suspend fun findLegacy(token: String): BackupInfo? = withContext(dispatchers.io) {
        val folderId = findBackupFolder(token) ?: return@withContext null
        queryFile(token, "name = '$LEGACY_FILE' and '$folderId' in parents and trashed = false", DRIVE_SPACE)
            ?.copy(encrypted = true, legacy = true)
    }

    private fun queryFile(token: String, query: String, space: String): BackupInfo? {
        val q = URLEncoder.encode(query, "UTF-8")
        val fields = URLEncoder.encode("files(id,size,modifiedTime)", "UTF-8")
        val body = request("$DRIVE/files?spaces=$space&q=$q&fields=$fields&pageSize=1", "GET", token)
        val files = JSONObject(body).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val f = files.getJSONObject(0)
        return BackupInfo(
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
     * Uploads [bytes] to the app-data file [name], overwriting it when [existingId] is given and
     * creating it otherwise. Returns the file id.
     */
    suspend fun uploadAppData(token: String, name: String, bytes: ByteArray, existingId: String?): String =
        withContext(dispatchers.io) {
            if (existingId != null) {
                updateMedia(token, existingId, bytes)
            } else {
                createMultipart(token, name, bytes, APPDATA_PARENT)
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

    private fun findFolder(token: String, name: String, parentId: String): String? {
        val q = URLEncoder.encode(
            "name = '$name' and mimeType = '$FOLDER_MIME' and '$parentId' in parents and trashed = false",
            "UTF-8",
        )
        val fields = URLEncoder.encode("files(id)", "UTF-8")
        val files = JSONObject(request("$DRIVE/files?spaces=$DRIVE_SPACE&q=$q&fields=$fields&pageSize=1", "GET", token))
            .optJSONArray("files") ?: return null
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    // --- file upload ------------------------------------------------------------------

    private fun createMultipart(token: String, name: String, bytes: ByteArray, parentId: String): String {
        val conn = open("$UPLOAD/files?uploadType=multipart", "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        val meta = JSONObject()
            .put("name", name)
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
        /** App-data backup without a passphrase (settings/dictionary/clipboard JSON). */
        const val PLAIN_FILE = "backup.json"

        /** App-data backup encrypted with the user's passphrase ([com.bornomala.keyboard.backup.crypto.CryptoBox]). */
        const val ENCRYPTED_FILE = "backup.enc"

        private const val LEGACY_FILE = "backup.bin"
        private const val APPDATA_SPACE = "appDataFolder"
        private const val APPDATA_PARENT = "appDataFolder"
        private const val DRIVE_SPACE = "drive"
        private const val FOLDER_TOP = "headquarter"
        private const val FOLDER_SUB = "bornomala"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val MIME = "application/octet-stream"
        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BOUNDARY = "bornomalaBackupBoundary7MA4YWxkTrZu0gW"
    }
}

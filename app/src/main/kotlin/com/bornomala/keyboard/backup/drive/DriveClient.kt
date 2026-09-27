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
 * space that does not appear in the user's Drive file list, as WhatsApp does, protected by the
 * user's Google account.
 *
 * All calls take a short-lived OAuth access token (minted by [GoogleAuthManager]) and run on
 * the IO dispatcher.
 */
@Singleton
class DriveClient @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    /** Finds the app-data file called [name], or null. */
    suspend fun findAppData(token: String, name: String): BackupInfo? = withContext(dispatchers.io) {
        val q = URLEncoder.encode("name = '$name' and 'appDataFolder' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,size,modifiedTime)", "UTF-8")
        val body = request("$DRIVE/files?spaces=$APPDATA_SPACE&q=$q&fields=$fields&pageSize=1", "GET", token)
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
        /** The backup: settings, dictionary, picks and clipboard as JSON. */
        const val BACKUP_FILE = "backup.json"

        /**
         * The passphrase-encrypted backup older versions could write. Nothing reads it any more;
         * it is deleted on the next backup or delete so no unreadable copy lingers in Drive.
         */
        const val OLD_ENCRYPTED_FILE = "backup.enc"

        private const val APPDATA_SPACE = "appDataFolder"
        private const val APPDATA_PARENT = "appDataFolder"
        private const val MIME = "application/octet-stream"
        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BOUNDARY = "bornomalaBackupBoundary7MA4YWxkTrZu0gW"
    }
}

package com.bornomala.keyboard.backup.drive

import com.bornomala.keyboard.backup.BackupInfo
import com.bornomala.keyboard.core.dispatchers.DispatcherProvider
import kotlinx.coroutines.withContext
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
 * Uses the `drive.file` scope: the app can only see and manage files it created, and the
 * backup is a normal, user-visible file in the user's Drive. All calls take a short-lived
 * OAuth access token (minted by [GoogleAuthManager]) and run on the IO dispatcher.
 */
@Singleton
class DriveClient @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {
    /** Finds the existing backup file, or null if none exists yet. */
    suspend fun findBackup(token: String): BackupInfo? = withContext(dispatchers.io) {
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,size,modifiedTime)", "UTF-8")
        val url = "$DRIVE/files?spaces=drive&q=$q&fields=$fields&pageSize=1"
        val body = request(url, "GET", token)
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
        if (files.length() == 0) return@withContext null
        val f = files.getJSONObject(0)
        BackupInfo(
            fileId = f.getString("id"),
            sizeBytes = f.optString("size", "0").toLongOrNull() ?: 0L,
            modifiedAtMillis = parseRfc3339(f.optString("modifiedTime")),
        )
    }

    /** Downloads the raw bytes of [fileId]. */
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
     * Uploads [bytes] as the backup. Creates the file (multipart, to set its name) when
     * [existingId] is null, otherwise overwrites that file's content (media upload).
     * Returns the file id.
     */
    suspend fun upload(token: String, bytes: ByteArray, existingId: String?): String =
        withContext(dispatchers.io) {
            if (existingId == null) createMultipart(token, bytes) else updateMedia(token, existingId, bytes)
        }

    suspend fun delete(token: String, fileId: String) = withContext(dispatchers.io) {
        val conn = open("$DRIVE/files/$fileId", "DELETE", token)
        try {
            // 204 No Content on success; 404 means already gone — both fine.
            if (conn.responseCode !in 200..299 && conn.responseCode != 404) {
                throw IOException("Drive delete failed: HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun createMultipart(token: String, bytes: ByteArray): String {
        val conn = open("$UPLOAD/files?uploadType=multipart", "POST", token)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
        val meta = JSONObject().put("name", FILE_NAME).put("mimeType", MIME).toString()
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
            // HttpURLConnection has no native PATCH; tunnel it (Drive honours the override).
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

    /** Parses an RFC-3339 timestamp (Drive `modifiedTime`) to epoch millis; 0 on failure. */
    private fun parseRfc3339(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    companion object {
        const val FILE_NAME = "bornomala-backup.bin"
        private const val MIME = "application/octet-stream"
        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val BOUNDARY = "bornomalaBackupBoundary7MA4YWxkTrZu0gW"
    }
}

package fr.geoking.gaston.feature.logging

import android.content.Context
import fr.geoking.gaston.shared.logging.DebugPayloadStorage
import java.io.File

class AndroidDebugPayloadStorage(private val context: Context) : DebugPayloadStorage {
    private val payloadDir: File
        get() = File(context.cacheDir, "debug_log_payloads").also { if (!it.exists()) it.mkdirs() }

    override fun storePayload(logId: String, isRequest: Boolean, body: String) {
        try {
            val type = if (isRequest) "req" else "resp"
            val file = File(payloadDir, "${logId}_$type.txt")
            file.writeText(body)
        } catch (_: Throwable) {
            // Ignore write errors to prevent logging from crashing app
        }
    }

    override fun getPayload(logId: String, isRequest: Boolean): String? {
        return try {
            val type = if (isRequest) "req" else "resp"
            val file = File(payloadDir, "${logId}_$type.txt")
            if (file.exists()) file.readText() else null
        } catch (_: Throwable) {
            null
        }
    }

    override fun deletePayloads(logId: String) {
        try {
            File(payloadDir, "${logId}_req.txt").delete()
            File(payloadDir, "${logId}_resp.txt").delete()
        } catch (_: Throwable) {}
    }

    override fun clear() {
        try {
            payloadDir.deleteRecursively()
        } catch (_: Throwable) {}
    }
}

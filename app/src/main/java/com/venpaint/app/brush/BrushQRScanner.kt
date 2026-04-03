package com.venpaint.app.brush

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.journeyapps.barcodescanner.CaptureActivity
import java.util.Base64

/**
 * Scans QR codes to import brush parameters.
 * Supports:
 * - VenPaint format: "VP:<base64-encoded-json>"
 * - ibisPaint format: "https://ibispaint.com/my/brush/XXXXXX"
 */
class BrushQRScanner(private val context: Context) {

    companion object {
        private const val VP_PREFIX = "VP:"
        private const val IBISPAINT_PREFIX = "https://ibispaint.com/my/"
        private const val IBISPAINT_BRUSH_PREFIX = "https://ibispaint.com/my/brush/"
    }

    private val gson = Gson()

    /**
     * Launch the QR scanner activity.
     */
    fun launchScanner(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(context, CaptureActivity::class.java).apply {
            putExtra("SCAN_FRAME", true)
            putExtra("SCAN_CAMERA_ID", 0)
        }
        launcher.launch(intent)
    }

    /**
     * Parse the result from a QR code scan.
     * Returns a ScanResult indicating what was found.
     */
    fun parseScanResult(contents: String?): ScanResult {
        if (contents.isNullOrBlank()) {
            return ScanResult(ScanType.INVALID, null, null)
        }

        return when {
            contents.startsWith(VP_PREFIX) -> parseVenPaintQR(contents)
            contents.startsWith(IBISPAINT_PREFIX) -> parseIbisPaintQR(contents)
            else -> ScanResult(ScanType.UNKNOWN, null, contents)
        }
    }

    /**
     * Parse a VenPaint QR code.
     */
    private fun parseVenPaintQR(qrData: String): ScanResult {
        return try {
            val encoded = qrData.removePrefix(VP_PREFIX)
            val json = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, type)

            val brush = Brush().companionFromMap(map)
            ScanResult(ScanType.VENPAINT_BRUSH, brush, null)
        } catch (e: Exception) {
            e.printStackTrace()
            ScanResult(ScanType.ERROR, null, "Failed to parse VenPaint QR: ${e.message}")
        }
    }

    /**
     * Parse an ibisPaint QR code.
     * ibisPaint QR codes contain URLs. We provide partial compatibility by opening in browser.
     */
    private fun parseIbisPaintQR(url: String): ScanResult {
        return if (url.startsWith(IBISPAINT_BRUSH_PREFIX)) {
            // This is an ibisPaint brush URL
            val brushId = url.removePrefix(IBISPAINT_BRUSH_PREFIX).trimEnd('/')
            ScanResult(
                ScanType.IBISPAINT_BRUSH,
                null,
                url
            )
        } else {
            // Other ibisPaint URL - just provide the link
            ScanResult(ScanType.IBISPAINT_LINK, null, url)
        }
    }

    /**
     * Open an ibisPaint URL in the browser.
     */
    fun openIbisPaintUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Try to import a brush from clipboard text.
     */
    fun importFromClipboard(text: String): ScanResult {
        return parseScanResult(text)
    }
}

/**
 * Result of a QR scan operation.
 */
sealed class ScanResult(
    val type: ScanType,
    val brush: Brush?,
    val metadata: String?
)

enum class ScanType {
    /** Successfully parsed a VenPaint brush QR */
    VENPAINT_BRUSH,
    /** Found an ibisPaint brush URL */
    IBISPAINT_BRUSH,
    /** Found another ibisPaint URL */
    IBISPAINT_LINK,
    /** Unknown QR content */
    UNKNOWN,
    /** Invalid/empty QR */
    INVALID,
    /** Error during parsing */
    ERROR
}

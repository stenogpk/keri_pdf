package com.keripdf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedPdf: Uri? = null
    private lateinit var fileLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var compressButton: Button
    private lateinit var openButton: Button
    private var lastSavedPdf: Uri? = null
    private lateinit var targetSize: EditText
    private lateinit var modeGroup: RadioGroup
    private var pendingMode = 1
    private var pendingTargetKb: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFFF7F8FC.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "Keri PDF"
            textSize = 30f
            setTextColor(0xFF172554.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Compress PDFs. Keep files on your device."
            textSize = 15f
            setTextColor(0xFF475569.toInt())
            setPadding(0, 4, 0, 24)
        })

        content.addView(Button(this).apply {
            text = "Choose PDF"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                startActivityForResult(intent, REQUEST_OPEN)
            }
        })
        fileLabel = TextView(this).apply {
            text = "No PDF selected"
            textSize = 14f
            setTextColor(0xFF334155.toInt())
            setPadding(0, 12, 0, 18)
        }
        content.addView(fileLabel)

        content.addView(sectionTitle("Compression mode"))
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("Light", "Recommended", "Strong", "Exact target size (KB)").forEachIndexed { index, label ->
            modeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 15f
                isChecked = index == 1
                tag = index
            })
        }
        content.addView(modeGroup)
        targetSize = EditText(this).apply {
            hint = "Target size in KB (e.g. 500)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        content.addView(targetSize)

        content.addView(TextView(this).apply {
            text = "Important: this first version converts each PDF page into an image. Text may no longer be selectable/searchable, and fine details can soften. Keep your original PDF."
            textSize = 13f
            setTextColor(0xFF92400E.toInt())
            setBackgroundColor(0xFFFFF7ED.toInt())
            setPadding(14, 14, 14, 14)
        })

        compressButton = Button(this).apply {
            text = "Compress PDF"
            isEnabled = false
            setOnClickListener {
                if (selectedPdf == null) return@setOnClickListener
                pendingMode = selectedMode()
                pendingTargetKb = targetSize.text.toString().toIntOrNull()
                if (pendingMode == 3 && (pendingTargetKb ?: 0) <= 0) {
                    targetSize.error = "Enter a target size in KB"
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Confirm image-based compression")
                    .setMessage("This method rasterizes PDF pages. Searchable text and vector content may be lost. Continue with a copy? Your original file will not be changed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ ->
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, "KeriPDF_compressed.pdf")
                        }
                        startActivityForResult(intent, REQUEST_SAVE)
                    }
                    .show()
            }
        }
        content.addView(compressButton)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progress)
        statusLabel = TextView(this).apply {
            text = "Your PDF stays on this device."
            textSize = 14f
            setTextColor(0xFF475569.toInt())
            setPadding(0, 12, 0, 0)
        }
        content.addView(statusLabel)
        openButton = Button(this).apply {
            text = "Open compressed PDF"
            visibility = View.GONE
            setOnClickListener { openLastCompressedPdf() }
        }
        content.addView(openButton)

        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFF0F172A.toInt())
        setPadding(0, 10, 0, 8)
    }

    private fun selectedMode(): Int {
        val checked = modeGroup.checkedRadioButtonId
        return modeGroup.findViewById<RadioButton>(checked)?.tag as? Int ?: 1
    }

    @Deprecated("Deprecated in Android API; retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQUEST_OPEN) {
            selectedPdf = uri
            fileLabel.text = "Selected PDF: " + (uri.lastPathSegment ?: "document.pdf")
            statusLabel.text = "Ready to compress"
            compressButton.isEnabled = true
        } else if (requestCode == REQUEST_SAVE) {
            startCompression(uri)
        }
    }

    private fun startCompression(outputUri: Uri) {
        val inputUri = selectedPdf ?: return
        compressButton.isEnabled = false
        progress.visibility = View.VISIBLE
        statusLabel.text = "Compressing locally…"

        executor.execute {
            try {
                val source = File(cacheDir, "keri_input_" + System.currentTimeMillis() + ".pdf")
                contentResolver.openInputStream(inputUri)?.use { input ->
                    source.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not read the selected PDF")

                val sourceBytes = source.length()
                val result = compressPdf(source, pendingMode, pendingTargetKb)
                contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                    result.file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not save the compressed PDF")

                lastSavedPdf = outputUri
                source.delete()
                result.file.delete()
                runOnUiThread {
                    progress.visibility = View.GONE
                    compressButton.isEnabled = true
                    openButton.visibility = View.VISIBLE
                    val saved = max(0L, sourceBytes - result.bytes)
                    val percent = if (sourceBytes > 0) saved * 100.0 / sourceBytes else 0.0
                    var message = "Original: " + formatBytes(sourceBytes) +
                        "\nCompressed: " + formatBytes(result.bytes) +
                        "\nSaved: " + formatBytes(saved) + " (" + DecimalFormat("0.0").format(percent) + "%)"
                    if (result.targetReached == false) message += "\nTarget could not be reached at the available quality settings."
                    statusLabel.text = message
                    Toast.makeText(this, "Compressed PDF saved", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    compressButton.isEnabled = true
                    openButton.visibility = View.GONE
                    statusLabel.text = "Compression failed: " + (error.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private data class CompressionResult(val file: File, val bytes: Long, val targetReached: Boolean?)

    private fun compressPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        val targetBytes = if (mode == 3) (targetKb ?: 0).toLong() * 1024L else null
        val presets = when (mode) {
            0 -> listOf(150 to 82)
            1 -> listOf(120 to 74)
            2 -> listOf(85 to 58)
            // Exact Target mode tries progressively smaller outputs. The last steps are aggressive;
            // users should inspect fine text and diagrams before relying on the result.
            else -> listOf(
                160 to 84,
                130 to 76,
                105 to 68,
                85 to 58,
                68 to 48,
                52 to 38
            )
        }
        var best: File? = null
        for ((dpi, quality) in presets) {
            val candidate = File(cacheDir, "keri_candidate_" + dpi + "_" + quality + ".pdf")
            renderCompressed(source, candidate, dpi, quality)
            best?.delete()
            best = candidate
            if (targetBytes != null && candidate.length() <= targetBytes) {
                return CompressionResult(candidate, candidate.length(), true)
            }
            if (targetBytes == null) break
        }
        val finalFile = best ?: error("No compression output was created")
        return CompressionResult(finalFile, finalFile.length(), if (targetBytes == null) null else false)
    }

    private fun renderCompressed(source: File, destination: File, dpi: Int, quality: Int) {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val outputPdf = PdfDocument()
                try {
                    for (index in 0 until renderer.pageCount) {
                        val page = renderer.openPage(index)
                        try {
                            val width = max(1, page.width * dpi / 72)
                            val height = max(1, page.height * dpi / 72)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, Rect(0, 0, width, height), null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val jpegBytes = ByteArrayOutputStream().use { buffer ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
                                    buffer.toByteArray()
                                }
                                val compressedBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                    ?: error("Could not encode PDF page")
                                try {
                                    val info = PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create()
                                    val outputPage = outputPdf.startPage(info)
                                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                    outputPage.canvas.drawBitmap(compressedBitmap, null, RectF(0f, 0f, page.width.toFloat(), page.height.toFloat()), paint)
                                    outputPdf.finishPage(outputPage)
                                } finally {
                                    compressedBitmap.recycle()
                                    bitmap.recycle()
                                }
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    }
                    FileOutputStream(destination).use { outputPdf.writeTo(it) }
                } finally {
                    outputPdf.close()
                }
            }
        }
    }

    private fun openLastCompressedPdf() {
        val uri = lastSavedPdf ?: run {
            Toast.makeText(this, "No compressed PDF available yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open compressed PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "No PDF viewer app found on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024L * 1024L) {
            DecimalFormat("0.00").format(bytes / (1024.0 * 1024.0)) + " MB"
        } else {
            DecimalFormat("0.0").format(bytes / 1024.0) + " KB"
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_OPEN = 1001
        private const val REQUEST_SAVE = 1002
    }
}

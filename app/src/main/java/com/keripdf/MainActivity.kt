package com.keripdf

import android.app.Activity
import android.graphics.Bitmap
import android.app.AlertDialog
import android.content.Intent
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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
    private var pendingMode = 0
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
        listOf("Quality-preserving (recommended)", "Check exact target size (KB)").forEachIndexed { index, label ->
            modeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 15f
                isChecked = index == 0
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
            text = "Keri PDF optimizes embedded images while keeping page text and vector content intact. Transparent/masked images are left unchanged. Very small target sizes may not be achievable without visible image quality loss."
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
                if (pendingMode == 1 && (pendingTargetKb ?: 0) <= 0) {
                    targetSize.error = "Enter a target size in KB"
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Confirm quality-preserving compression")
                    .setMessage("Keri PDF will optimize embedded images, not flatten whole pages. Text and vector content remain in the PDF. Images may become softer at lower target sizes. Continue with a copy? Your original file will not be changed.")
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
        return modeGroup.findViewById<RadioButton>(checked)?.tag as? Int ?: 0
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

    /** Re-encode embedded raster images only; leave page text and vector streams intact. */
    private fun compressPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        val targetBytes = if (mode == 1) (targetKb ?: 0).toLong() * 1024L else Long.MAX_VALUE
        val qualities = if (mode == 1) listOf(88, 80, 72, 64, 56, 48) else listOf(82)
        var bestFile: File? = null
        var bestBytes = Long.MAX_VALUE
        var reached = false
        for (quality in qualities) {
            val candidate = File(cacheDir, "keri_opt_" + System.currentTimeMillis() + "_" + quality + ".pdf")
            try {
                PDFBoxResourceLoader.init(applicationContext)
                PDDocument.load(source).use { document ->
                    for (page in document.pages) {
                        page.resources?.let { optimizeResources(document, it, quality, HashSet<Int>()) }
                    }
                    document.save(candidate)
                }
                val size = candidate.length()
                if (size < bestBytes) {
                    bestFile?.delete()
                    bestFile = candidate
                    bestBytes = size
                } else candidate.delete()
                if (bestBytes <= targetBytes) { reached = true; break }
            } catch (e: Exception) {
                candidate.delete()
                if (bestFile == null) throw e
            }
        }
        val resultFile = bestFile ?: error("PDF optimization did not produce an output")
        if (source.length() - resultFile.length() <= 0L) {
            val unchanged = File(cacheDir, "keri_unchanged_" + System.currentTimeMillis() + ".pdf")
            source.copyTo(unchanged, overwrite = true)
            resultFile.delete()
            return CompressionResult(unchanged, unchanged.length(), if (mode == 1) false else null)
        }
        return CompressionResult(resultFile, resultFile.length(), if (mode == 1) reached else null)
    }

    private fun optimizeResources(
        document: PDDocument,
        resources: PDResources,
        quality: Int,
        visitedForms: MutableSet<Int>
    ) {
        for (name in resources.xObjectNames.toList()) {
            try {
                when (val item = resources.getXObject(name)) {
                    is PDImageXObject -> {
                        val dict = item.cosObject
                        if (dict.containsKey(COSName.SMASK) || dict.containsKey(COSName.MASK) || dict.getBoolean(COSName.IMAGE_MASK, false)) continue
                        val bitmap = item.image ?: continue
                        if (bitmap.width < 160 || bitmap.height < 160) continue
                        val buffer = java.io.ByteArrayOutputStream()
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer)) continue
                        val bytes = buffer.toByteArray()
                        val oldLength = dict.getLong(COSName.LENGTH, Long.MAX_VALUE)
                        if (bytes.size.toLong() >= oldLength * 0.95) continue
                        val replacement = PDImageXObject.createFromByteArray(document, bytes, "keri-image.jpg")
                        resources.put(name, replacement)
                    }
                    is PDFormXObject -> {
                        val id = System.identityHashCode(item.cosObject)
                        if (visitedForms.add(id)) item.resources?.let { optimizeResources(document, it, quality, visitedForms) }
                    }
                }
            } catch (_: Exception) { /* Skip unsupported images; preserve the rest of the document. */ }
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

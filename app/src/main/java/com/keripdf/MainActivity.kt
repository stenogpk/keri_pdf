package com.keripdf

import android.app.Activity
import android.graphics.Bitmap
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowInsets
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
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.text.PDFTextStripper
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
    private lateinit var shareButton: Button
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
        window.statusBarColor = 0xFFF4F7FB.toInt()
        window.navigationBarColor = 0xFFF4F7FB.toInt()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F7FB.toInt())
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content)
        page.setOnApplyWindowInsetsListener { _, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
            val bottomInset = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom else insets.systemWindowInsetBottom
            scroll.setPadding(dp(18), dp(16) + topInset, dp(18), dp(12) + bottomInset)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = rounded(0xFF172554.toInt(), 24)
        }
        header.addView(TextView(this).apply {
            text = "PDF TOOLS  •  OFFLINE"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(0xFFB9C8F5.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "KeRi PDF\nCompressor"
            textSize = 29f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(1).toFloat(), 1.0f)
            setPadding(0, dp(8), 0, dp(5))
        })
        header.addView(TextView(this).apply {
            text = "Compress PDFs. Keep your files on your device."
            textSize = 14f
            setTextColor(0xFFDCE5FF.toInt())
            setLineSpacing(dp(3).toFloat(), 1.0f)
        })
        content.addView(header, fullWidth())
        addGap(content, 18)

        val fileCard = makeCard()
        fileCard.addView(sectionTitle("SELECT YOUR PDF"))
        fileCard.addView(TextView(this).apply {
            text = "Choose a document from your device to get started."
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(12))
        })
        val chooseButton = actionButton("＋   Choose PDF", 0xFFE8EEF9.toInt(), 0xFF1E3A8A.toInt())
        chooseButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            startActivityForResult(intent, REQUEST_OPEN)
        }
        fileCard.addView(chooseButton, fullWidth())
        fileLabel = TextView(this).apply {
            text = "No PDF selected"
            textSize = 13f
            setTextColor(0xFF334155.toInt())
            setLineSpacing(dp(2).toFloat(), 1.0f)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = rounded(0xFFF1F5F9.toInt(), 12)
        }
        val fileParams = fullWidth().apply { topMargin = dp(12) }
        fileCard.addView(fileLabel, fileParams)
        content.addView(fileCard, fullWidth())
        addGap(content, 14)

        val modeCard = makeCard()
        modeCard.addView(sectionTitle("COMPRESSION MODE"))
        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        listOf("Quality-preserving (recommended)", "Check exact target size (KB)").forEachIndexed { index, label ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                minHeight = dp(48)
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setTextColor(0xFF1E293B.toInt())
                buttonTintList = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(0xFF0D9488.toInt(), 0xFF94A3B8.toInt())
                )
                isChecked = index == 0
                tag = index
            }
            modeGroup.addView(radio, fullWidth())
        }
        modeCard.addView(modeGroup)
        targetSize = EditText(this).apply {
            hint = "Target size in KB (e.g. 200)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            textSize = 16f
            setTextColor(0xFF0F172A.toInt())
            setHintTextColor(0xFF94A3B8.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xFFF8FAFC.toInt(), 12, 0xFFCBD5E1.toInt())
        }
        val targetParams = fullWidth().apply { topMargin = dp(10) }
        modeCard.addView(targetSize, targetParams)
        modeCard.addView(TextView(this).apply {
            text = "The requested KB limit is checked against the final saved PDF. If it cannot be reached at the available quality settings, the app will tell you."
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(dp(2), dp(10), dp(2), 0)
        })
        content.addView(modeCard, fullWidth())
        addGap(content, 14)

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(0xFFFFF7ED.toInt(), 14, 0xFFFED7AA.toInt())
        }
        note.addView(TextView(this).apply {
            text = "QUALITY NOTE"
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF9A3412.toInt())
        })
        note.addView(TextView(this).apply {
            text = "Very small file sizes can require image quality reduction. Your original PDF is never overwritten."
            textSize = 12f
            setTextColor(0xFF9A3412.toInt())
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(0, dp(5), 0, 0)
        })
        content.addView(note, fullWidth())
        addGap(content, 16)

        compressButton = actionButton("Compress PDF", 0xFF0F766E.toInt(), 0xFFFFFFFF.toInt()).apply {
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
                    .setTitle("Confirm compression")
                    .setMessage("Keri PDF will optimize the document and save a separate copy. Your original file will not be changed. Continue?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ ->
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, "KeRiPDF_compressed.pdf")
                        }
                        startActivityForResult(intent, REQUEST_SAVE)
                    }
                    .show()
            }
        }
        content.addView(compressButton, fullWidth())
        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        val progressParams = fullWidth().apply {
            topMargin = dp(12)
            bottomMargin = dp(4)
        }
        content.addView(progress, progressParams)

        val resultCard = makeCard()
        resultCard.addView(sectionTitle("COMPRESSION RESULT"))
        statusLabel = TextView(this).apply {
            text = "Your PDF stays on this device. Select a file to begin."
            textSize = 14f
            setTextColor(0xFF475569.toInt())
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(0, dp(4), 0, dp(4))
        }
        resultCard.addView(statusLabel, fullWidth())
        openButton = actionButton("Open compressed PDF", 0xFFE0F2FE.toInt(), 0xFF075985.toInt()).apply {
            visibility = View.GONE
            setOnClickListener { openLastCompressedPdf() }
        }
        val openParams = fullWidth().apply { topMargin = dp(12) }
        resultCard.addView(openButton, openParams)
        shareButton = actionButton("Share PDF", 0xFFDCFCE7.toInt(), 0xFF166534.toInt()).apply {
            visibility = View.GONE
            setOnClickListener { shareLastCompressedPdf() }
        }
        val shareParams = fullWidth().apply { topMargin = dp(10) }
        resultCard.addView(shareButton, shareParams)
        val resultParams = fullWidth().apply { topMargin = dp(14) }
        content.addView(resultCard, resultParams)

        addGap(content, 18)
        content.addView(TextView(this).apply {
            text = "Developed by Shartendu"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF334155.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(6))
        }, fullWidth())
        content.addView(TextView(this).apply {
            text = "KeRi PDF Compressor  •  Files stay on your device"
            textSize = 11f
            setTextColor(0xFF94A3B8.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(14))
        }, fullWidth())

        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        page.requestApplyInsets()
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.10f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFF64748B.toInt())
        setPadding(0, 0, 0, dp(12))
    }

    private fun makeCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(0xFFFFFFFF.toInt(), 18, 0xFFE2E8F0.toInt())
        elevation = dp(2).toFloat()
    }

    private fun actionButton(label: String, backgroundColor: Int, textColor: Int) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(textColor)
        minHeight = dp(50)
        setPadding(dp(16), dp(11), dp(16), dp(11))
        background = rounded(backgroundColor, 14)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun addGap(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

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
                    shareButton.visibility = View.VISIBLE
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
                    shareButton.visibility = View.GONE
                    statusLabel.text = "Compression failed: " + (error.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private data class CompressionResult(val file: File, val bytes: Long, val targetReached: Boolean?)

    /**
     * Text PDFs keep their original text/vector structure. Image-only scanned PDFs use a
     * dedicated page-rendering path so the scan itself can actually be recompressed.
     */
    private fun compressPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        PDFBoxResourceLoader.init(applicationContext)
        PDDocument.load(source).use { original ->
            val hasSelectableText = try { PDFTextStripper().getText(original).trim().isNotEmpty() } catch (_: Exception) { true }
            if (!hasSelectableText) return compressScannedPdf(source, mode, targetKb)
        }
        return compressTextPdf(source, mode, targetKb)
    }

    private fun compressScannedPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        val target = if (mode == 1) (targetKb ?: 0).toLong() * 1024L else Long.MAX_VALUE
        val candidates = if (mode == 1) listOf(115 to 76, 110 to 78, 120 to 72, 105 to 80, 100 to 82, 110 to 74, 100 to 78, 95 to 80, 90 to 82, 85 to 80)
            else listOf(220 to 90)
        var best: File? = null
        var bestSize = Long.MAX_VALUE
        var reached = false
        for ((dpi, quality) in candidates) {
            val output = File(cacheDir, "keri_scan_" + System.currentTimeMillis() + "_" + dpi + "_" + quality + ".pdf")
            try {
                PDDocument.load(source).use { input ->
                    val renderer = PDFRenderer(input)
                    PDDocument().use { result ->
                        for (index in 0 until input.numberOfPages) {
                            val sourcePage = input.getPage(index)
                            val bitmap = renderer.renderImageWithDPI(index, dpi.toFloat(), ImageType.RGB)
                            val jpeg = java.io.ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpeg)
                            bitmap.recycle()
                            val image = PDImageXObject.createFromByteArray(result, jpeg.toByteArray(), "scan-page.jpg")
                            val page = PDPage(sourcePage.mediaBox)
                            result.addPage(page)
                            PDPageContentStream(result, page).use { stream ->
                                stream.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                            }
                        }
                        result.save(output)
                    }
                }
                if (output.length() < bestSize) { best?.delete(); best = output; bestSize = output.length() } else output.delete()
                if (bestSize <= target) { reached = true; break }
            } catch (e: Exception) { output.delete(); if (best == null) throw e }
        }
        val finalFile = best ?: error("Could not compress scanned PDF")
        if (finalFile.length() >= source.length()) {
            finalFile.delete()
            val copy = File(cacheDir, "keri_scan_original_" + System.currentTimeMillis() + ".pdf")
            source.copyTo(copy, overwrite = true)
            return CompressionResult(copy, copy.length(), if (mode == 1) false else null)
        }
        return CompressionResult(finalFile, finalFile.length(), if (mode == 1) reached else null)
    }

    private fun compressTextPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        val targetBytes = if (mode == 1) (targetKb ?: 0).toLong() * 1024L else Long.MAX_VALUE
        val qualities = if (mode == 1) listOf(88, 80, 72, 64, 56, 48) else listOf(82)
        var bestFile: File? = null
        var bestBytes = Long.MAX_VALUE
        var reached = false
        for (quality in qualities) {
            val candidate = File(cacheDir, "keri_opt_" + System.currentTimeMillis() + "_" + quality + ".pdf")
            try {
                PDDocument.load(source).use { document ->
                    for (page in document.pages) page.resources?.let { optimizeResources(document, it, quality, HashSet<Int>()) }
                    document.save(candidate)
                }
                if (candidate.length() < bestBytes) { bestFile?.delete(); bestFile = candidate; bestBytes = candidate.length() } else candidate.delete()
                if (bestBytes <= targetBytes) { reached = true; break }
            } catch (e: Exception) { candidate.delete(); if (bestFile == null) throw e }
        }
        val result = bestFile ?: error("PDF optimization did not produce an output")
        if (result.length() >= source.length()) {
            result.delete()
            val copy = File(cacheDir, "keri_unchanged_" + System.currentTimeMillis() + ".pdf")
            source.copyTo(copy, overwrite = true)
            return CompressionResult(copy, copy.length(), if (mode == 1) false else null)
        }
        return CompressionResult(result, result.length(), if (mode == 1) reached else null)
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

    private fun shareLastCompressedPdf() {
        val uri = lastSavedPdf ?: run {
            Toast.makeText(this, "No compressed PDF available yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "Compressed PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Share compressed PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open the sharing menu", Toast.LENGTH_LONG).show()
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

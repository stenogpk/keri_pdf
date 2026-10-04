package com.keripdf

import android.app.Activity
import android.graphics.Bitmap
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.provider.OpenableColumns
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.DecimalFormat
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedPdf: Uri? = null
    private var selectedBulkPdfs: List<Uri> = emptyList()
    private lateinit var fileLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var compressButton: Button
    private lateinit var openButton: Button
    private lateinit var shareButton: Button
    private lateinit var bulkButton: Button
    private lateinit var openBulkZipButton: Button
    private lateinit var shareBulkZipButton: Button
    private var lastSavedPdf: Uri? = null
    private var lastSavedBulkZip: Uri? = null
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
        bulkButton = actionButton("Bulk compress up to 50 PDFs  •  ZIP", 0xFFDBEAFE.toInt(), 0xFF1E3A8A.toInt()).apply {
            setOnClickListener { launchBulkPicker() }
        }
        val bulkParams = fullWidth().apply { topMargin = dp(12) }
        fileCard.addView(bulkButton, bulkParams)
        fileCard.addView(TextView(this).apply {
            text = "Select up to 50 PDFs. The KB target applies to each PDF separately; files that cannot meet it are skipped. ZIP size is the combined archive size."
            textSize = 11f
            setTextColor(0xFF64748B.toInt())
            setLineSpacing(dp(2).toFloat(), 1.0f)
            setPadding(dp(2), dp(7), dp(2), 0)
        }, fullWidth())
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
            text = "The requested KB limit is checked against each final PDF. Bulk mode adds only files that meet the limit to the ZIP."
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
            text = "Exact target mode searches for the best quality within your KB limit. Very small targets may require page-image conversion, so text may no longer be selectable/searchable. Your original PDF is never overwritten."
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
                        val originalName = selectedPdf?.let { queryDisplayName(it) } ?: "document.pdf"
                        val outputName = if (originalName.startsWith("KeRi", ignoreCase = true)) originalName else "KeRi$originalName"
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_TITLE, outputName)
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
        openBulkZipButton = actionButton("Open bulk ZIP", 0xFFE0E7FF.toInt(), 0xFF3730A3.toInt()).apply {
            visibility = View.GONE
            setOnClickListener { openLastBulkZip() }
        }
        val bulkOpenParams = fullWidth().apply { topMargin = dp(10) }
        resultCard.addView(openBulkZipButton, bulkOpenParams)
        shareBulkZipButton = actionButton("Share bulk ZIP", 0xFFF3E8FF.toInt(), 0xFF6B21A8.toInt()).apply {
            visibility = View.GONE
            setOnClickListener { shareLastBulkZip() }
        }
        val bulkShareParams = fullWidth().apply { topMargin = dp(10) }
        resultCard.addView(shareBulkZipButton, bulkShareParams)
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
        val uri = data?.data
        if (requestCode == REQUEST_OPEN) {
            val selected = uri ?: return
            selectedPdf = selected
            fileLabel.text = "Selected PDF: " + (selected.lastPathSegment ?: "document.pdf")
            statusLabel.text = "Ready to compress"
            compressButton.isEnabled = true
        } else if (requestCode == REQUEST_SAVE) {
            startCompression(uri ?: return)
        } else if (requestCode == REQUEST_BULK_OPEN) {
            val picked = ArrayList<Uri>()
            val clip = data?.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
            } else {
                uri?.let { picked.add(it) }
            }
            if (picked.isEmpty() || picked.size > MAX_BULK_FILES) {
                Toast.makeText(this, "Select between 1 and 50 PDF files", Toast.LENGTH_LONG).show()
                return
            }
            selectedBulkPdfs = picked.distinct()
            pendingMode = selectedMode()
            pendingTargetKb = targetSize.text.toString().toIntOrNull()
            if (pendingMode == 1 && (pendingTargetKb ?: 0) <= 0) {
                targetSize.error = "Enter a target size in KB"
                return
            }
            AlertDialog.Builder(this)
                .setTitle("Create bulk ZIP")
                .setMessage("Compress ${selectedBulkPdfs.size} PDFs using the selected mode. In exact-target mode, the KB limit applies separately to every PDF. Files that cannot meet the limit will be skipped, never added oversized. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ ->
                    val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, "KeRi_Bulk_Compressed.zip")
                    }
                    startActivityForResult(saveIntent, REQUEST_BULK_SAVE)
                }
                .show()
        } else if (requestCode == REQUEST_BULK_SAVE) {
            startBulkCompression(uri ?: return)
        }
    }

    private fun launchBulkPicker() {
        pendingMode = selectedMode()
        pendingTargetKb = targetSize.text.toString().toIntOrNull()
        if (pendingMode == 1 && (pendingTargetKb ?: 0) <= 0) {
            targetSize.error = "Enter a target size in KB"
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_BULK_OPEN)
    }

    private fun startBulkCompression(zipUri: Uri) {
        if (selectedBulkPdfs.isEmpty()) return
        bulkButton.isEnabled = false
        compressButton.isEnabled = false
        progress.visibility = View.VISIBLE
        openBulkZipButton.visibility = View.GONE
        shareBulkZipButton.visibility = View.GONE
        statusLabel.text = "Preparing bulk compression…"

        val inputs = selectedBulkPdfs.toList()
        val mode = pendingMode
        val targetKb = pendingTargetKb
        executor.execute {
            var completed = 0
            val failed = ArrayList<String>()
            val usedNames = HashSet<String>()
            try {
                val outputStream = contentResolver.openOutputStream(zipUri, "w")
                    ?: error("Could not create the ZIP file")
                ZipOutputStream(outputStream.buffered()).use { zip ->
                    inputs.forEachIndexed { index, inputUri ->
                        val originalName = queryDisplayName(inputUri)
                        val safeOriginal = originalName.substringAfterLast('/').ifBlank { "document_${index + 1}.pdf" }
                        val inputFile = File(cacheDir, "keri_bulk_in_${System.currentTimeMillis()}_$index.pdf")
                        var resultFile: File? = null
                        try {
                            contentResolver.openInputStream(inputUri)?.use { input ->
                                inputFile.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("Could not read $safeOriginal")
                            val result = compressPdf(inputFile, mode, targetKb)
                            resultFile = result.file
                            val maxBytes = if (mode == 1) targetLimitBytes(targetKb) else Long.MAX_VALUE
                            if (mode == 1 && result.bytes > maxBytes) {
                                failed.add("$safeOriginal (target not achievable)")
                            } else {
                                val prefixed = if (safeOriginal.startsWith("KeRi", ignoreCase = true)) safeOriginal else "KeRi$safeOriginal"
                                val entryName = uniqueZipName(prefixed, usedNames)
                                zip.putNextEntry(ZipEntry(entryName))
                                result.file.inputStream().buffered().use { it.copyTo(zip) }
                                zip.closeEntry()
                                completed++
                            }
                        } catch (fileError: Exception) {
                            failed.add("$safeOriginal (${fileError.localizedMessage ?: "compression failed"})")
                        } finally {
                            resultFile?.delete()
                            inputFile.delete()
                        }
                        val done = index + 1
                        runOnUiThread { statusLabel.text = "Bulk compression: $done / ${inputs.size} processed…" }
                    }
                    if (completed == 0) error("No PDF could be compressed within the requested settings")
                }
                lastSavedBulkZip = zipUri
                runOnUiThread {
                    progress.visibility = View.GONE
                    compressButton.isEnabled = selectedPdf != null
                    bulkButton.isEnabled = true
                    openBulkZipButton.visibility = View.VISIBLE
                    shareBulkZipButton.visibility = View.VISIBLE
                    val summary = "Bulk ZIP saved\nPDFs added: $completed / ${inputs.size}" +
                        if (failed.isNotEmpty()) "\nSkipped: ${failed.size}\n" + failed.take(8).joinToString("\n") else ""
                    statusLabel.text = summary
                    Toast.makeText(this, "Bulk ZIP created successfully", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                try { android.provider.DocumentsContract.deleteDocument(contentResolver, zipUri) } catch (_: Exception) { }
                runOnUiThread {
                    progress.visibility = View.GONE
                    compressButton.isEnabled = selectedPdf != null
                    bulkButton.isEnabled = true
                    statusLabel.text = "Bulk compression failed: " + (error.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "document.pdf"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    private fun uniqueZipName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "$base ($index)$ext"
            if (used.add(candidate)) return candidate
            index++
        }
    }

    private fun openLastBulkZip() {
        val uri = lastSavedBulkZip ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(intent, "Open bulk ZIP")) }
        catch (_: Exception) { shareLastBulkZip() }
    }

    private fun shareLastBulkZip() {
        val uri = lastSavedBulkZip ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "KeRi bulk ZIP", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(Intent.createChooser(intent, "Share bulk ZIP")) }
        catch (_: Exception) { Toast.makeText(this, "Could not open sharing menu", Toast.LENGTH_LONG).show() }
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
                val hardLimit = if (pendingMode == 1) targetLimitBytes(pendingTargetKb) else Long.MAX_VALUE
                if (pendingMode == 1 && result.bytes > hardLimit) {
                    result.file.delete()
                    source.delete()
                    try { android.provider.DocumentsContract.deleteDocument(contentResolver, outputUri) } catch (_: Exception) { }
                    runOnUiThread {
                        progress.visibility = View.GONE
                        compressButton.isEnabled = true
                        openButton.visibility = View.GONE
                        shareButton.visibility = View.GONE
                        statusLabel.text = "The requested " + pendingTargetKb + " KB limit could not be met. No oversized PDF was saved. Try a larger target size."
                        Toast.makeText(this, "Target limit could not be met. No oversized file saved.", Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }
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
                    if (pendingMode == 1) message += "\nTarget: " + pendingTargetKb + " KB  •  Within limit"
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

    // Keep a 1 KB safety margin below the requested target whenever possible.
    private fun targetLimitBytes(targetKb: Int?): Long {
        val requested = (targetKb ?: 0).toLong() * 1024L
        return if (requested > 1024L) requested - 1024L else requested
    }

    /**
     * Text PDFs keep their original text/vector structure. Image-only scanned PDFs use a
     * dedicated page-rendering path so the scan itself can actually be recompressed.
     */
    private fun compressPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        PDFBoxResourceLoader.init(applicationContext)
        val targetBytes = if (mode == 1) targetLimitBytes(targetKb) else Long.MAX_VALUE
        if (mode == 1 && source.length() <= targetBytes) {
            val copy = File(cacheDir, "keri_already_within_target_" + System.currentTimeMillis() + ".pdf")
            source.copyTo(copy, overwrite = true)
            return CompressionResult(copy, copy.length(), true)
        }
        val hasSelectableText = PDDocument.load(source).use { document ->
            try { PDFTextStripper().getText(document).trim().isNotEmpty() } catch (_: Exception) { true }
        }
        if (!hasSelectableText) return compressScannedPdf(source, mode, targetKb)
        val textResult = compressTextPdf(source, mode, targetKb)
        if (mode == 1 && textResult.bytes > targetBytes) {
            textResult.file.delete()
            return compressScannedPdf(source, mode, targetKb)
        }
        return textResult
    }

    private fun compressScannedPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        if (mode != 1) {
            val output = renderScannedCandidate(source, 220, 90)
            if (output.length() >= source.length()) {
                output.delete()
                val copy = File(cacheDir, "keri_scan_original_" + System.currentTimeMillis() + ".pdf")
                source.copyTo(copy, overwrite = true)
                return CompressionResult(copy, copy.length(), null)
            }
            return CompressionResult(output, output.length(), null)
        }
        val targetBytes = targetLimitBytes(targetKb)
        val dpiLevels = listOf(220, 180, 150, 120, 100, 80, 60, 40, 28)
        var bestFile: File? = null
        var bestScore = -1L
        for (dpi in dpiLevels) {
            if (bestScore >= dpi.toLong() * 95L) break
            var low = 15
            var high = 95
            var bestAtDpi: File? = null
            var bestQuality = -1
            val minimum = renderScannedCandidate(source, dpi, 15)
            if (minimum.length() <= targetBytes) {
                bestAtDpi = minimum
                bestQuality = 15
            } else {
                minimum.delete()
                continue
            }
            while (low <= high) {
                val quality = (low + high) / 2
                val candidate = renderScannedCandidate(source, dpi, quality)
                if (candidate.length() <= targetBytes) {
                    bestAtDpi?.delete()
                    bestAtDpi = candidate
                    bestQuality = quality
                    low = quality + 1
                } else {
                    candidate.delete()
                    high = quality - 1
                }
            }
            val score = dpi.toLong() * bestQuality.toLong()
            if (score > bestScore) {
                bestFile?.delete()
                bestFile = bestAtDpi
                bestScore = score
            } else {
                bestAtDpi?.delete()
            }
        }
        val finalFile = bestFile ?: run {
            val diagnostic = File(cacheDir, "keri_target_unreachable_" + System.currentTimeMillis() + ".pdf")
            source.copyTo(diagnostic, overwrite = true)
            return CompressionResult(diagnostic, diagnostic.length(), false)
        }
        return CompressionResult(finalFile, finalFile.length(), finalFile.length() <= targetBytes)
    }

    private fun renderScannedCandidate(source: File, dpi: Int, quality: Int): File {
        val output = File(cacheDir, "keri_scan_" + System.currentTimeMillis() + "_" + dpi + "_" + quality + ".pdf")
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
        return output
    }
    private fun compressTextPdf(source: File, mode: Int, targetKb: Int?): CompressionResult {
        val targetBytes = if (mode == 1) targetLimitBytes(targetKb) else Long.MAX_VALUE
        val qualities = if (mode == 1) listOf(96, 92, 88, 84, 80, 76, 72, 68, 64, 60, 56, 52, 48, 44, 40, 36, 32, 28, 24, 20, 16, 12) else listOf(82)
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
        private const val REQUEST_BULK_OPEN = 1003
        private const val REQUEST_BULK_SAVE = 1004
        private const val MAX_BULK_FILES = 50
    }
}

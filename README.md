# Keri PDF

Keri PDF is an offline-first Android PDF optimizer. Files are processed locally on the device.

## Version 1.2.0 approach
- Uses PDFBox-Android to preserve text PDFs and apply a dedicated JPEG/DPI optimization path to image-only scanned PDFs.
- Preserves the existing PDF page content, selectable/searchable text, vector graphics and page geometry as far as supported by the PDF library.
- Saves a new copy through Android's document picker; the source file is not overwritten.
- Offers a quality-preserving mode and an exact-target check. The exact-target option reports whether the rewritten file meets the requested size; it does not degrade page quality to force a number.
- Offers an **Open compressed PDF** action after saving.
- No account, upload server or cloud processing.

## Important limitation
A structure-preserving rewrite cannot guarantee a smaller file for every PDF. PDFs that are already optimized or contain large embedded scans may remain close to their original size. A very small target (for example 200 KB) may be impossible without lossy image recompression. Keri PDF intentionally does not rasterize all pages or blur text to claim that target was reached.

This version has been build-validated through GitHub Actions. Real-device testing with varied PDFs (text PDFs, Hindi notes, scanned documents, image-heavy PDFs, forms and password-protected PDFs) is still required before calling it fully production-verified.

## Build and install
Open **Actions → Keri PDF Final APK Build → latest successful run → Artifacts** and download `Keri-PDF-v1.2.0-APK`. Extract the ZIP and install `app-debug.apk` on Android. The workflow also publishes a SHA-256 checksum file.

## Library
Uses [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), Apache License 2.0.

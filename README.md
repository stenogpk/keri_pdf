# KeRi PDF Compressor

Offline-first Android PDF compression. Files are processed locally on the device.

## Version 1.4.0
- Added bulk PDF compression for up to 50 selected PDFs in one operation.
- Bulk results are delivered as one ZIP archive, with each PDF filename prefixed by `KeRi` while retaining its original filename.
- The selected compression mode is applied to every PDF. In Exact target mode, the KB limit applies independently to each PDF, not to the combined ZIP archive.
- A PDF that cannot meet its individual target is skipped and reported; an oversized PDF is never added to the ZIP.
- Added Open bulk ZIP and Share bulk ZIP actions.
- Single-file output now also starts with `KeRi` followed by the original filename.
- Existing compression engine, quality search, target-size enforcement, app identity and single-file open/share behavior retained.

## Exact target behavior
- The final PDF byte count is checked before it is saved or included in a ZIP.
- If a selected PDF cannot meet the requested size, it is not saved as an oversized result. In bulk mode it is skipped and listed in the result summary.
- The target applies per PDF. A ZIP containing several PDFs will naturally be larger than the per-file target.
- To fit very small targets, scanned/image-heavy PDFs may need lossy image recompression; text may become non-selectable in that fallback.

## Build and install
Open **Actions → Keri PDF Final APK Build → latest successful run → Artifacts** and download `KeRi-PDF-Compressor-v1.4.0`. Extract the ZIP and install `app-debug.apk` on Android. The workflow also publishes a SHA-256 checksum file.

## Library
Uses [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), Apache License 2.0.

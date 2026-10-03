# Keri PDF

Keri PDF is an offline-first Android PDF compression app.

## Initial implementation scope
- Pick a PDF using Android Storage Access Framework.
- Compress scanned/image-heavy PDFs locally on the device.
- Light, Recommended and Strong presets.
- Target-size mode attempts a requested output size and reports the actual result if it cannot be reached.
- Save the compressed PDF to a user-selected location.
- No account, upload server or cloud processing.

## Important PDF fidelity note
The first raster-compression engine renders pages to images and rebuilds the PDF. This is useful for scanned PDFs, but rasterizes text and vector artwork: text may no longer be selectable/searchable and fine details may be reduced. The app must disclose this before processing. A separate content-preserving optimizer must not be represented as available until implemented and tested.

## Build
The GitHub Actions workflow builds an installable Android debug APK and uploads it as an artifact. Open **Actions → Android APK Build → latest successful run → Artifacts** to download it.

## Development status
Project scaffold in progress. Do not treat an APK as release-ready until the workflow succeeds and device-level PDF quality tests have been completed.

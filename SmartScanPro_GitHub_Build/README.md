# SmartDoc Scanner — Final APK-ready project

यह Android Studio project है। इसे Windows PC पर Android Studio में खोलकर APK बनाया जा सकता है।

## Included
- CameraX document scanner
- automatic document boundary detection + perspective normalization
- manual/automatic processing workflow
- rotate, color, grayscale, B&W, high contrast
- multi-page PDF queue
- Quality Based output (default)
- Maximum Size output, minimum 2 MB, with size verification/reduction attempts
- Hindi (Devanagari) + English OCR
- editable OCR result
- OCR → DOCX
- OCR → XLSX with basic row/column heuristics
- QR / barcode camera scanner
- PDF → images
- images → PDF
- local document library, search, delete
- Android share/open/print hand-off

## Important limitation
Photo से Word/Excel में **100% pixel-identical** editable reconstruction सामान्य OCR से guarantee नहीं की जा सकती। यह project text और basic table/column structure को editable format में reconstruct करता है; complex formatting, fonts, merged cells, stamps/signatures आदि में manual correction लग सकती है।

## Build
1. Android Studio install करें।
2. यह folder खोलें।
3. Gradle sync पूरा होने दें।
4. Build → Generate App Bundles or APKs → Generate APKs.
5. Debug APK `app/build/outputs/apk/debug/app-debug.apk` में मिलेगा।

इस environment में Android SDK/Gradle build tool उपलब्ध न होने के कारण APK को यहाँ compile/test नहीं किया गया है।

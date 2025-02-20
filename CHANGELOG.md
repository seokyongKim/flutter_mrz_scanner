## 3.0.0

* **OCR Optimization**: Implemented character whitelisting for MRZ-specific characters (`ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<`)
* **Image Processing Enhancements**:
  - Improved document cropping accuracy
  - Added grayscale conversion pipeline
  - Optimized binarization thresholds for better text recognition
* **Camera Improvements**:
  - Upgraded Fotoapparat implementation with better focus handling
  - Enhanced frame processing reliability
* **Overlay UI Updates**:
  - Added dynamic scanning guidance indicators
  - Improved aspect ratio handling for different devices
* **Validation System**:
  - Implemented MRZ checksum verification
  - Added error correction heuristics
* **Dependency Updates**:
  - Upgraded Tesseract OCR to latest stable version
  - Migrated to latest Kotlin Gradle plugin
  - Updated Android target SDK to 34
* **Breaking Change**: Requires minimum Flutter 3.16.0

(by @ELMEHDAOUIAhmed)


## 2.1.1

* Add namespace (by @makhosi6)

## 2.1.0

* Support for Flutter 3.0.5 (by @dadagov125)

## 2.0.1

* Fix : Android crash (by @eusopht2021)

## 2.0.0

* Fix : iOS compiling errors for iOS 15 (by @gdaguin)
* Improvements : on Android, the camera is now focusing automatically (by @gdaguin)
* Port to null safety

## 1.0.0
* Android version redeveloped with Fotoapparat library
* Add overlay widget
* Flashlight on/off
* Take a photo

## 0.7.1

* Provide possibility to disable overlay

## 0.7.0

* First public version with basic iOS and Android scanners

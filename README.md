# Flutter MRZ Scanner Enhanced 

Based on [![Pub Version](https://img.shields.io/pub/v/flutter_mrz_scanner)](https://pub.dev/packages/flutter_mrz_scanner)

**A community-maintained fork** of the original `flutter_mrz_scanner` package with significant improvements to MRZ scanning reliability and camera UX.

## ✨ Key Enhancements
- **Improved text recognition accuracy** through advanced image preprocessing
- **Optimized camera overlay UI** for better user experience
- Enhanced image processing pipelines
- Modernized dependencies and null-safety support
- Improved error handling and validation
- Better platform compatibility

## 🚧 Active Development

As I'm actively using this in production, expect regular updates including:

### Performance Improvements 🚀
- **Flutter Isolate Integration**: Offload heavy image processing and OCR tasks to background isolates for smoother UI performance.
- **Real-Time MRZ Detection**: Implement live feedback for MRZ detection with visual indicators and dynamic UI updates.

### Feature Enhancements ✨
- **Machine Learning Model Optimizations**: Improve OCR accuracy with updated ML models and preprocessing pipelines.
- **Customizable UI Components**: Allow developers to fully customize the camera overlay, scanning UI, and feedback animations.

### Stability & Maintenance 🔧
- **Improved Error Handling**: Better error reporting and recovery mechanisms for edge cases.
- **Cross-Platform Compatibility**: Ensure consistent behavior across iOS, Android, and web platforms.
- **Community-Driven Features**: Prioritize features and fixes based on community feedback.


## 🙏 Acknowledgments
This package builds upon the work of:
- [@olexale](https://github.com/olexale) (Oleksandr Leushchenko) - Original creator
- [@makhosi6](https://github.com/makhosi6) (Makhosandile) - Early contributor
- [@eusopht2021](https://github.com/eusopht2021) - Community contributor

Contributions welcome! Please report issues and feature requests on [GitHub](https://github.com/ELMEHDAOUIAhmed/flutter_mrz_scanner_enhanced).
### Supported formats:
* TD1
* TD2
* TD3
* MRV-A
* MRV-B

## Usage

### Import the package
Add to `pubspec.yaml`
```yaml
dependencies:
  flutter_mrz_scanner: <latest_version_here>
```
### For iOS
Set iOS deployment target to 12.
The plugin uses the device camera, so do not forget to provide the `NSCameraUsageDescription`. You may specify it in `Info.plist` like that:
```xml
    <key>NSCameraUsageDescription</key>
    <string>SCANNING MRZ REQUIRE CAMERA PERMISSIONS</string>
```

### For Android
Add
```
<uses-permission android:name="android.permission.CAMERA" />
```
to `AndroidManifest.xml`

### Use the widget
Use `MRZScanner` widget:
```dart
MRZScanner(
  withOverlay: true, // Mandatory for proper document cropping
  onControllerCreated: (controller) =>
    onControllerCreated(controller),
  )
```
Refer to `example` project for the complete app sample.

## Acknowledgements
* [Anna Domashych](https://github.com/foxanna) for helping with [mrz_parser](https://github.com/olexale/mrz_parser) implementation in Dart
* [Anton Derevyanko](https://github.com/antonderevyanko) for hours of Android-related discussions
* [Mattijah](https://github.com/Mattijah) for beautiful [QKMRZScanner](https://github.com/Mattijah/QKMRZScanner) library

## License
`flutter_mrz_scanner_enhanced` is released under a [MIT License](https://opensource.org/licenses/MIT). See `LICENSE` for details.

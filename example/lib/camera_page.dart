import 'package:flutter/material.dart';
import 'package:flutter_mrz_scanner/flutter_mrz_scanner.dart';

class CameraPage extends StatefulWidget {
  const CameraPage({Key? key}) : super(key: key);

  @override
  _CameraPageState createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  bool isParsed = false;
  MRZController? controller;

  // Document overlay ratio (for driving license: 82/52).
  static const double _documentFrameRatio = 82.0 / 52.0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan MRZ'),
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          // Calculate the overlay rectangle based on the screen size.
          final overlayRect = _calculateOverlaySize(
            Size(constraints.maxWidth, constraints.maxHeight),
          );
          return Stack(
            children: [
              // The native camera view with overlay.
              MRZScanner(
                withOverlay: true,
                onControllerCreated: onControllerCreated,
              ),
              // Draw a transparent overlay border (for guidance).
              Positioned(
                left: overlayRect.left,
                top: overlayRect.top,
                width: overlayRect.width,
                height: overlayRect.height,
                child: Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.white, width: 2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
              // Optional: a status indicator below the overlay.
              Positioned(
                left: overlayRect.left,
                top: overlayRect.bottom + 8,
                width: overlayRect.width,
                child: Center(
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: const AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  /// Calculates the overlay area based on screen size and document ratio.
  RRect _calculateOverlaySize(Size size) {
    double width, height;
    if (size.height > size.width) {
      width = size.width * 0.9;
      height = width / _documentFrameRatio;
    } else {
      height = size.height * 0.75;
      width = height * _documentFrameRatio;
    }
    final topOffset = (size.height - height) / 2;
    final leftOffset = (size.width - width) / 2;
    return RRect.fromLTRBR(
      leftOffset,
      topOffset,
      leftOffset + width,
      topOffset + height,
      const Radius.circular(8),
    );
  }

  @override
  void dispose() {
    controller?.stopPreview();
    super.dispose();
  }

  void onControllerCreated(MRZController controller) {
    this.controller = controller;
    controller.onParsed = (result) async {
      if (isParsed) return;
      isParsed = true;

      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Text('Document type: ${result.documentType}'),
                Text('Country: ${result.countryCode}'),
                Text('Surnames: ${result.surnames}'),
                Text('Given names: ${result.givenNames}'),
                Text('Document number: ${result.documentNumber}'),
                Text('Nationality code: ${result.nationalityCountryCode}'),
                Text('Birthdate: ${result.birthDate}'),
                Text('Sex: ${result.sex}'),
                Text('Expiry date: ${result.expiryDate}'),
                Text('Personal number: ${result.personalNumber}'),
                Text('Personal number 2: ${result.personalNumber2}'),
              ],
            ),
          ),
          actions: [
            ElevatedButton(
              child: const Text('OK'),
              onPressed: () {
                isParsed = false;
                Navigator.pop(context);
              },
            ),
          ],
        ),
      );
    };

    controller.onError = (error) => print(error);
    controller.startPreview();
  }
}

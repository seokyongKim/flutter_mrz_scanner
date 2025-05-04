import 'package:flutter/material.dart';
import 'package:flutter_mrz_scanner_enhanced/flutter_mrz_scanner_enhanced.dart';

class CameraPage extends StatefulWidget {
  const CameraPage({Key? key}) : super(key: key);

  @override
  _CameraPageState createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  bool isParsed = false;
  MRZController? controller;
  final ValueNotifier<String> statusNotifier = ValueNotifier('');
  final List<String> _statusMessages = [];

  // Document overlay ratio (for driving license: 82/52).
  static const double _documentFrameRatio = 82.0 / 52.0;
  bool isFlashOn = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan Back Side'),
        actions: [
          IconButton(
            icon: Icon(
              isFlashOn ? Icons.flash_off_rounded : Icons.flash_on_rounded,
            ),
            onPressed: _toggleFlash,
          ),
        ],
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
                child: ValueListenableBuilder<String>(
                  valueListenable: statusNotifier,
                  builder: (context, _, child) {
                    return Column(
                      children: List.generate(_statusMessages.length, (index) {
                        final message =
                            _statusMessages[_statusMessages.length - 1 - index];
                        return AnimatedOpacity(
                          opacity: index == 0 ? 1.0 : 0.5,
                          duration: const Duration(milliseconds: 300),
                          child: Padding(
                            padding: const EdgeInsets.only(
                                bottom: 4.0), // Add spacing between messages
                            child: Text(
                              message,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        );
                      }),
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  void _updateStatus(String message) {
    _statusMessages.add(message); // Add the new message to the list
    statusNotifier.value = message; // Trigger a rebuild

    // Remove the message after a delay
    Future.delayed(const Duration(seconds: 1), () {
      _statusMessages.remove(message);
      statusNotifier.value = ''; // Trigger a rebuild
    });
  }

  void _toggleFlash() {
    if (isFlashOn) {
      controller?.flashlightOff();
    } else {
      controller?.flashlightOn();
    }
    setState(() {
      isFlashOn = !isFlashOn;
    });
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
    _updateStatus('Camera initialized');

    controller.onDetection = () {
      _updateStatus('MRZ Detected, Parsing...');
    };

    controller.onParsed = (result) async {
      if (isParsed) {
        return;
      }

      try {
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

        // Reset status after parsing is complete
        _updateStatus('');
      } catch (e, stackTrace) {
        // Log the error and reset the state
        debugPrint('Error during parsing: $e');
        debugPrint(stackTrace.toString());
        _updateStatus('Parsing failed, scanning again');
        isParsed = false;
      }
    };

    controller.onParsingFailed = () {
      // Update status when parsing fails
      _updateStatus('Parsing failed, scanning again');
      debugPrint('Parsing failed callback triggered');
    };

    controller.onError = (error) {
      _updateStatus('Parsing failed, scanning again');
      debugPrint('Error: $error');
    };

    controller.startPreview();
  }
}

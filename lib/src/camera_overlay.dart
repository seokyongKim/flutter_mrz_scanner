import 'package:flutter/material.dart';

class CameraOverlay extends StatelessWidget {
  const CameraOverlay({required this.child, super.key});

  static const _documentFrameRatio = 86.0 / 55.0; // Standard document ratio
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (_, c) {
        final overlayRect = _calculateOverlaySize(
          Size(c.maxWidth, c.maxHeight),
        );
        return Stack(
          children: [
            child,
            ClipPath(
              clipper: _DocumentClipper(rect: overlayRect),
              child: Container(
                foregroundDecoration: const BoxDecoration(
                  color: Color.fromRGBO(0, 0, 0, 0.80),
                ),
              ),
            ),
            _WhiteOverlay(rect: overlayRect),
            _MRZOverlay(
              rect: overlayRect,
            ), // MRZ box inside the original white rectangle
          ],
        );
      },
    );
  }

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
}

class _DocumentClipper extends CustomClipper<Path> {
  _DocumentClipper({required this.rect});

  final RRect rect;

  @override
  Path getClip(Size size) => Path()
    ..addRRect(rect)
    ..addRect(Rect.fromLTWH(0.0, 0.0, size.width, size.height))
    ..fillType = PathFillType.evenOdd;

  @override
  bool shouldReclip(_DocumentClipper oldClipper) => false;
}

class _WhiteOverlay extends StatelessWidget {
  const _WhiteOverlay({required this.rect, Key? key}) : super(key: key);
  final RRect rect;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: rect.left,
      top: rect.top,
      child: Container(
        width: rect.width,
        height: rect.height,
        decoration: BoxDecoration(
          border: Border.all(width: 2.0, color: const Color(0xFFFFFFFF)),
          borderRadius: BorderRadius.all(rect.tlRadius),
        ),
      ),
    );
  }
}

class _MRZOverlay extends StatelessWidget {
  const _MRZOverlay({required this.rect, Key? key}) : super(key: key);
  final RRect rect;

  @override
  Widget build(BuildContext context) {
    final double mrzHeight = rect.height * 0.35; // 20% of document height

    return Positioned(
      left: rect.left + 8, // Slight padding
      top:
          rect.bottom - mrzHeight - 10, // Positioned inside the white rectangle
      child: Container(
        width: rect.width - 16, // Add padding on sides
        height: mrzHeight,
        decoration: BoxDecoration(
          border: Border.all(
            width: 2.0,
            color: const Color.fromARGB(127, 255, 255, 255),
          ),
          borderRadius: BorderRadius.all(rect.tlRadius),

          color: const Color.fromARGB(
            62,
            7,
            7,
            7,
          ), // Lighter white overlay for MRZ zone
          //borderRadius: BorderRadius.circular(4),
        ),
        alignment: Alignment.center,
        // TODO : Uncomment this to show MRZ text AND MAKE IT RESPONSIVE for different screen sizes
        // child: const Text(
        //   "DLDZA123456789X<<<<<<<<<<<<<<<<\n123456XM778000XDZA<<<<<<<<<<<X\nHB<<TECHNOLOGIES<<<<<<<<<<<<<",
        //   style: TextStyle(
        //     color: Color.fromARGB(101, 255, 255, 255),
        //     fontWeight: FontWeight.w500,
        //     fontSize: 16.5,
        //   ),
        // ),
      ),
    );
  }
}

import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../models/brush.dart';
import 'ipbz_service.dart';

/// Service for generating and scanning brush QR codes.
///
/// Uses qr_flutter for QR code generation and mobile_scanner for scanning.
/// QR codes contain VPBZ (VenPaint Brush) or IPBZ (ibisPaint) encoded brush data.
class BrushQrService {
  /// Generates a QR code image widget from a [VenBrush].
  ///
  /// The brush data is encoded in VPBZ format, which is compatible
  /// with ibisPaint's IPBZ format for cross-app brush sharing.
  static Widget generateBrushQR(VenBrush brush, {double size = 280}) {
    final optimized = IpbzService.optimizeForQr(brush);
    final binaryData = IpbzService.generateVpbzData(optimized);

    // Encode binary data as base64 for QR-safe transport
    final base64Data = base64Encode(binaryData);

    return QrImageView(
      data: base64Data,
      version: QrVersions.auto,
      size: size,
      backgroundColor: Colors.white,
      eyeStyle: const QrEyeStyle(
        eyeShape: QrEyeShape.square,
        color: Color(0xFF1A1A2E),
      ),
      dataModuleStyle: const QrDataModuleStyle(
        dataModuleShape: QrDataModuleShape.square,
        color: Color(0xFF1A1A2E),
      ),
      padding: const EdgeInsets.all(16),
    );
  }

  /// Generates raw QR code data bytes from a [VenBrush].
  ///
  /// Returns base64-encoded VPBZ data suitable for QR code encoding.
  static String generateBrushQRData(VenBrush brush) {
    final optimized = IpbzService.optimizeForQr(brush);
    final binaryData = IpbzService.generateVpbzData(optimized);
    return base64Encode(binaryData);
  }

  /// Parses scanned QR code data and returns a [VenBrush].
  ///
  /// [qrData] is the raw string scanned from a QR code.
  /// Handles both base64-encoded VPBZ/IPBZ and raw binary data.
  ///
  /// Returns null if the data cannot be parsed as a valid brush.
  static VenBrush? scanBrushQR(String qrData) {
    try {
      // Try base64 decoding first (VenPaint format)
      final decoded = base64Decode(qrData);

      if (IpbzService.isValidVpbzData(decoded)) {
        return IpbzService.parseVpbzData(decoded);
      }

      // Try raw binary (ibisPaint direct format)
      if (qrData.isNotEmpty) {
        final rawBytes = Uint8List.fromList(qrData.codeUnits);
        if (IpbzService.isValidVpbzData(rawBytes)) {
          return IpbzService.parseVpbzData(rawBytes);
        }
      }
    } catch (e) {
      // Try parsing as JSON directly (fallback for simple brush sharing)
      try {
        final json = jsonDecode(qrData) as Map<String, dynamic>;
        if (json.containsKey('brushType') || json.containsKey('size')) {
          return IpbzService.brushFromJson(qrData);
        }
      } catch (_) {
        // Not a valid format
      }
    }
    return null;
  }

  /// Validates QR code data as containing valid brush information.
  ///
  /// Returns a description of the brush if valid, null otherwise.
  static String? validateBrushQR(String qrData) {
    final brush = scanBrushQR(qrData);
    if (brush == null) return null;
    return 'Brush: ${brush.name} (${brush.brushType}, '
        '${brush.size.toStringAsFixed(1)}px)';
  }

  /// Checks if a QR code data string contains VPBZ/IPBZ brush data.
  static bool isBrushQR(String qrData) {
    try {
      final decoded = base64Decode(qrData);
      return IpbzService.isValidVpbzData(decoded);
    } catch (_) {
      try {
        final rawBytes = Uint8List.fromList(qrData.codeUnits);
        return IpbzService.isValidVpbzData(rawBytes);
      } catch (_) {
        return false;
      }
    }
  }

  /// Creates a shareable brush preset card widget.
  ///
  /// Displays the brush QR code along with brush name and parameters.
  static Widget createBrushPresetCard(VenBrush brush, {VoidCallback? onTap}) {
    return Card(
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                brush.name,
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              generateBrushQR(brush, size: 160),
              const SizedBox(height: 8),
              Text(
                '${brush.brushType} · ${brush.size.toStringAsFixed(1)}px · '
                '${(brush.opacity * 100).toStringAsFixed(0)}%',
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey[600],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Generates a preview thumbnail of the brush stroke for display.
  ///
  /// Returns a custom widget that shows a sample stroke using the brush settings.
  static Widget createBrushPreview(VenBrush brush, {double width = 200, double height = 40}) {
    return CustomPaint(
      size: Size(width, height),
      painter: _BrushPreviewPainter(brush),
    );
  }
}

/// Custom painter that renders a preview stroke using brush parameters.
class _BrushPreviewPainter extends CustomPainter {
  final VenBrush brush;

  _BrushPreviewPainter(this.brush);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = brush.color
      ..strokeWidth = brush.size.clamp(1.0, size.height * 0.8)
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke
      ..blendMode = brush.brushType == 'eraser' ? BlendMode.dstOut : BlendMode.srcOver;

    // Draw a wavy line as brush preview
    final path = Path();
    path.moveTo(10, size.height / 2);

    for (double x = 10; x <= size.width - 10; x += 2) {
      final y = size.height / 2 +
          math.sin(x * 0.05) * size.height * 0.2 +
          math.sin(x * 0.12) * size.height * 0.1;
      path.lineTo(x, y);
    }

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(_BrushPreviewPainter oldDelegate) {
    return brush != oldDelegate.brush;
  }
}

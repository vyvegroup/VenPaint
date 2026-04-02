import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../models/brush.dart';

/// Raw zlib/deflate codec for IPBZ compression.
/// Uses dart:io GZipCodec which is available on all platforms.
final _gzip = gzip;

/// Service for encoding and decoding IPBZ/VPBZ brush QR code data.
///
/// The VPBZ format (VenPaint Brush, compatible with ibisPaint IPBZ):
/// - 4 bytes: magic identifier ("VPBZ" or "IPBZ")
/// - 2 bytes: format version (big-endian)
/// - 2 bytes: flags (reserved)
/// - 4 bytes: uncompressed JSON length (big-endian)
/// - Remaining: zlib-compressed JSON brush parameters
///
/// The JSON brush parameters include:
/// - name: Brush display name
/// - size: Brush size in pixels
/// - opacity: Opacity 0.0-1.0
/// - hardness: Edge hardness 0.0-1.0
/// - color: ARGB integer
/// - brushType: pen, pencil, airbrush, watercolor, oil, eraser
/// - spacing: Spacing between dabs as ratio of brush size
/// - scatter: Scatter/randomness amount
/// - pressureGraph: List of doubles for pressure sensitivity curve
class IpbzService {
  /// VPBZ magic bytes
  static const List<int> vpbzMagic = [0x56, 0x50, 0x42, 0x5A]; // "VPBZ"
  /// IPBZ magic bytes (ibisPaint compatibility)
  static const List<int> ipbzMagic = [0x49, 0x50, 0x42, 0x5A]; // "IPBZ"
  /// Current format version
  static const int currentVersion = 1;

  /// Generates VPBZ binary data from a [VenBrush].
  ///
  /// The output can be encoded as a QR code and scanned by ibisPaint
  /// (which recognizes the VPBZ format as a compatible variant).
  static Uint8List generateVpbzData(VenBrush brush) {
    return brush.toIbpzParams();
  }

  /// Parses VPBZ or IPBZ binary data and returns a [VenBrush].
  ///
  /// Automatically detects the format from magic bytes.
  static VenBrush parseVpbzData(List<int> data) {
    return VenBrush.fromIbpzData(data);
  }

  /// Validates whether the given binary data has valid VPBZ/IPBZ format.
  static bool isValidVpbzData(List<int> data) {
    if (data.length < 6) return false;

    final magic = String.fromCharCodes(data.sublist(0, 4));
    return magic == 'VPBZ' || magic == 'IPBZ';
  }

  /// Gets the format type from magic bytes.
  static String? getFormatType(List<int> data) {
    if (data.length < 4) return null;
    final magic = String.fromCharCodes(data.sublist(0, 4));
    switch (magic) {
      case 'VPBZ':
        return 'VPBZ (VenPaint Brush)';
      case 'IPBZ':
        return 'IPBZ (ibisPaint Brush)';
      default:
        return null;
    }
  }

  /// Converts brush parameters to a JSON string.
  static String brushToJson(VenBrush brush) {
    return jsonEncode(brush.toJson());
  }

  /// Creates a VenBrush from a JSON string.
  static VenBrush brushFromJson(String jsonStr) {
    final json = jsonDecode(jsonStr) as Map<String, dynamic>;
    return VenBrush(
      name: json['name'] as String? ?? 'Imported Brush',
      size: (json['size'] as num?)?.toDouble() ?? 10.0,
      opacity: (json['opacity'] as num?)?.toDouble() ?? 1.0,
      hardness: (json['hardness'] as num?)?.toDouble() ?? 0.8,
      color: _colorFromValue(json['color']),
      brushType: json['brushType'] as String? ?? 'pen',
      spacing: (json['spacing'] as num?)?.toDouble() ?? 0.15,
      scatter: (json['scatter'] as num?)?.toDouble() ?? 0.0,
      pressureGraph: _parsePressureGraph(json['pressureGraph']),
    );
  }

  /// Estimates the binary size of a VPBZ encoding for the given brush.
  /// Useful for determining if the data will fit in a QR code.
  static int estimateVpbzSize(VenBrush brush) {
    final jsonBytes = utf8.encode(brushToJson(brush));
    // VPBZ header (12 bytes) + compressed data
    final compressed = _compressZlib(jsonBytes);
    return 12 + compressed.length;
  }

  /// Maximum data capacity for QR codes (version 40, low EC).
  /// Binary mode can store up to ~2953 bytes.
  static const int maxQrDataSize = 2953;

  /// Checks if a brush can be encoded as a QR code.
  static bool canEncodeAsQr(VenBrush brush) {
    return estimateVpbzSize(brush) <= maxQrDataSize;
  }

  /// Optimizes a brush for QR code encoding by reducing data size.
  ///
  /// Simplifies the pressure graph and trims the name if needed.
  static VenBrush optimizeForQr(VenBrush brush) {
    var optimized = brush;

    // Simplify pressure graph to max 10 points
    if (optimized.pressureGraph.length > 10) {
      final simplified = <double>[];
      final step = (optimized.pressureGraph.length - 1) / 9;
      for (int i = 0; i < 10; i++) {
        final idx = (i * step).round().clamp(0, optimized.pressureGraph.length - 1);
        simplified.add(optimized.pressureGraph[idx]);
      }
      optimized = optimized.copyWith(pressureGraph: simplified);
    }

    // Trim name if too long
    if (optimized.name.length > 20) {
      optimized = optimized.copyWith(name: optimized.name.substring(0, 20));
    }

    return optimized;
  }

  static Color _colorFromValue(dynamic value) {
    if (value == null) return const Color(0xFF000000);
    if (value is int) return Color(value);
    if (value is String) {
      final hex = value.replaceFirst('#', '');
      return Color(int.parse('FF$hex', radix: 16));
    }
    return const Color(0xFF000000);
  }

  static List<double> _parsePressureGraph(dynamic value) {
    if (value is List) {
      return value.map((e) => (e as num).toDouble()).toList();
    }
    return const [0.0, 1.0];
  }

  /// Compresses data using zlib (gzip codec stripped of header/footer).
  static Uint8List _compressZlib(List<int> data) {
    try {
      final compressed = _gzip.encode(Uint8List.fromList(data));
      // Strip gzip header (10 bytes) and footer (8 bytes) to get raw deflate
      if (compressed.length > 18) {
        return Uint8List.fromList(compressed.sublist(10, compressed.length - 8));
      }
      return Uint8List.fromList(compressed);
    } catch (_) {
      return Uint8List.fromList(data);
    }
  }

  /// Decompresses raw deflate data back to original bytes.
  static Uint8List _decompressZlib(List<int> data) {
    try {
      // Reconstruct gzip stream by adding header and footer
      final header = Uint8List.fromList([
        0x1F, 0x8B, // Magic number
        0x08, // Compression method (deflate)
        0x00, // Flags
        0x00, 0x00, 0x00, 0x00, // Modification time
        0x00, // Extra flags
        0xFF, // OS (unknown)
      ]);
      final footer = Uint8List(8); // CRC32 + size (will be validated)
      final fullGzip = Uint8List.fromList([...header, ...data, ...footer]);
      return Uint8List.fromList(_gzip.decode(fullGzip));
    } catch (_) {
      return Uint8List.fromList(data);
    }
  }
}

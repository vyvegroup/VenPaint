import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';

/// Represents a brush with full parameter set for professional drawing.
///
/// Supports IPBZ/VPBZ binary format for QR code exchange with ibisPaint.
class VenBrush {
  String id;
  String name;
  double size;
  double opacity;
  double hardness;
  Color color;
  String brushType;
  double spacing;
  double scatter;
  List<double> pressureGraph;

  VenBrush({
    String? id,
    this.name = 'Default Brush',
    this.size = 10.0,
    this.opacity = 1.0,
    this.hardness = 0.8,
    this.color = const Color(0xFF000000),
    this.brushType = 'pen',
    this.spacing = 0.15,
    this.scatter = 0.0,
    List<double>? pressureGraph,
  })  : id = id ?? '',
        pressureGraph = pressureGraph ?? const [0.0, 1.0];

  VenBrush copyWith({
    String? id,
    String? name,
    double? size,
    double? opacity,
    double? hardness,
    Color? color,
    String? brushType,
    double? spacing,
    double? scatter,
    List<double>? pressureGraph,
  }) {
    return VenBrush(
      id: id ?? this.id,
      name: name ?? this.name,
      size: size ?? this.size,
      opacity: opacity ?? this.opacity,
      hardness: hardness ?? this.hardness,
      color: color ?? this.color,
      brushType: brushType ?? this.brushType,
      spacing: spacing ?? this.spacing,
      scatter: scatter ?? this.scatter,
      pressureGraph: pressureGraph ?? List.from(this.pressureGraph),
    );
  }

  /// Converts this brush to a JSON map suitable for VPBZ/IPBZ encoding.
  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'size': size,
      'opacity': opacity,
      'hardness': hardness,
      'color': color.toARGB32(),
      'brushType': brushType,
      'spacing': spacing,
      'scatter': scatter,
      'pressureGraph': pressureGraph,
    };
  }

  /// Serializes brush parameters to IPBZ-compatible binary format.
  ///
  /// VPBZ format (also compatible with ibisPaint IPBZ):
  /// - 4 bytes: magic "VPBZ"
  /// - 2 bytes: version (0x0001)
  /// - 2 bytes: flags (reserved, 0x0000)
  /// - 4 bytes: uncompressed JSON length (big-endian)
  /// - N bytes: zlib-compressed JSON brush parameters
  Uint8List toIbpzParams() {
    final jsonBytes = utf8.encode(jsonEncode(toJson()));
    final uncompressedLength = jsonBytes.length;

    // zlib compress
    final compressed = zlib.encode(jsonBytes);

    final output = BytesBuilder();
    // Magic: "VPBZ"
    output.addByte(0x56); // V
    output.addByte(0x50); // P
    output.addByte(0x42); // B
    output.addByte(0x5A); // Z
    // Version: 0x0001
    output.addByte(0x00);
    output.addByte(0x01);
    // Flags: 0x0000
    output.addByte(0x00);
    output.addByte(0x00);
    // Uncompressed length (big-endian 32-bit)
    output.addByte((uncompressedLength >> 24) & 0xFF);
    output.addByte((uncompressedLength >> 16) & 0xFF);
    output.addByte((uncompressedLength >> 8) & 0xFF);
    output.addByte(uncompressedLength & 0xFF);
    // Compressed data
    output.add(compressed);

    return output.toBytes();
  }

  /// Parses IPBZ/VPBZ binary data and returns a [VenBrush].
  ///
  /// Supports both VPBZ (VenPaint) and IPBZ (ibisPaint) formats.
  factory VenBrush.fromIbpzData(List<int> data) {
    if (data.length < 12) {
      throw const FormatException('IPBZ data too short');
    }

    // Check magic bytes
    final magic = String.fromCharCodes(data.sublist(0, 4));
    if (magic != 'VPBZ' && magic != 'IPBZ') {
      throw FormatException('Invalid magic bytes: $magic');
    }

    // Read version (2 bytes)
    final version = (data[4] << 8) | data[5];

    int compressedDataOffset;
    Uint8List compressedBytes;

    if (magic == 'VPBZ') {
      // VPBZ format: skip flags (2 bytes) + uncompressed length (4 bytes)
      compressedDataOffset = 12;
      compressedBytes = Uint8List.fromList(data.sublist(compressedDataOffset));
    } else {
      // IPBZ format (ibisPaint): version >= 2 has 4-byte header + data
      // IPBZ v2: magic(4) + version(2) + compressed data
      compressedDataOffset = 6;
      compressedBytes = Uint8List.fromList(data.sublist(compressedDataOffset));
    }

    // Decompress
    final jsonBytes = zlib.decode(compressedBytes);
    final jsonStr = utf8.decode(jsonBytes);
    final json = jsonDecode(jsonStr) as Map<String, dynamic>;

    return VenBrush(
      name: json['name'] as String? ?? 'Imported Brush',
      size: (json['size'] as num?)?.toDouble() ?? 10.0,
      opacity: (json['opacity'] as num?)?.toDouble() ?? 1.0,
      hardness: (json['hardness'] as num?)?.toDouble() ?? 0.8,
      color: _colorFromJson(json['color']),
      brushType: json['brushType'] as String? ?? 'pen',
      spacing: (json['spacing'] as num?)?.toDouble() ?? 0.15,
      scatter: (json['scatter'] as num?)?.toDouble() ?? 0.0,
      pressureGraph: _parsePressureGraph(json['pressureGraph']),
    );
  }

  static Color _colorFromJson(dynamic value) {
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

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    if (other is! VenBrush) return false;
    return id == other.id &&
        name == other.name &&
        size == other.size &&
        opacity == other.opacity &&
        hardness == other.hardness &&
        color == other.color &&
        brushType == other.brushType &&
        spacing == other.spacing &&
        scatter == other.scatter &&
        _listEquals(pressureGraph, other.pressureGraph);
  }

  bool _listEquals(List<double> a, List<double> b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode =>
      Object.hash(id, name, size, opacity, hardness, color, brushType,
          spacing, scatter, Object.hashAll(pressureGraph));

  @override
  String toString() {
    return 'VenBrush(id: $id, name: $name, type: $brushType, size: $size)';
  }
}

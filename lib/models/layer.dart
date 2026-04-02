import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';

/// Represents a single layer in the canvas composition.
///
/// Each layer holds its own pixel data, opacity, visibility, and blend mode.
class ArtLayer {
  String id = '';
  String name;
  final ui.Image? _image;
  double opacity;
  bool visible;
  BlendMode blendMode;
  bool locked;
  bool isBackground;

  /// Raw pixel data in RGBA format.
  /// Width and height are tracked separately via [width] and [height].
  Uint8List? pixels;
  int width;
  int height;

  /// Offset for layer positioning
  double offsetX;
  double offsetY;

  ArtLayer({
    String? id,
    required this.name,
    ui.Image? image,
    this.opacity = 1.0,
    this.visible = true,
    this.blendMode = BlendMode.srcOver,
    this.locked = false,
    this.isBackground = false,
    this.pixels,
    this.width = 1080,
    this.height = 1920,
    this.offsetX = 0.0,
    this.offsetY = 0.0,
  })  : id = id ?? '',
        _image = image;

  ui.Image? get image => _image;

  /// Creates an empty layer with the specified dimensions filled with transparent pixels.
  factory ArtLayer.empty({
    String? id,
    required String name,
    required int width,
    required int height,
    bool isBackground = false,
  }) {
    return ArtLayer(
      id: id,
      name: name,
      pixels: Uint8List(width * height * 4),
      width: width,
      height: height,
      isBackground: isBackground,
      opacity: isBackground ? 1.0 : 1.0,
    );
  }

  /// Creates a layer from raw RGBA pixel data.
  factory ArtLayer.fromPixels({
    String? id,
    required String name,
    required Uint8List pixels,
    required int width,
    required int height,
  }) {
    assert(pixels.length == width * height * 4,
        'Pixel buffer size mismatch: got ${pixels.length}, expected ${width * height * 4}');
    return ArtLayer(
      id: id,
      name: name,
      pixels: Uint8List.fromList(pixels),
      width: width,
      height: height,
    );
  }

  /// Creates a background layer filled with white.
  factory ArtLayer.whiteBackground({
    required int width,
    required int height,
  }) {
    final pixels = Uint8List(width * height * 4);
    for (int i = 0; i < pixels.length; i += 4) {
      pixels[i] = 255; // R
      pixels[i + 1] = 255; // G
      pixels[i + 2] = 255; // B
      pixels[i + 3] = 255; // A
    }
    return ArtLayer(
      name: 'Background',
      pixels: pixels,
      width: width,
      height: height,
      isBackground: true,
      opacity: 1.0,
    );
  }

  /// Gets the pixel color at the specified (x, y) coordinates.
  /// Returns null if coordinates are out of bounds.
  Color? getPixelAt(int x, int y) {
    if (x < 0 || x >= width || y < 0 || y >= height) return null;
    if (pixels == null) return null;
    final offset = (y * width + x) * 4;
    return Color.fromARGB(
      pixels![offset + 3], // A
      pixels![offset], // R
      pixels![offset + 1], // G
      pixels![offset + 2], // B
    );
  }

  /// Sets the pixel color at the specified (x, y) coordinates.
  void setPixelAt(int x, int y, Color color) {
    if (x < 0 || x >= width || y < 0 || y >= height) return;
    if (pixels == null) return;
    final offset = (y * width + x) * 4;
    pixels![offset] = color.red;
    pixels![offset + 1] = color.green;
    pixels![offset + 2] = color.blue;
    pixels![offset + 3] = color.alpha;
  }

  /// Clears all pixels to transparent.
  void clear() {
    if (pixels != null) {
      pixels!.fillRange(0, pixels!.length, 0);
    }
  }

  /// Fills the entire layer with the specified color.
  void fill(Color color) {
    if (pixels == null) return;
    for (int i = 0; i < pixels!.length; i += 4) {
      pixels![i] = color.red;
      pixels![i + 1] = color.green;
      pixels![i + 2] = color.blue;
      pixels![i + 3] = color.alpha;
    }
  }

  /// Creates a copy of this layer with independent pixel data.
  ArtLayer copy({String? newName}) {
    return ArtLayer(
      id: id,
      name: newName ?? name,
      pixels: pixels != null ? Uint8List.fromList(pixels!) : null,
      width: width,
      height: height,
      opacity: opacity,
      visible: visible,
      blendMode: blendMode,
      locked: locked,
      isBackground: isBackground,
      offsetX: offsetX,
      offsetY: offsetY,
    );
  }

  /// Returns whether this layer has any non-transparent pixels.
  bool hasContent() {
    if (pixels == null) return false;
    for (int i = 3; i < pixels!.length; i += 4) {
      if (pixels![i] > 0) return true;
    }
    return false;
  }

  /// Serializes layer metadata to JSON.
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'opacity': opacity,
      'visible': visible,
      'blendMode': blendMode.index,
      'locked': locked,
      'isBackground': isBackground,
      'width': width,
      'height': height,
      'offsetX': offsetX,
      'offsetY': offsetY,
    };
  }

  factory ArtLayer.fromJson(Map<String, dynamic> json) {
    return ArtLayer(
      id: json['id'] as String?,
      name: json['name'] as String? ?? 'Layer',
      opacity: (json['opacity'] as num?)?.toDouble() ?? 1.0,
      visible: json['visible'] as bool? ?? true,
      blendMode: BlendMode.values[json['blendMode'] as int? ?? 3],
      locked: json['locked'] as bool? ?? false,
      isBackground: json['isBackground'] as bool? ?? false,
      width: json['width'] as int? ?? 1080,
      height: json['height'] as int? ?? 1920,
      offsetX: (json['offsetX'] as num?)?.toDouble() ?? 0.0,
      offsetY: (json['offsetY'] as num?)?.toDouble() ?? 0.0,
    );
  }

  @override
  String toString() => 'ArtLayer(id: $id, name: $name, ${width}x$height, '
      'opacity: ${opacity.toStringAsFixed(2)}, visible: $visible)';
}

import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../models/brush.dart';
import '../models/canvas_state.dart';
import '../models/layer.dart';

/// A single stroke point with pressure information.
class StrokePoint {
  final double x;
  final double y;
  final double pressure;
  final DateTime timestamp;

  StrokePoint({
    required this.x,
    required this.y,
    this.pressure = 0.5,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();
}

/// A complete stroke consisting of multiple points.
class Stroke {
  final List<StrokePoint> points;
  final VenBrush brush;
  final int layerIndex;

  Stroke({
    required this.points,
    required this.brush,
    required this.layerIndex,
  });

  void addPoint(StrokePoint point) {
    points.add(point);
  }

  bool get isEmpty => points.isEmpty;
  int get length => points.length;
}

/// CustomPainter-based drawing engine supporting pressure-sensitive
/// multi-brush-type drawing, undo/redo, and layer compositing.
class DrawingEngine {
  CanvasState? _state;

  /// Current stroke being drawn.
  Stroke? _currentStroke;

  /// Whether a stroke is currently being drawn.
  bool get isDrawing => _currentStroke != null;

  void attachState(CanvasState state) {
    _state = state;
  }

  void detachState() {
    _state = null;
  }

  /// Begins a new stroke at the given position.
  void beginStroke(double x, double y, {double pressure = 0.5}) {
    if (_state == null) return;
    final brush = _state!.currentBrush;
    _currentStroke = Stroke(
      points: [StrokePoint(x: x, y: y, pressure: pressure)],
      brush: brush.copyWith(),
      layerIndex: _state!.activeLayerIndex,
    );
    _applyDab(x, y, pressure, brush);
  }

  /// Continues the current stroke to a new position.
  void continueStroke(double x, double y, {double pressure = 0.5}) {
    if (_currentStroke == null || _state == null) return;
    _currentStroke!.addPoint(StrokePoint(x: x, y: y, pressure: pressure));

    final brush = _currentStroke!.brush;
    final points = _currentStroke!.points;
    if (points.length < 2) return;

    final prev = points[points.length - 2];
    final curr = points[points.length - 1];

    _interpolateStroke(prev, curr, brush);
  }

  /// Ends the current stroke and saves undo state.
  void endStroke() {
    if (_currentStroke == null || _state == null) return;
    _state!.saveSnapshot();
    _currentStroke = null;
    _state!.notifyListeners();
  }

  /// Cancels the current stroke without saving.
  void cancelStroke() {
    if (_currentStroke == null) return;
    _currentStroke = null;
  }

  /// Applies a single brush dab at the given position.
  void _applyDab(double x, double y, double pressure, VenBrush brush) {
    if (_state == null) return;
    final layer = _state!.activeLayer;
    if (layer.pixels == null || layer.locked) return;

    final effectiveSize = _getEffectiveSize(brush, pressure);
    final effectiveOpacity = _getEffectiveOpacity(brush, pressure);

    switch (brush.brushType) {
      case 'pen':
        _drawRoundDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
        break;
      case 'pencil':
        _drawPencilDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
        break;
      case 'airbrush':
        _drawAirbrushDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
        break;
      case 'watercolor':
        _drawWatercolorDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
        break;
      case 'oil':
        _drawOilDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
        break;
      case 'eraser':
        _drawEraserDab(layer, x.round(), y.round(), effectiveSize, effectiveOpacity);
        break;
      default:
        _drawRoundDab(layer, x.round(), y.round(), effectiveSize, brush.color, effectiveOpacity);
    }
  }

  /// Interpolates between two stroke points, placing dabs along the path.
  void _interpolateStroke(StrokePoint from, StrokePoint to, VenBrush brush) {
    final dx = to.x - from.x;
    final dy = to.y - from.y;
    final dist = math.sqrt(dx * dx + dy * dy);

    if (dist < 1.0) return;

    final effectiveSize = _getEffectiveSize(brush, to.pressure);
    final spacing = brush.spacing * effectiveSize;
    final numDabs = (dist / spacing).ceil();

    for (int i = 0; i <= numDabs; i++) {
      final t = i / numDabs;
      final pressure = from.pressure + (to.pressure - from.pressure) * t;
      final x = from.x + dx * t;
      final y = from.y + dy * t;
      _applyDab(x, y, pressure, brush);
    }
  }

  /// Calculates the effective brush size based on pressure.
  double _getEffectiveSize(VenBrush brush, double pressure) {
    final pressureFactor = _interpolatePressureGraph(brush.pressureGraph, pressure);
    return brush.size * pressureFactor;
  }

  /// Calculates the effective opacity based on pressure.
  double _getEffectiveOpacity(VenBrush brush, double pressure) {
    return brush.opacity * pressure;
  }

  /// Interpolates the pressure graph at the given pressure value [0.0, 1.0].
  double _interpolatePressureGraph(List<double> graph, double pressure) {
    if (graph.isEmpty) return 1.0;
    if (graph.length == 1) return graph[0];

    final t = pressure.clamp(0.0, 1.0);
    final segment = t * (graph.length - 1);
    final index = segment.floor().clamp(0, graph.length - 2);
    final frac = segment - index;

    return graph[index] + (graph[index + 1] - graph[index]) * frac;
  }

  /// Draws a round solid dab (standard pen brush).
  void _drawRoundDab(ArtLayer layer, int cx, int cy, double size, Color color, double opacity) {
    final radius = (size / 2).round();
    if (radius <= 0) return;

    final hardness = _state!.currentBrush.hardness;
    final pixels = layer.pixels;
    if (pixels == null) return;

    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        final dist = math.sqrt(dx * dx + dy * dy);
        if (dist > radius) continue;

        final px = cx + dx;
        final py = cy + dy;
        if (px < 0 || px >= layer.width || py < 0 || py >= layer.height) continue;

        double alpha;
        if (hardness >= 1.0) {
          alpha = dist <= radius ? 1.0 : 0.0;
        } else {
          final edgeStart = radius * hardness;
          if (dist <= edgeStart) {
            alpha = 1.0;
          } else {
            alpha = 1.0 - ((dist - edgeStart) / (radius - edgeStart));
          }
        }
        alpha *= opacity;

        _blendPixel(pixels, px, py, layer.width, color, alpha);
      }
    }
  }

  /// Draws a pencil-style dab with scattered sub-dots and texture.
  void _drawPencilDab(ArtLayer layer, int cx, int cy, double size, Color color, double opacity) {
    final radius = (size / 2).round();
    final scatter = _state!.currentBrush.scatter;
    final pixels = layer.pixels;
    if (pixels == null || radius <= 0) return;

    final rng = math.Random(cx * 10000 + cy);
    final numDots = (radius * radius * 0.8).round();

    for (int i = 0; i < numDots; i++) {
      final angle = rng.nextDouble() * 2 * math.pi;
      final dist = rng.nextDouble() * radius;
      final sx = cx + (dist * math.cos(angle) + rng.nextDouble() * scatter).round();
      final sy = cy + (dist * math.sin(angle) + rng.nextDouble() * scatter).round();

      if (sx < 0 || sx >= layer.width || sy < 0 || sy >= layer.height) continue;

      final dotOpacity = opacity * (0.3 + rng.nextDouble() * 0.7);
      _blendPixel(pixels, sx, sy, layer.width, color, dotOpacity);
    }
  }

  /// Draws an airbrush-style soft spray.
  void _drawAirbrushDab(ArtLayer layer, int cx, int cy, double size, Color color, double opacity) {
    final radius = (size / 2).round();
    final pixels = layer.pixels;
    if (pixels == null || radius <= 0) return;

    final rng = math.Random();
    final numParticles = (radius * radius * 0.3).round();

    for (int i = 0; i < numParticles; i++) {
      final angle = rng.nextDouble() * 2 * math.pi;
      // Gaussian-like distribution using Box-Muller
      final u1 = rng.nextDouble();
      final u2 = rng.nextDouble();
      final gauss = math.sqrt(-2 * math.log(u1)) * math.cos(2 * math.pi * u2);
      final dist = (gauss * radius * 0.4).abs().clamp(0.0, radius.toDouble());

      final sx = cx + (dist * math.cos(angle)).round();
      final sy = cy + (dist * math.sin(angle)).round();

      if (sx < 0 || sx >= layer.width || sy < 0 || sy >= layer.height) continue;

      final falloff = 1.0 - (dist / radius);
      final dotOpacity = opacity * falloff * 0.15;
      _blendPixel(pixels, sx, sy, layer.width, color, dotOpacity);
    }
  }

  /// Draws a watercolor-style dab with soft edges and bleed.
  void _drawWatercolorDab(ArtLayer layer, int cx, int cy, double size, Color color, double opacity) {
    final radius = (size / 2).round();
    final pixels = layer.pixels;
    if (pixels == null || radius <= 0) return;

    // Multiple concentric soft rings
    for (int ring = radius; ring > 0; ring = (ring * 0.7).floor()) {
      final ringRadius = ring.round();
      final ringOpacity = opacity * 0.08 * (1.0 - ring / radius);

      for (int dy = -ringRadius; dy <= ringRadius; dy++) {
        for (int dx = -ringRadius; dx <= ringRadius; dx++) {
          final dist = math.sqrt(dx * dx + dy * dy);
          if (dist > ringRadius) continue;

          final px = cx + dx;
          final py = cy + dy;
          if (px < 0 || px >= layer.width || py < 0 || py >= layer.height) continue;

          final falloff = 1.0 - (dist / ringRadius);
          final alpha = ringOpacity * falloff;
          _blendPixel(pixels, px, py, layer.width, color, alpha);
        }
      }
    }
  }

  /// Draws an oil brush-style dab with bristle texture.
  void _drawOilDab(ArtLayer layer, int cx, int cy, double size, Color color, double opacity) {
    final radius = (size / 2).round();
    final pixels = layer.pixels;
    if (pixels == null || radius <= 0) return;

    // Simulate bristle marks with elongated rectangles
    final rng = math.Random(cx * 31337 + cy);
    final numBristles = (size * 1.5).round();

    for (int i = 0; i < numBristles; i++) {
      final offsetX = (rng.nextDouble() - 0.5) * size;
      final bristleWidth = 1 + rng.nextInt(3);
      final bristleLength = (radius * (0.6 + rng.nextDouble() * 0.8)).round();

      for (int j = -bristleLength; j <= bristleLength; j++) {
        for (int bw = 0; bw < bristleWidth; bw++) {
          final px = cx + offsetX.round() + bw;
          final py = cy + j;
          if (px < 0 || px >= layer.width || py < 0 || py >= layer.height) continue;

          final edgeFalloff = 1.0 - (j.abs() / bristleLength);
          final alpha = opacity * edgeFalloff * 0.4;
          _blendPixel(pixels, px, py, layer.width, color, alpha);
        }
      }
    }
  }

  /// Draws an eraser dab (sets alpha to 0).
  void _drawEraserDab(ArtLayer layer, int cx, int cy, double size, double opacity) {
    final radius = (size / 2).round();
    if (radius <= 0) return;

    final pixels = layer.pixels;
    if (pixels == null) return;

    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        final dist = math.sqrt(dx * dx + dy * dy);
        if (dist > radius) continue;

        final px = cx + dx;
        final py = cy + dy;
        if (px < 0 || px >= layer.width || py < 0 || py >= layer.height) continue;

        final edgeFalloff = 1.0 - (dist / radius);
        final offset = (py * layer.width + px) * 4;
        final currentAlpha = pixels[offset + 3];
        final newAlpha = (currentAlpha * (1.0 - edgeFalloff * opacity)).round().clamp(0, 255);
        pixels[offset + 3] = newAlpha;
      }
    }
  }

  /// Alpha-blends a pixel color onto the pixel buffer.
  void _blendPixel(Uint8List pixels, int x, int y, int width, Color color, double alpha) {
    final offset = (y * width + x) * 4;
    final a = (alpha * 255).round().clamp(0, 255);
    if (a == 0) return;

    if (a >= 255) {
      pixels[offset] = color.red;
      pixels[offset + 1] = color.green;
      pixels[offset + 2] = color.blue;
      pixels[offset + 3] = 255;
      return;
    }

    final invAlpha = 255 - a;
    pixels[offset] = (color.red * a + pixels[offset] * invAlpha) ~/ 255;
    pixels[offset + 1] = (color.green * a + pixels[offset + 1] * invAlpha) ~/ 255;
    pixels[offset + 2] = (color.blue * a + pixels[offset + 2] * invAlpha) ~/ 255;
    pixels[offset + 3] = (a + pixels[offset + 3] * invAlpha) ~/ 255;
  }

  /// Fills an area with the given color using flood fill algorithm.
  void floodFill(int startX, int startY, Color fillColor) {
    if (_state == null) return;
    final layer = _state!.activeLayer;
    if (layer.pixels == null || layer.locked) return;

    startX = startX.clamp(0, layer.width - 1);
    startY = startY.clamp(0, layer.height - 1);

    final pixels = layer.pixels!;
    final targetOffset = (startY * layer.width + startX) * 4;
    final targetColor = Color.fromARGB(
      pixels[targetOffset + 3],
      pixels[targetOffset],
      pixels[targetOffset + 1],
      pixels[targetOffset + 2],
    );

    if (targetColor == fillColor) return;

    final tolerance = 32;
    final visited = Uint8List(layer.width * layer.height);
    final stack = <int>[startY * layer.width + startX];

    while (stack.isNotEmpty) {
      final pos = stack.removeLast();
      if (visited[pos] == 1) continue;
      visited[pos] = 1;

      final px = pos % layer.width;
      final py = pos ~/ layer.width;
      final offset = pos * 4;

      final pr = pixels[offset];
      final pg = pixels[offset + 1];
      final pb = pixels[offset + 2];
      final pa = pixels[offset + 3];

      if (!_colorsMatch(pr, pg, pb, pa, targetColor, tolerance)) continue;

      pixels[offset] = fillColor.red;
      pixels[offset + 1] = fillColor.green;
      pixels[offset + 2] = fillColor.blue;
      pixels[offset + 3] = fillColor.alpha;

      if (px > 0) stack.add(pos - 1);
      if (px < layer.width - 1) stack.add(pos + 1);
      if (py > 0) stack.add(pos - layer.width);
      if (py < layer.height - 1) stack.add(pos + layer.width);
    }

    _state!.saveSnapshot();
    _state!.notifyListeners();
  }

  bool _colorsMatch(int r, int g, int b, int a, Color target, int tolerance) {
    return (r - target.red).abs() <= tolerance &&
        (g - target.green).abs() <= tolerance &&
        (b - target.blue).abs() <= tolerance &&
        (a - target.alpha).abs() <= tolerance;
  }

  /// Picks the color at the specified canvas position.
  Color? pickColor(int x, int y) {
    if (_state == null) return null;
    // Check layers top to bottom
    for (int i = _state!.layers.length - 1; i >= 0; i--) {
      final layer = _state!.layers[i];
      if (!layer.visible) continue;
      final color = layer.getPixelAt(x, y);
      if (color != null && color.alpha > 0) {
        return color;
      }
    }
    return null;
  }

  /// Composites all visible layers into a single [ui.Image].
  Future<ui.Image?> compositeAllLayers() async {
    if (_state == null) return null;

    final width = _state!.canvasWidth;
    final height = _state!.canvasHeight;
    final pixelData = Uint8List(width * height * 4);

    // Start with transparent background
    pixelData.fillRange(0, pixelData.length, 0);

    // Composite each visible layer
    for (final layer in _state!.layers) {
      if (!layer.visible || layer.pixels == null) continue;
      _compositeLayerOnBuffer(pixelData, layer, width, height);
    }

    final bytes = await ui.decodeImageFromPixels(
      pixelData,
      width,
      height,
      ui.PixelFormat.rgba8888,
    );

    return bytes;
  }

  void _compositeLayerOnBuffer(Uint8List target, ArtLayer layer, int width, int height) {
    final src = layer.pixels;
    if (src == null) return;

    final layerOpacity = (layer.opacity * 255).round();
    if (layerOpacity == 0) return;

    final invOpacity = 255 - layerOpacity;

    for (int i = 0; i < target.length && i < src.length; i += 4) {
      final sa = (src[i + 3] * layerOpacity) ~/ 255;
      if (sa == 0) continue;

      final da = target[i + 3];
      final finalAlpha = sa + da * (255 - sa) ~/ 255;

      final invSa = 255 - sa;
      target[i] = (src[i] * sa + target[i] * da * invSa ~/ 255) ~/ finalAlpha;
      target[i + 1] = (src[i + 1] * sa + target[i + 1] * da * invSa ~/ 255) ~/ finalAlpha;
      target[i + 2] = (src[i + 2] * sa + target[i + 2] * da * invSa ~/ 255) ~/ finalAlpha;
      target[i + 3] = finalAlpha;
    }
  }

  /// Gets the pixel data for a specific layer as a renderable format.
  Future<ui.Image?> layerToImage(ArtLayer layer) async {
    if (layer.pixels == null) return null;
    try {
      return await ui.decodeImageFromPixels(
        layer.pixels!,
        layer.width,
        layer.height,
        ui.PixelFormat.rgba8888,
      );
    } catch (_) {
      return null;
    }
  }
}

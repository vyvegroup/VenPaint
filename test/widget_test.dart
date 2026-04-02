import 'package:flutter_test/flutter_test.dart';
import 'package:venpaint/models/brush.dart';
import 'package:venpaint/models/canvas_state.dart';
import 'package:venpaint/services/ipbz_service.dart';
import 'dart:typed_data';
import 'dart:convert';

void main() {
  group('VenBrush', () {
    test('creates default brush with correct values', () {
      final brush = VenBrush();
      expect(brush.name, 'Default Brush');
      expect(brush.size, 10.0);
      expect(brush.opacity, 1.0);
      expect(brush.hardness, 0.8);
      expect(brush.color, const Color(0xFF000000));
      expect(brush.brushType, 'pen');
      expect(brush.spacing, 0.15);
      expect(brush.scatter, 0.0);
      expect(brush.pressureGraph, [0.0, 1.0]);
    });

    test('copyWith creates independent copy', () {
      final original = VenBrush(
        name: 'Test Brush',
        size: 20.0,
        opacity: 0.5,
        color: const Color(0xFFFF0000),
        brushType: 'airbrush',
      );
      final copy = original.copyWith(size: 30.0);

      expect(copy.name, original.name);
      expect(copy.size, 30.0);
      expect(original.size, 20.0);
      expect(copy.opacity, original.opacity);
    });

    test('toJson and fromJson round-trip', () {
      final brush = VenBrush(
        name: 'Round Pen',
        size: 8.0,
        opacity: 0.9,
        hardness: 1.0,
        color: const Color(0xFF444444),
        brushType: 'pen',
        spacing: 0.2,
        scatter: 0.0,
        pressureGraph: [0.0, 0.5, 1.0],
      );

      final json = brush.toJson();
      expect(json['name'], 'Round Pen');
      expect(json['size'], 8.0);
      expect(json['brushType'], 'pen');

      final restored = IpbzService.brushFromJson(jsonEncode(json));
      expect(restored.name, brush.name);
      expect(restored.size, brush.size);
      expect(restored.opacity, brush.opacity);
      expect(restored.hardness, brush.hardness);
      expect(restored.brushType, brush.brushType);
    });
  });

  group('VPBZ/IPBZ Codec', () {
    test('generates valid VPBZ data', () {
      final brush = VenBrush(
        name: 'Test Brush',
        size: 15.0,
        opacity: 0.8,
        hardness: 0.6,
        brushType: 'pencil',
      );

      final data = IpbzService.generateVpbzData(brush);
      expect(data.length, greaterThan(12));

      // Check magic bytes
      expect(data[0], 0x56); // V
      expect(data[1], 0x50); // P
      expect(data[2], 0x42); // B
      expect(data[3], 0x5A); // Z
    });

    test('round-trips VPBZ encode/decode', () {
      final original = VenBrush(
        name: 'Round Pen',
        size: 8.0,
        opacity: 1.0,
        hardness: 1.0,
        color: const Color(0xFF000000),
        brushType: 'pen',
        spacing: 0.15,
        scatter: 0.0,
        pressureGraph: [0.0, 1.0],
      );

      final data = IpbzService.generateVpbzData(original);
      final restored = VenBrush.fromIpbzData(data);

      expect(restored.name, original.name);
      expect(restored.size, original.size);
      expect(restored.opacity, closeTo(original.opacity, 0.01));
      expect(restored.hardness, closeTo(original.hardness, 0.01));
      expect(restored.brushType, original.brushType);
      expect(restored.spacing, closeTo(original.spacing, 0.01));
      expect(restored.scatter, closeTo(original.scatter, 0.01));
    });

    test('validates VPBZ data correctly', () {
      final brush = VenBrush(name: 'Test', size: 10.0);
      final validData = IpbzService.generateVpbzData(brush);
      expect(IpbzService.isValidVpbzData(validData), true);

      final invalidData = Uint8List.fromList([1, 2, 3, 4, 5, 6]);
      expect(IpbzService.isValidVpbzData(invalidData), false);

      final tooShort = Uint8List.fromList([0x56, 0x50]);
      expect(IpbzService.isValidVpbzData(tooShort), false);
    });

    test('identifies VPBZ format type', () {
      final brush = VenBrush(name: 'Test', size: 10.0);
      final data = IpbzService.generateVpbzData(brush);
      final formatType = IpbzService.getFormatType(data);
      expect(formatType, contains('VPBZ'));
    });

    test('optimizes brush for QR encoding', () {
      // Create a brush with many pressure graph points and long name
      final longName = 'A' * 30;
      final longPressureGraph = List.generate(
        50,
        (i) => i / 49.0,
      );
      final brush = VenBrush(
        name: longName,
        size: 10.0,
        pressureGraph: longPressureGraph,
      );

      final optimized = IpbzService.optimizeForQr(brush);
      expect(optimized.name.length, lessThanOrEqualTo(20));
      expect(optimized.pressureGraph.length, lessThanOrEqualTo(10));
    });
  });

  group('CanvasState', () {
    test('initializes with background and drawing layer', () {
      final state = CanvasState();
      expect(state.layers.length, 2);
      expect(state.layers[0].isBackground, true);
      expect(state.layers[1].name, 'Layer 1');
      expect(state.activeLayerIndex, 1);
    });

    test('adds and removes layers correctly', () {
      final state = CanvasState();
      state.addLayer(name: 'New Layer');
      expect(state.layers.length, 3);
      expect(state.activeLayerIndex, 2);

      state.removeLayer(2);
      expect(state.layers.length, 2);
      expect(state.activeLayerIndex, 1);
    });

    test('cannot remove background layer', () {
      final state = CanvasState();
      state.removeLayer(0);
      expect(state.layers.length, 2);
    });

    test('undo and redo work correctly', () {
      final state = CanvasState();
      expect(state.canUndo, true);
      expect(state.canRedo, false);

      state.addLayer(name: 'Test Layer');
      expect(state.canUndo, true);
      expect(state.canRedo, false);

      state.undo();
      expect(state.layers.length, 2);
      expect(state.canRedo, true);

      state.redo();
      expect(state.layers.length, 3);
      expect(state.canRedo, false);
    });

    test('layer opacity is clamped to valid range', () {
      final state = CanvasState();
      state.setLayerOpacity(1, 1.5);
      expect(state.layers[1].opacity, 1.0);

      state.setLayerOpacity(1, -0.5);
      expect(state.layers[1].opacity, 0.0);
    });

    test('color swap works correctly', () {
      final state = CanvasState();
      state.setPrimaryColor(const Color(0xFFFF0000));
      state.setSecondaryColor(const Color(0xFF0000FF));
      state.swapColors();

      expect(state.primaryColor, const Color(0xFF0000FF));
      expect(state.secondaryColor, const Color(0xFFFF0000));
    });

    test('new project resets state', () {
      final state = CanvasState();
      state.addLayer(name: 'Extra');
      state.setPrimaryColor(const Color(0xFFFF0000));

      state.newProject(width: 800, height: 600, name: 'New Project');
      expect(state.layers.length, 2);
      expect(state.canvasWidth, 800);
      expect(state.canvasHeight, 600);
      expect(state.projectName, 'New Project');
    });
  });

  group('ArtLayer', () {
    test('creates white background layer', () {
      final layer = ArtLayer.whiteBackground(width: 100, height: 100);
      expect(layer.isBackground, true);
      expect(layer.width, 100);
      expect(layer.height, 100);
      expect(layer.pixels, isNotNull);

      // Check first pixel is white
      final pixel = layer.getPixelAt(0, 0);
      expect(pixel, isNotNull);
      expect(pixel!.red, 255);
      expect(pixel.green, 255);
      expect(pixel.blue, 255);
      expect(pixel.alpha, 255);
    });

    test('empty layer has transparent pixels', () {
      final layer = ArtLayer.empty(name: 'Empty', width: 50, height: 50);
      final pixel = layer.getPixelAt(0, 0);
      expect(pixel, isNotNull);
      expect(pixel!.alpha, 0);
    });

    test('set and get pixel at coordinates', () {
      final layer = ArtLayer.empty(name: 'Test', width: 10, height: 10);
      layer.setPixelAt(5, 5, const Color(0xFFFF0000));
      final pixel = layer.getPixelAt(5, 5);
      expect(pixel, isNotNull);
      expect(pixel!.red, 255);
      expect(pixel.green, 0);
      expect(pixel.blue, 0);
    });

    test('getPixelAt returns null for out of bounds', () {
      final layer = ArtLayer.empty(name: 'Test', width: 10, height: 10);
      expect(layer.getPixelAt(-1, 0), isNull);
      expect(layer.getPixelAt(10, 0), isNull);
      expect(layer.getPixelAt(0, -1), isNull);
      expect(layer.getPixelAt(0, 10), isNull);
    });

    test('fill sets all pixels to specified color', () {
      final layer = ArtLayer.empty(name: 'Test', width: 10, height: 10);
      layer.fill(const Color(0xFF00FF00));
      final pixel = layer.getPixelAt(5, 5);
      expect(pixel, isNotNull);
      expect(pixel!.green, 255);
      expect(pixel.red, 0);
    });

    test('clear resets all pixels to transparent', () {
      final layer = ArtLayer.whiteBackground(width: 10, height: 10);
      layer.clear();
      expect(layer.hasContent(), false);
    });

    test('hasContent detects non-transparent pixels', () {
      final layer = ArtLayer.empty(name: 'Test', width: 10, height: 10);
      expect(layer.hasContent(), false);

      layer.setPixelAt(0, 0, const Color(0x01000000)); // Nearly transparent
      expect(layer.hasContent(), true);
    });

    test('copy creates independent layer', () {
      final layer = ArtLayer.empty(name: 'Original', width: 10, height: 10);
      layer.setPixelAt(0, 0, const Color(0xFFFF0000));

      final copy = layer.copy(newName: 'Copy');
      expect(copy.name, 'Copy');
      expect(copy.getPixelAt(0, 0)!.red, 255);

      // Modify copy should not affect original
      copy.setPixelAt(0, 0, const Color(0xFF0000FF));
      expect(layer.getPixelAt(0, 0)!.red, 255);
    });
  });

  group('BrushQrService validation', () {
    test('detects brush QR data correctly', () {
      final brush = VenBrush(name: 'Test', size: 10.0);
      final qrData = IpbzService.generateVpbzData(brush);
      final base64Qr = base64Encode(qrData);

      expect(BrushQrService.isBrushQR(base64Qr), true);
      expect(BrushQrService.isBrushQR('not a brush'), false);
      expect(BrushQrService.isBrushQR(''), false);
    });

    test('scans valid brush QR data', () {
      final original = VenBrush(
        name: 'Test Brush',
        size: 12.0,
        opacity: 0.8,
        hardness: 0.5,
        brushType: 'pen',
      );

      final qrData = IpbzService.generateVpbzData(original);
      final base64Qr = base64Encode(qrData);

      final scanned = BrushQrService.scanBrushQR(base64Qr);
      expect(scanned, isNotNull);
      expect(scanned!.name, original.name);
      expect(scanned.size, original.size);
      expect(scanned.brushType, original.brushType);
    });

    test('returns null for invalid QR data', () {
      expect(BrushQrService.scanBrushQR('invalid data'), isNull);
      expect(BrushQrService.scanBrushQR(''), isNull);
    });

    test('validates and describes brush QR', () {
      final brush = VenBrush(name: 'My Brush', size: 15.0, brushType: 'airbrush');
      final qrData = base64Encode(IpbzService.generateVpbzData(brush));

      final description = BrushQrService.validateBrushQR(qrData);
      expect(description, isNotNull);
      expect(description!, contains('My Brush'));
      expect(description, contains('airbrush'));
      expect(description, contains('15.0'));
    });
  });
}

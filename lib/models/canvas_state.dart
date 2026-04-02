import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'brush.dart';
import 'layer.dart';

/// Tool types available in the drawing canvas.
enum DrawingTool {
  pen,
  pencil,
  airbrush,
  watercolor,
  oil,
  eraser,
  fill,
  select,
  move,
  eyedropper,
}

/// Complete canvas state managed via ChangeNotifier for Provider integration.
///
/// Tracks all layers, brush settings, undo/redo history, and canvas metadata.
class CanvasState extends ChangeNotifier {
  // Canvas dimensions
  int canvasWidth = 1080;
  int canvasHeight = 1920;

  // Layers
  final List<ArtLayer> _layers = [];
  int _activeLayerIndex = 0;

  // Current tool and brush
  DrawingTool _currentTool = DrawingTool.pen;
  VenBrush _currentBrush = VenBrush();

  // Color
  Color _primaryColor = const Color(0xFF000000);
  Color _secondaryColor = const Color(0xFFFFFFFF);

  // View state
  double _zoom = 1.0;
  double _panX = 0.0;
  double _panY = 0.0;

  // Undo/Redo
  final List<CanvasSnapshot> _undoStack = [];
  final List<CanvasSnapshot> _redoStack = [];
  static const int maxUndoSteps = 50;

  // Brush presets
  final List<VenBrush> _brushPresets = [];

  // Project metadata
  String _projectName = 'Untitled';
  String? _projectFilePath;

  // Selection state
  Rect? _selectionRect;
  bool _selectionActive = false;

  CanvasState() {
    _initializeDefaults();
  }

  void _initializeDefaults() {
    // Create background layer
    final background =
        ArtLayer.whiteBackground(width: canvasWidth, height: canvasHeight);
    background.id = 'bg';
    background.name = 'Background';
    _layers.add(background);

    // Create initial drawing layer
    final drawingLayer = ArtLayer.empty(
      id: 'layer_1',
      name: 'Layer 1',
      width: canvasWidth,
      height: canvasHeight,
    );
    _layers.add(drawingLayer);
    _activeLayerIndex = 1;

    // Initialize default brush presets
    _brushPresets.addAll([
      VenBrush(id: 'preset_pen', name: 'Round Pen', size: 8.0, brushType: 'pen', hardness: 1.0),
      VenBrush(id: 'preset_pencil', name: 'Pencil', size: 4.0, brushType: 'pencil', hardness: 0.6, scatter: 2.0),
      VenBrush(id: 'preset_airbrush', name: 'Airbrush', size: 20.0, brushType: 'airbrush', hardness: 0.0, opacity: 0.3),
      VenBrush(id: 'preset_watercolor', name: 'Watercolor', size: 30.0, brushType: 'watercolor', hardness: 0.1, opacity: 0.5),
      VenBrush(id: 'preset_oil', name: 'Oil Brush', size: 15.0, brushType: 'oil', hardness: 0.4, spacing: 0.3),
      VenBrush(id: 'preset_eraser', name: 'Eraser', size: 20.0, brushType: 'eraser', hardness: 1.0),
      VenBrush(id: 'preset_ink', name: 'Inking Pen', size: 3.0, brushType: 'pen', hardness: 1.0, opacity: 1.0),
      VenBrush(id: 'preset_marker', name: 'Marker', size: 12.0, brushType: 'pen', hardness: 0.3, opacity: 0.7),
    ]);

    saveSnapshot();
    notifyListeners();
  }

  // --- Getters ---
  List<ArtLayer> get layers => List.unmodifiable(_layers);
  int get activeLayerIndex => _activeLayerIndex;
  ArtLayer get activeLayer => _layers[_activeLayerIndex];
  DrawingTool get currentTool => _currentTool;
  VenBrush get currentBrush => _currentBrush;
  Color get primaryColor => _primaryColor;
  Color get secondaryColor => _secondaryColor;
  double get zoom => _zoom;
  double get panX => _panX;
  double get panY => _panY;
  List<VenBrush> get brushPresets => List.unmodifiable(_brushPresets);
  String get projectName => _projectName;
  String? get projectFilePath => _projectFilePath;
  Rect? get selectionRect => _selectionRect;
  bool get selectionActive => _selectionActive;
  int get undoCount => _undoStack.length;
  int get redoCount => _redoStack.length;

  // --- Layer operations ---
  void addLayer({String? name}) {
    final index = _layers.length;
    final layer = ArtLayer.empty(
      id: 'layer_$index',
      name: name ?? 'Layer $index',
      width: canvasWidth,
      height: canvasHeight,
    );
    _layers.insert(_activeLayerIndex + 1, layer);
    _activeLayerIndex = _activeLayerIndex + 1;
    notifyListeners();
  }

  void removeLayer(int index) {
    if (_layers.length <= 1) return;
    if (index < 0 || index >= _layers.length) return;
    if (_layers[index].isBackground) return;
    _layers.removeAt(index);
    if (_activeLayerIndex >= _layers.length) {
      _activeLayerIndex = _layers.length - 1;
    } else if (_activeLayerIndex > index) {
      _activeLayerIndex--;
    }
    notifyListeners();
  }

  void setActiveLayer(int index) {
    if (index < 0 || index >= _layers.length) return;
    _activeLayerIndex = index;
    notifyListeners();
  }

  void moveLayer(int fromIndex, int toIndex) {
    if (fromIndex == toIndex) return;
    if (fromIndex < 0 || fromIndex >= _layers.length) return;
    if (toIndex < 0 || toIndex >= _layers.length) return;
    final layer = _layers.removeAt(fromIndex);
    _layers.insert(toIndex, layer);
    if (_activeLayerIndex == fromIndex) {
      _activeLayerIndex = toIndex;
    }
    notifyListeners();
  }

  void setLayerOpacity(int index, double opacity) {
    if (index < 0 || index >= _layers.length) return;
    _layers[index].opacity = opacity.clamp(0.0, 1.0);
    notifyListeners();
  }

  void setLayerVisibility(int index, bool visible) {
    if (index < 0 || index >= _layers.length) return;
    _layers[index].visible = visible;
    notifyListeners();
  }

  void setLayerBlendMode(int index, BlendMode blendMode) {
    if (index < 0 || index >= _layers.length) return;
    _layers[index].blendMode = blendMode;
    notifyListeners();
  }

  void renameLayer(int index, String name) {
    if (index < 0 || index >= _layers.length) return;
    _layers[index].name = name;
    notifyListeners();
  }

  void duplicateLayer(int index) {
    if (index < 0 || index >= _layers.length) return;
    final copy = _layers[index].copy(newName: '${_layers[index].name} copy');
    _layers.insert(index + 1, copy);
    _activeLayerIndex = index + 1;
    notifyListeners();
  }

  void mergeDown(int index) {
    if (index <= 0 || index >= _layers.length) return;
    final upper = _layers[index];
    final lower = _layers[index - 1];
    // Simple merge: composite upper onto lower pixel by pixel
    _compositePixels(lower, upper);
    _layers.removeAt(index);
    _activeLayerIndex = index - 1;
    notifyListeners();
  }

  void _compositePixels(ArtLayer target, ArtLayer source) {
    if (target.pixels == null || source.pixels == null) return;
    final srcOpacity = source.opacity;
    for (int i = 0; i < target.pixels!.length; i += 4) {
      final sr = source.pixels![i];
      final sg = source.pixels![i + 1];
      final sb = source.pixels![i + 2];
      final sa = (source.pixels![i + 3] * srcOpacity).round();

      if (sa == 0) continue;

      final dr = target.pixels![i];
      final dg = target.pixels![i + 1];
      final db = target.pixels![i + 2];
      final da = target.pixels![i + 3];

      final alpha = sa + da * (255 - sa) ~/ 255;
      if (alpha == 0) continue;

      final invSa = 255 - sa;
      target.pixels![i] = (sr * sa + dr * da * invSa ~/ 255) ~/ alpha;
      target.pixels![i + 1] = (sg * sa + dg * da * invSa ~/ 255) ~/ alpha;
      target.pixels![i + 2] = (sb * sa + db * da * invSa ~/ 255) ~/ alpha;
      target.pixels![i + 3] = alpha;
    }
  }

  // --- Tool & Brush ---
  void setTool(DrawingTool tool) {
    _currentTool = tool;
    if (tool == DrawingTool.eraser) {
      _currentBrush = _currentBrush.copyWith(brushType: 'eraser');
    }
    notifyListeners();
  }

  void setBrush(VenBrush brush) {
    _currentBrush = brush;
    notifyListeners();
  }

  void updateBrush({
    double? size,
    double? opacity,
    double? hardness,
    Color? color,
    String? brushType,
    double? spacing,
    double? scatter,
    List<double>? pressureGraph,
  }) {
    _currentBrush = _currentBrush.copyWith(
      size: size,
      opacity: opacity,
      hardness: hardness,
      color: color,
      brushType: brushType,
      spacing: spacing,
      scatter: scatter,
      pressureGraph: pressureGraph,
    );
    notifyListeners();
  }

  // --- Color ---
  void setPrimaryColor(Color color) {
    _primaryColor = color;
    _currentBrush = _currentBrush.copyWith(color: color);
    notifyListeners();
  }

  void setSecondaryColor(Color color) {
    _secondaryColor = color;
    notifyListeners();
  }

  void swapColors() {
    final temp = _primaryColor;
    _primaryColor = _secondaryColor;
    _secondaryColor = temp;
    _currentBrush = _currentBrush.copyWith(color: _primaryColor);
    notifyListeners();
  }

  // --- Zoom & Pan ---
  void setZoom(double zoom) {
    _zoom = zoom.clamp(0.1, 20.0);
    notifyListeners();
  }

  void setPan(double x, double y) {
    _panX = x;
    _panY = y;
    notifyListeners();
  }

  void resetView() {
    _zoom = 1.0;
    _panX = 0.0;
    _panY = 0.0;
    notifyListeners();
  }

  // --- Selection ---
  void setSelection(Rect? rect) {
    _selectionRect = rect;
    _selectionActive = rect != null;
    notifyListeners();
  }

  void clearSelection() {
    _selectionRect = null;
    _selectionActive = false;
    notifyListeners();
  }

  // --- Project ---
  void setProjectName(String name) {
    _projectName = name;
    notifyListeners();
  }

  void setProjectFilePath(String path) {
    _projectFilePath = path;
    notifyListeners();
  }

  void setCanvasSize(int width, int height) {
    canvasWidth = width;
    canvasHeight = height;
    notifyListeners();
  }

  // --- Presets ---
  void addBrushPreset(VenBrush brush) {
    _brushPresets.add(brush.copyWith(id: 'preset_${DateTime.now().millisecondsSinceEpoch}'));
    notifyListeners();
  }

  void removeBrushPreset(int index) {
    if (index >= 0 && index < _brushPresets.length) {
      _brushPresets.removeAt(index);
      notifyListeners();
    }
  }

  // --- Undo/Redo ---
  void saveSnapshot() {
    final snapshot = _createSnapshot();
    _undoStack.add(snapshot);
    if (_undoStack.length > maxUndoSteps) {
      _undoStack.removeAt(0);
    }
    _redoStack.clear();
  }

  void undo() {
    if (_undoStack.length <= 1) return;
    final current = _undoStack.removeLast();
    _redoStack.add(current);
    final previous = _undoStack.last;
    _restoreSnapshot(previous);
    notifyListeners();
  }

  void redo() {
    if (_redoStack.isEmpty) return;
    final snapshot = _redoStack.removeLast();
    _undoStack.add(snapshot);
    _restoreSnapshot(snapshot);
    notifyListeners();
  }

  bool get canUndo => _undoStack.length > 1;
  bool get canRedo => _redoStack.isNotEmpty;

  CanvasSnapshot _createSnapshot() {
    return CanvasSnapshot(
      layerData: _layers.map((layer) {
        return LayerSnapshot(
          id: layer.id,
          name: layer.name,
          pixels: layer.pixels != null ? Uint8List.fromList(layer.pixels!) : null,
          width: layer.width,
          height: layer.height,
          opacity: layer.opacity,
          visible: layer.visible,
          blendModeIndex: layer.blendMode.index,
          locked: layer.locked,
          isBackground: layer.isBackground,
          offsetX: layer.offsetX,
          offsetY: layer.offsetY,
        );
      }).toList(),
      activeLayerIndex: _activeLayerIndex,
    );
  }

  void _restoreSnapshot(CanvasSnapshot snapshot) {
    _layers.clear();
    for (final layerSnap in snapshot.layerData) {
      _layers.add(ArtLayer(
        id: layerSnap.id,
        name: layerSnap.name,
        pixels: layerSnap.pixels != null ? Uint8List.fromList(layerSnap.pixels!) : null,
        width: layerSnap.width,
        height: layerSnap.height,
        opacity: layerSnap.opacity,
        visible: layerSnap.visible,
        blendMode: BlendMode.values[layerSnap.blendModeIndex],
        locked: layerSnap.locked,
        isBackground: layerSnap.isBackground,
        offsetX: layerSnap.offsetX,
        offsetY: layerSnap.offsetY,
      ));
    }
    _activeLayerIndex = snapshot.activeLayerIndex.clamp(0, _layers.length - 1);
  }

  /// Creates a new blank canvas project.
  void newProject({int width = 1080, int height = 1920, String name = 'Untitled'}) {
    _layers.clear();
    _undoStack.clear();
    _redoStack.clear();
    canvasWidth = width;
    canvasHeight = height;
    _projectName = name;
    _projectFilePath = null;
    _initializeDefaults();
  }
}

/// Immutable snapshot of canvas state for undo/redo.
class CanvasSnapshot {
  final List<LayerSnapshot> layerData;
  final int activeLayerIndex;

  const CanvasSnapshot({
    required this.layerData,
    required this.activeLayerIndex,
  });
}

/// Immutable snapshot of a single layer.
class LayerSnapshot {
  final String? id;
  final String name;
  final Uint8List? pixels;
  final int width;
  final int height;
  final double opacity;
  final bool visible;
  final int blendModeIndex;
  final bool locked;
  final bool isBackground;
  final double offsetX;
  final double offsetY;

  const LayerSnapshot({
    this.id,
    required this.name,
    this.pixels,
    required this.width,
    required this.height,
    required this.opacity,
    required this.visible,
    required this.blendModeIndex,
    required this.locked,
    required this.isBackground,
    required this.offsetX,
    required this.offsetY,
  });
}

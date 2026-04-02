import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/canvas_state.dart';
import '../../services/drawing_engine.dart';
import '../widgets/brush_settings_panel.dart';
import '../widgets/layer_panel.dart';
import '../widgets/color_picker_widget.dart';
import '../widgets/brush_qr_dialog.dart';

/// Main drawing canvas screen.
///
/// Provides a full-screen drawing experience with:
/// - Top toolbar: undo, redo, brush size, opacity, zoom controls
/// - Bottom toolbar: tool selection (pen, eraser, fill, select, move, eyedropper)
/// - Side panel: layers, brush settings, color picker
/// - Floating brush QR button for generating/sharing brush QR codes
class CanvasScreen extends StatefulWidget {
  /// Whether to immediately open the brush QR scanner on load.
  final bool openBrushQrScanner;

  /// Whether to show the brush library on load.
  final bool showBrushLibrary;

  const CanvasScreen({
    super.key,
    this.openBrushQrScanner = false,
    this.showBrushLibrary = false,
  });

  @override
  State<CanvasScreen> createState() => _CanvasScreenState();
}

class _CanvasScreenState extends State<CanvasScreen> {
  final DrawingEngine _drawingEngine = DrawingEngine();
  bool _showLayerPanel = false;
  bool _showBrushSettings = false;
  bool _showColorPicker = false;

  // Transformation state
  double _zoom = 1.0;
  Offset _panOffset = Offset.zero;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initEngine();
      if (widget.openBrushQrScanner) {
        _showBrushQrDialog();
      }
      if (widget.showBrushLibrary) {
        setState(() => _showBrushSettings = true);
      }
    });
  }

  void _initEngine() {
    final state = context.read<CanvasState>();
    _drawingEngine.attachState(state);
  }

  @override
  void dispose() {
    _drawingEngine.detachState();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<CanvasState>(
      builder: (context, state, child) {
        return Scaffold(
          backgroundColor: const Color(0xFF2A2A2A),
          body: SafeArea(
            child: Stack(
              children: [
                // Canvas area
                Positioned.fill(
                  child: GestureDetector(
                    onPanStart: _onPanStart,
                    onPanUpdate: _onPanUpdate,
                    onPanEnd: _onPanEnd,
                    onScaleStart: _onScaleStart,
                    onScaleUpdate: _onScaleUpdate,
                    onScaleEnd: _onScaleEnd,
                    child: Container(
                      color: const Color(0xFFE8E8E8),
                      child: Center(
                        child: Transform.scale(
                          scale: _zoom,
                          child: Transform.translate(
                            offset: _panOffset,
                            child: _buildCanvasView(state),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),

                // Top toolbar
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: _buildTopToolbar(state),
                ),

                // Bottom toolbar
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: _buildBottomToolbar(state),
                ),

                // Right side panel
                if (_showLayerPanel || _showBrushSettings || _showColorPicker)
                  Positioned(
                    top: 56,
                    right: 0,
                    bottom: 56,
                    child: _buildSidePanel(state),
                  ),

                // Floating brush QR button
                Positioned(
                  bottom: 72,
                  right: 12,
                  child: _buildFloatingQrButton(),
                ),

                // Zoom indicator
                Positioned(
                  bottom: 68,
                  left: 12,
                  child: _buildZoomIndicator(),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildCanvasView(CanvasState state) {
    return Container(
      width: state.canvasWidth.toDouble(),
      height: state.canvasHeight.toDouble(),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.3),
            blurRadius: 8,
            offset: const Offset(2, 2),
          ),
        ],
      ),
      child: CustomPaint(
        painter: _CanvasPainter(),
      ),
    );
  }

  Widget _buildTopToolbar(CanvasState state) {
    return Container(
      height: 56,
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // Back button
          IconButton(
            icon: const Icon(Icons.arrow_back, color: Colors.white, size: 22),
            onPressed: () => Navigator.pop(context),
          ),

          // Project name
          Expanded(
            child: Text(
              state.projectName,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),

          // Undo
          IconButton(
            icon: Icon(
              Icons.undo,
              color: state.canUndo ? Colors.white : Colors.white38,
              size: 22,
            ),
            onPressed: state.canUndo ? () => state.undo() : null,
          ),

          // Redo
          IconButton(
            icon: Icon(
              Icons.redo,
              color: state.canRedo ? Colors.white : Colors.white38,
              size: 22,
            ),
            onPressed: state.canRedo ? () => state.redo() : null,
          ),

          // Brush size indicator
          _buildSizeControl(state),

          // Opacity control
          _buildOpacityControl(state),

          // More menu
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert, color: Colors.white, size: 22),
            onSelected: (value) => _handleMenuAction(value, state),
            itemBuilder: (context) => [
              const PopupMenuItem(value: 'new', child: Text('New Canvas')),
              const PopupMenuItem(value: 'save', child: Text('Save as IPv')),
              const PopupMenuItem(value: 'export_png', child: Text('Export as PNG')),
              const PopupMenuItem(value: 'clear', child: Text('Clear Canvas')),
              const PopupMenuItem(value: 'reset_view', child: Text('Reset View')),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSizeControl(CanvasState state) {
    return SizedBox(
      width: 80,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '${state.currentBrush.size.round()}px',
            style: const TextStyle(color: Colors.white70, fontSize: 9),
          ),
          SliderTheme(
            data: SliderThemeData(
              trackHeight: 2,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              overlayShape: const RoundSliderOverlayShape(overlayRadius: 12),
              activeTrackColor: const Color(0xFFE94560),
              inactiveTrackColor: Colors.white24,
              thumbColor: Colors.white,
            ),
            child: Slider(
              value: state.currentBrush.size,
              min: 1,
              max: 100,
              onChanged: (value) => state.updateBrush(size: value),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOpacityControl(CanvasState state) {
    return SizedBox(
      width: 60,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '${(state.currentBrush.opacity * 100).round()}%',
            style: const TextStyle(color: Colors.white70, fontSize: 9),
          ),
          SliderTheme(
            data: SliderThemeData(
              trackHeight: 2,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              overlayShape: const RoundSliderOverlayShape(overlayRadius: 12),
              activeTrackColor: const Color(0xFFE94560),
              inactiveTrackColor: Colors.white24,
              thumbColor: Colors.white,
            ),
            child: Slider(
              value: state.currentBrush.opacity,
              min: 0.01,
              max: 1.0,
              onChanged: (value) => state.updateBrush(opacity: value),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomToolbar(CanvasState state) {
    final tools = [
      _ToolItem(DrawingTool.pen, Icons.edit, 'Pen'),
      _ToolItem(DrawingTool.pencil, Icons.brush, 'Pencil'),
      _ToolItem(DrawingTool.airbrush, Icons.air, 'Airbrush'),
      _ToolItem(DrawingTool.eraser, Icons.auto_fix_normal, 'Eraser'),
      _ToolItem(DrawingTool.fill, Icons.format_color_fill, 'Fill'),
      _ToolItem(DrawingTool.eyedropper, Icons.colorize, 'Eyedropper'),
      _ToolItem(DrawingTool.move, Icons.open_with, 'Move'),
    ];

    return Container(
      height: 56,
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          // Layer panel toggle
          _ToolbarButton(
            icon: Icons.layers,
            label: 'Layers',
            isActive: _showLayerPanel,
            onTap: () {
              setState(() {
                _showLayerPanel = !_showLayerPanel;
                if (_showLayerPanel) {
                  _showBrushSettings = false;
                  _showColorPicker = false;
                }
              });
            },
          ),
          const SizedBox(width: 4),
          // Tool buttons
          ...tools.map((tool) {
            final isActive = state.currentTool == tool.tool;
            final isBrushType = tool.tool == DrawingTool.pen ||
                tool.tool == DrawingTool.pencil ||
                tool.tool == DrawingTool.airbrush;
            return _ToolbarButton(
              icon: tool.icon,
              label: tool.label,
              isActive: isActive,
              onTap: () {
                if (isBrushType) {
                  state.setBrush(
                    state.currentBrush.copyWith(brushType: tool.tool.name),
                  );
                }
                state.setTool(tool.tool);
              },
            );
          }),
        ],
      ),
    );
  }

  Widget _buildSidePanel(CanvasState state) {
    return Container(
      width: 280,
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A2E),
        border: Border(
          left: BorderSide(color: Colors.white.withOpacity(0.1)),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.3),
            blurRadius: 8,
            offset: const Offset(-2, 0),
          ),
        ],
      ),
      child: Column(
        children: [
          // Panel tabs
          Container(
            height: 40,
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(color: Colors.white.withOpacity(0.1)),
              ),
            ),
            child: Row(
              children: [
                _PanelTab(
                  label: 'Brush',
                  isActive: _showBrushSettings,
                  onTap: () => setState(() {
                    _showBrushSettings = true;
                    _showLayerPanel = false;
                    _showColorPicker = false;
                  }),
                ),
                _PanelTab(
                  label: 'Color',
                  isActive: _showColorPicker,
                  onTap: () => setState(() {
                    _showColorPicker = true;
                    _showBrushSettings = false;
                    _showLayerPanel = false;
                  }),
                ),
                _PanelTab(
                  label: 'Layers',
                  isActive: _showLayerPanel,
                  onTap: () => setState(() {
                    _showLayerPanel = true;
                    _showBrushSettings = false;
                    _showColorPicker = false;
                  }),
                ),
              ],
            ),
          ),
          // Panel content
          Expanded(
            child: _showLayerPanel
                ? LayerPanel(state: state)
                : _showBrushSettings
                    ? BrushSettingsPanel(state: state)
                    : ColorPickerWidget(state: state),
          ),
        ],
      ),
    );
  }

  Widget _buildFloatingQrButton() {
    return FloatingActionButton(
      heroTag: 'brush_qr',
      onPressed: _showBrushQrDialog,
      backgroundColor: const Color(0xFFE94560),
      mini: true,
      child: const Icon(Icons.qr_code_2, color: Colors.white, size: 20),
    );
  }

  Widget _buildZoomIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: const Color(0xCC1A1A2E),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        '${(_zoom * 100).round()}%',
        style: const TextStyle(color: Colors.white, fontSize: 11),
      ),
    );
  }

  // --- Touch event handlers ---

  void _onPanStart(DragStartDetails details) {
    final canvasPos = _screenToCanvas(details.localPosition);

    final state = context.read<CanvasState>();
    if (state.currentTool == DrawingTool.eyedropper) {
      final color = _drawingEngine.pickColor(canvasPos.dx.round(), canvasPos.dy.round());
      if (color != null) {
        state.setPrimaryColor(color);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Color picked: ${color.value.toRadixString(16).toUpperCase()}'),
            duration: const Duration(seconds: 1),
          ),
        );
      }
      return;
    }

    if (state.currentTool == DrawingTool.fill) {
      _drawingEngine.floodFill(
        canvasPos.dx.round(),
        canvasPos.dy.round(),
        state.primaryColor,
      );
      return;
    }

    _drawingEngine.beginStroke(canvasPos.dx, canvasPos.dy);
  }

  void _onPanUpdate(DragUpdateDetails details) {
    final canvasPos = _screenToCanvas(details.localPosition);

    final state = context.read<CanvasState>();
    if (state.currentTool == DrawingTool.fill ||
        state.currentTool == DrawingTool.eyedropper) {
      return;
    }

    _drawingEngine.continueStroke(canvasPos.dx, canvasPos.dy);
    setState(() {}); // Trigger repaint
  }

  void _onPanEnd(DragEndDetails details) {
    _drawingEngine.endStroke();
    setState(() {});
  }

  // --- Pinch-to-zoom handlers ---

  void _onScaleStart(ScaleStartDetails details) {
    // Nothing special needed
  }

  void _onScaleUpdate(ScaleUpdateDetails details) {
    final state = context.read<CanvasState>();
    if (state.currentTool == DrawingTool.move || details.pointerCount >= 2) {
      setState(() {
        _zoom = (_zoom * details.scale).clamp(0.1, 20.0);
        _panOffset += details.focalPointDelta;
      });
    }
  }

  void _onScaleEnd(ScaleEndDetails details) {
    // Nothing special needed
  }

  Offset _screenToCanvas(Offset screenPos) {
    final renderBox = context.findRenderObject() as RenderBox;
    final canvasCenter = Offset(
      renderBox.size.width / 2,
      renderBox.size.height / 2,
    );
    return (screenPos - canvasCenter - _panOffset) / _zoom +
        Offset(540, 960); // Half of default canvas size
  }

  void _handleMenuAction(String action, CanvasState state) {
    switch (action) {
      case 'new':
        state.newProject();
        break;
      case 'save':
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Saving as IPv file...')),
        );
        break;
      case 'export_png':
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Exporting as PNG...')),
        );
        break;
      case 'clear':
        state.newProject();
        break;
      case 'reset_view':
        setState(() {
          _zoom = 1.0;
          _panOffset = Offset.zero;
        });
        break;
    }
  }

  void _showBrushQrDialog() {
    final state = context.read<CanvasState>();
    showDialog(
      context: context,
      builder: (context) => BrushQrDialog(
        currentBrush: state.currentBrush,
        onBrushSelected: (brush) {
          state.setBrush(brush);
          Navigator.pop(context);
        },
      ),
    );
  }
}

class _ToolItem {
  final DrawingTool tool;
  final IconData icon;
  final String label;

  const _ToolItem(this.tool, this.icon, this.label);
}

class _ToolbarButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _ToolbarButton({
    required this.icon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
        decoration: BoxDecoration(
          color: isActive
              ? const Color(0xFFE94560).withOpacity(0.2)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: isActive
              ? Border.all(color: const Color(0xFFE94560), width: 1.5)
              : null,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 18, color: isActive ? const Color(0xFFE94560) : Colors.white70),
            const SizedBox(height: 2),
            Text(
              label,
              style: TextStyle(
                fontSize: 8,
                color: isActive ? const Color(0xFFE94560) : Colors.white54,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PanelTab extends StatelessWidget {
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _PanelTab({
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Container(
          alignment: Alignment.center,
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: isActive ? const Color(0xFFE94560) : Colors.transparent,
                width: 2,
              ),
            ),
          ),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
              color: isActive ? const Color(0xFFE94560) : Colors.white70,
            ),
          ),
        ),
      ),
    );
  }
}

/// Custom painter that renders the canvas layers.
class _CanvasPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    // Draw a subtle grid pattern for the canvas
    final gridPaint = Paint()
      ..color = const Color(0xFFEEEEEE)
      ..strokeWidth = 0.5;

    const gridSize = 50.0;
    for (double x = 0; x <= size.width; x += gridSize) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), gridPaint);
    }
    for (double y = 0; y <= size.height; y += gridSize) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }

    // Draw checkerboard pattern for transparency (subtle)
    final checkPaint = Paint();
    const checkSize = 10.0;
    for (double y = 0; y < size.height; y += checkSize) {
      for (double x = 0; x < size.width; x += checkSize) {
        final isEven = ((x / checkSize).floor() + (y / checkSize).floor()) % 2 == 0;
        checkPaint.color = isEven
            ? const Color(0xFFF8F8F8)
            : const Color(0xFFF0F0F0);
        canvas.drawRect(
          Rect.fromLTWH(x, y, checkSize, checkSize),
          checkPaint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

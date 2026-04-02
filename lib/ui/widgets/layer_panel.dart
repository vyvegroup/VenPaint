import 'package:flutter/material.dart';
import '../../models/canvas_state.dart';
import '../../models/layer.dart';

/// Layer management panel.
///
/// Provides controls for:
/// - Adding, removing, duplicating layers
/// - Reordering layers via drag
/// - Layer visibility toggling
/// - Layer opacity control
/// - Blend mode selection
/// - Layer rename dialog
class LayerPanel extends StatefulWidget {
  final CanvasState state;

  const LayerPanel({super.key, required this.state});

  @override
  State<LayerPanel> createState() => _LayerPanelState();
}

class _LayerPanelState extends State<LayerPanel> {
  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final layers = state.layers;

    return Column(
      children: [
        // Header with add button
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '${layers.length} Layers',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _SmallIconButton(
                    icon: Icons.add,
                    tooltip: 'Add Layer',
                    onTap: () => state.addLayer(),
                  ),
                  _SmallIconButton(
                    icon: Icons.content_copy,
                    tooltip: 'Duplicate Layer',
                    onTap: () => state.duplicateLayer(state.activeLayerIndex),
                  ),
                  _SmallIconButton(
                    icon: Icons.merge_type,
                    tooltip: 'Merge Down',
                    onTap: () => state.mergeDown(state.activeLayerIndex),
                  ),
                  _SmallIconButton(
                    icon: Icons.delete_outline,
                    tooltip: 'Delete Layer',
                    onTap: () => state.removeLayer(state.activeLayerIndex),
                  ),
                ],
              ),
            ],
          ),
        ),

        const Divider(height: 1, color: Colors.white12),

        // Layer list (reversed: top layer first)
        Expanded(
          child: ReorderableListView.builder(
            reverse: true,
            padding: const EdgeInsets.symmetric(vertical: 4),
            buildDefaultDragHandles: false,
            itemCount: layers.length,
            onReorder: (oldIndex, newIndex) {
              // ReorderableListView with reverse=true needs index adjustment
              final adjustedOld = layers.length - 1 - oldIndex;
              var adjustedNew = layers.length - 1 - newIndex;
              if (oldIndex < newIndex) {
                adjustedNew--;
              }
              state.moveLayer(adjustedOld, adjustedNew);
            },
            itemBuilder: (context, index) {
              final reversedIndex = layers.length - 1 - index;
              final layer = layers[reversedIndex];
              final isActive = state.activeLayerIndex == reversedIndex;

              return _LayerItem(
                key: ValueKey(layer.id.isEmpty ? index : layer.id),
                layer: layer,
                index: reversedIndex,
                isActive: isActive,
                state: state,
                onTap: () => state.setActiveLayer(reversedIndex),
                onVisibilityToggle: () =>
                    state.setLayerVisibility(reversedIndex, !layer.visible),
                onRename: () => _showRenameDialog(context, reversedIndex, layer),
                onOpacityChanged: (value) =>
                    state.setLayerOpacity(reversedIndex, value),
              );
            },
          ),
        ),

        // Active layer opacity control
        Container(
          padding: const EdgeInsets.all(12),
          decoration: const BoxDecoration(
            border: Border(
              top: BorderSide(color: Colors.white12),
            ),
          ),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Layer Opacity',
                    style: TextStyle(color: Colors.white70, fontSize: 11),
                  ),
                  Text(
                    '${(state.activeLayer.opacity * 100).round()}%',
                    style: const TextStyle(color: Colors.white, fontSize: 11),
                  ),
                ],
              ),
              SliderTheme(
                data: SliderThemeData(
                  trackHeight: 3,
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                  activeTrackColor: const Color(0xFFE94560),
                  inactiveTrackColor: Colors.white24,
                  thumbColor: Colors.white,
                ),
                child: Slider(
                  value: state.activeLayer.opacity,
                  min: 0.0,
                  max: 1.0,
                  onChanged: (value) =>
                      state.setLayerOpacity(state.activeLayerIndex, value),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _showRenameDialog(BuildContext context, int index, ArtLayer layer) {
    final controller = TextEditingController(text: layer.name);
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename Layer'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Layer name',
            border: OutlineInputBorder(),
          ),
          onSubmitted: (name) {
            widget.state.renameLayer(index, name);
            Navigator.pop(context);
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              widget.state.renameLayer(index, controller.text);
              Navigator.pop(context);
            },
            child: const Text('Rename'),
          ),
        ],
      ),
    );
  }
}

class _LayerItem extends StatelessWidget {
  final ArtLayer layer;
  final int index;
  final bool isActive;
  final CanvasState state;
  final VoidCallback onTap;
  final VoidCallback onVisibilityToggle;
  final VoidCallback onRename;
  final ValueChanged<double> onOpacityChanged;

  const _LayerItem({
    required super.key,
    required this.layer,
    required this.index,
    required this.isActive,
    required this.state,
    required this.onTap,
    required this.onVisibilityToggle,
    required this.onRename,
    required this.onOpacityChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ReorderableDragStartListener(
      index: index,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          height: 52,
          margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(
            color: isActive
                ? const Color(0xFFE94560).withOpacity(0.15)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
            border: isActive
                ? Border.all(color: const Color(0xFFE94560), width: 1.5)
                : null,
          ),
          child: Row(
            children: [
              // Drag handle
              Padding(
                padding: const EdgeInsets.only(left: 4, right: 2),
                child: Icon(
                  Icons.drag_handle,
                  size: 16,
                  color: Colors.white38,
                ),
              ),

              // Layer thumbnail
              Container(
                width: 36,
                height: 36,
                margin: const EdgeInsets.symmetric(horizontal: 6),
                decoration: BoxDecoration(
                  color: Colors.white12,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: Colors.white24),
                ),
                child: CustomPaint(
                  painter: _LayerThumbnailPainter(layer),
                ),
              ),

              // Layer info
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      layer.name,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight:
                            isActive ? FontWeight.bold : FontWeight.normal,
                        color: isActive
                            ? const Color(0xFFE94560)
                            : Colors.white,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      '${layer.width}×${layer.height} · '
                      '${(layer.opacity * 100).round()}%',
                      style: const TextStyle(
                        fontSize: 9,
                        color: Colors.white54,
                      ),
                    ),
                  ],
                ),
              ),

              // Visibility toggle
              IconButton(
                icon: Icon(
                  layer.visible
                      ? Icons.visibility
                      : Icons.visibility_off,
                  size: 18,
                  color: layer.visible ? Colors.white70 : Colors.white30,
                ),
                onPressed: onVisibilityToggle,
                padding: const EdgeInsets.all(4),
                constraints: const BoxConstraints(minWidth: 28),
              ),

              // More options
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert, size: 16, color: Colors.white54),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 24),
                onSelected: (value) {
                  switch (value) {
                    case 'rename':
                      onRename();
                      break;
                    case 'duplicate':
                      state.duplicateLayer(index);
                      break;
                    case 'delete':
                      state.removeLayer(index);
                      break;
                    case 'clear':
                      layer.clear();
                      state.refresh();
                      break;
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(value: 'rename', child: Text('Rename')),
                  const PopupMenuItem(value: 'duplicate', child: Text('Duplicate')),
                  const PopupMenuItem(value: 'clear', child: Text('Clear Layer')),
                  const PopupMenuItem(value: 'delete', child: Text('Delete')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SmallIconButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  const _SmallIconButton({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(6),
        child: Padding(
          padding: const EdgeInsets.all(4),
          child: Icon(icon, size: 18, color: Colors.white70),
        ),
      ),
    );
  }
}

class _LayerThumbnailPainter extends CustomPainter {
  final ArtLayer layer;

  _LayerThumbnailPainter(this.layer);

  @override
  void paint(Canvas canvas, Size size) {
    // Draw checkerboard background for transparency indication
    final checkerPaint = Paint();
    const checkSize = 6.0;
    for (double y = 0; y < size.height; y += checkSize) {
      for (double x = 0; x < size.width; x += checkSize) {
        final isEven = ((x / checkSize).floor() + (y / checkSize).floor()) % 2 == 0;
        checkerPaint.color = isEven
            ? const Color(0xFF444444)
            : const Color(0xFF333333);
        canvas.drawRect(
          Rect.fromLTWH(x, y, checkSize, checkSize),
          checkerPaint,
        );
      }
    }

    // If layer has a background, show white fill
    if (layer.isBackground) {
      canvas.drawRect(
        Rect.fromLTWH(0, 0, size.width, size.height),
        Paint()..color = Colors.white,
      );
    }

    // If layer has pixels, sample and draw representative colors
    if (layer.pixels != null && layer.hasContent()) {
      final scaleX = layer.width / size.width;
      final scaleY = layer.height / size.height;
      final samplePaint = Paint();

      for (double sy = 0; sy < size.height; sy += 2) {
        for (double sx = 0; sx < size.width; sx += 2) {
          final px = (sx * scaleX).round().clamp(0, layer.width - 1);
          final py = (sy * scaleY).round().clamp(0, layer.height - 1);
          final color = layer.getPixelAt(px, py);
          if (color != null && color.alpha > 0) {
            samplePaint.color = color;
            canvas.drawRect(
              Rect.fromLTWH(sx, sy, 2, 2),
              samplePaint,
            );
          }
        }
      }
    }
  }

  @override
  bool shouldRepaint(_LayerThumbnailPainter oldDelegate) {
    return layer != oldDelegate.layer;
  }
}

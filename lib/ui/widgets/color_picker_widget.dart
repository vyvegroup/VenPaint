import 'package:flutter/material.dart';
import 'package:flutter_colorpicker/flutter_colorpicker.dart';
import '../../models/canvas_state.dart';

/// Color selection widget with multiple picker modes.
///
/// Provides:
/// - HSV color wheel picker
/// - RGB sliders
/// - Recent colors palette
/// - Color swatches
/// - Primary/secondary color indicator with swap button
class ColorPickerWidget extends StatefulWidget {
  final CanvasState state;

  const ColorPickerWidget({super.key, required this.state});

  @override
  State<ColorPickerWidget> createState() => _ColorPickerWidgetState();
}

class _ColorPickerWidgetState extends State<ColorPickerWidget> {
  Color _currentColor = Colors.black;
  final List<Color> _recentColors = [];
  static const int maxRecentColors = 16;

  @override
  void initState() {
    super.initState();
    _currentColor = widget.state.primaryColor;
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Primary/Secondary color indicator
        _buildColorIndicator(),
        const SizedBox(height: 12),
        const Divider(height: 1, color: Colors.white12),

        // Color wheel picker
        Padding(
          padding: const EdgeInsets.all(12),
          child: SizedBox(
            height: 200,
            child: ColorPicker(
              pickerColor: _currentColor,
              onColorChanged: _onColorChanged,
              displayThumbColor: true,
              portraitOnly: true,
              pickerAreaHeightPercent: 0.8,
              enableAlpha: true,
              labelTypes: const [],
            ),
          ),
        ),

        const Divider(height: 1, color: Colors.white12),

        // Recent colors
        Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Recent Colors',
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              if (_recentColors.isEmpty)
                const Text(
                  'No recent colors yet',
                  style: TextStyle(color: Colors.white38, fontSize: 11),
                )
              else
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: _recentColors.map((color) {
                    return GestureDetector(
                      onTap: () => _selectColor(color),
                      child: Container(
                        width: 24,
                        height: 24,
                        decoration: BoxDecoration(
                          color: color,
                          borderRadius: BorderRadius.circular(4),
                          border: Border.all(color: Colors.white24),
                        ),
                      ),
                    );
                  }).toList(),
                ),
            ],
          ),
        ),

        const Divider(height: 1, color: Colors.white12),

        // Preset swatches
        Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Color Swatches',
                style: TextStyle(
                  color: Colors.white70,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              _buildSwatchGrid(),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildColorIndicator() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // Secondary color (behind)
          GestureDetector(
            onTap: () => _selectColor(widget.state.secondaryColor),
            child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: widget.state.secondaryColor,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.white38),
              ),
            ),
          ),
          const SizedBox(width: 8),
          // Primary color (front)
          GestureDetector(
            onTap: () => _selectColor(widget.state.primaryColor),
            child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: widget.state.primaryColor,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.white, width: 2),
              ),
            ),
          ),
          const SizedBox(width: 12),
          // Swap button
          IconButton(
            icon: const Icon(Icons.swap_horiz, color: Colors.white70, size: 20),
            onPressed: () => widget.state.swapColors(),
            tooltip: 'Swap Colors',
            padding: const EdgeInsets.all(4),
          ),
          const SizedBox(width: 8),
          // Reset to black/white
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70, size: 20),
            onPressed: () {
              widget.state.setPrimaryColor(const Color(0xFF000000));
              widget.state.setSecondaryColor(const Color(0xFFFFFFFF));
              setState(() => _currentColor = Colors.black);
            },
            tooltip: 'Reset Colors',
            padding: const EdgeInsets.all(4),
          ),
        ],
      ),
    );
  }

  Widget _buildSwatchGrid() {
    final swatches = [
      // Grayscale
      const Color(0xFF000000), const Color(0xFF333333), const Color(0xFF666666),
      const Color(0xFF999999), const Color(0xFFCCCCCC), const Color(0xFFFFFFFF),
      // Primary colors
      const Color(0xFFFF0000), const Color(0xFF00FF00), const Color(0xFF0000FF),
      const Color(0xFFFFFF00), const Color(0xFFFF00FF), const Color(0xFF00FFFF),
      // Secondary colors
      const Color(0xFFFF8000), const Color(0xFF80FF00), const Color(0xFF0080FF),
      const Color(0xFFFF0080), const Color(0xFF8000FF), const Color(0xFF00FF80),
      // Warm tones
      const Color(0xFF8B4513), const Color(0xFFD2691E), const Color(0xFFF4A460),
      const Color(0xFFDEB887), const Color(0xFFFFDEAD), const Color(0xFFFAEBD7),
      // Cool tones
      const Color(0xFF000080), const Color(0xFF0000CD), const Color(0xFF4169E1),
      const Color(0xFF6495ED), const Color(0xFF87CEEB), const Color(0xFFE0FFFF),
    ];

    return Wrap(
      spacing: 3,
      runSpacing: 3,
      children: swatches.map((color) {
        return GestureDetector(
          onTap: () => _selectColor(color),
          child: Container(
            width: 20,
            height: 20,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(3),
              border: Border.all(
                color: Colors.white.withOpacity(0.2),
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  void _onColorChanged(Color color) {
    setState(() => _currentColor = color);
  }

  void _selectColor(Color color) {
    setState(() => _currentColor = color);
    widget.state.setPrimaryColor(color);
    _addRecentColor(color);
  }

  void _addRecentColor(Color color) {
    // Remove duplicate if exists
    _recentColors.remove(color);
    // Add to beginning
    _recentColors.insert(0, color);
    // Trim to max
    while (_recentColors.length > maxRecentColors) {
      _recentColors.removeLast();
    }
    setState(() {});
  }
}

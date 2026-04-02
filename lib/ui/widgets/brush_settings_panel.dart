import 'package:flutter/material.dart';
import '../../models/canvas_state.dart';
import '../../models/brush.dart';

/// Brush customization panel with sliders and presets.
///
/// Provides controls for:
/// - Brush size, opacity, hardness
/// - Brush type selection
/// - Spacing and scatter controls
/// - Brush presets gallery
class BrushSettingsPanel extends StatefulWidget {
  final CanvasState state;

  const BrushSettingsPanel({super.key, required this.state});

  @override
  State<BrushSettingsPanel> createState() => _BrushSettingsPanelState();
}

class _BrushSettingsPanelState extends State<BrushSettingsPanel> {
  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        // Brush type selector
        _SectionLabel('Brush Type'),
        const SizedBox(height: 8),
        _buildBrushTypeSelector(),
        const SizedBox(height: 16),

        // Size slider
        _SectionLabel('Size'),
        const SizedBox(height: 4),
        _buildSliderRow(
          value: widget.state.currentBrush.size,
          min: 1,
          max: 100,
          label: '${widget.state.currentBrush.size.round()} px',
          onChanged: (value) => widget.state.updateBrush(size: value),
        ),
        const SizedBox(height: 12),

        // Opacity slider
        _SectionLabel('Opacity'),
        const SizedBox(height: 4),
        _buildSliderRow(
          value: widget.state.currentBrush.opacity,
          min: 0.01,
          max: 1.0,
          label: '${(widget.state.currentBrush.opacity * 100).round()}%',
          onChanged: (value) => widget.state.updateBrush(opacity: value),
        ),
        const SizedBox(height: 12),

        // Hardness slider
        _SectionLabel('Hardness'),
        const SizedBox(height: 4),
        _buildSliderRow(
          value: widget.state.currentBrush.hardness,
          min: 0,
          max: 1.0,
          label: '${(widget.state.currentBrush.hardness * 100).round()}%',
          onChanged: (value) => widget.state.updateBrush(hardness: value),
        ),
        const SizedBox(height: 12),

        // Spacing slider
        _SectionLabel('Spacing'),
        const SizedBox(height: 4),
        _buildSliderRow(
          value: widget.state.currentBrush.spacing,
          min: 0.01,
          max: 1.0,
          label: '${(widget.state.currentBrush.spacing * 100).round()}%',
          onChanged: (value) => widget.state.updateBrush(spacing: value),
        ),
        const SizedBox(height: 12),

        // Scatter slider
        _SectionLabel('Scatter'),
        const SizedBox(height: 4),
        _buildSliderRow(
          value: widget.state.currentBrush.scatter,
          min: 0,
          max: 20.0,
          label: '${widget.state.currentBrush.scatter.toStringAsFixed(1)}',
          onChanged: (value) => widget.state.updateBrush(scatter: value),
        ),
        const SizedBox(height: 20),

        // Brush presets
        _SectionLabel('Presets'),
        const SizedBox(height: 8),
        _buildPresetsGrid(),
      ],
    );
  }

  Widget _buildBrushTypeSelector() {
    final types = [
      ('pen', Icons.edit, 'Pen'),
      ('pencil', Icons.brush, 'Pencil'),
      ('airbrush', Icons.air, 'Airbrush'),
      ('watercolor', Icons.water_drop, 'Watercolor'),
      ('oil', Icons.format_paint, 'Oil'),
      ('eraser', Icons.auto_fix_normal, 'Eraser'),
    ];

    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: types.map((type) {
        final isActive = widget.state.currentBrush.brushType == type.$1;
        return Material(
          color: isActive
              ? const Color(0xFFE94560).withOpacity(0.2)
              : const Color(0xFF2A2A3E),
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            onTap: () => widget.state.updateBrush(brushType: type.$1),
            borderRadius: BorderRadius.circular(8),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    type.$2,
                    size: 14,
                    color: isActive ? const Color(0xFFE94560) : Colors.white70,
                  ),
                  const SizedBox(width: 4),
                  Text(
                    type.$3,
                    style: TextStyle(
                      fontSize: 11,
                      color: isActive ? const Color(0xFFE94560) : Colors.white70,
                      fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildSliderRow({
    required double value,
    required double min,
    required double max,
    required String label,
    required ValueChanged<double> onChanged,
  }) {
    return Row(
      children: [
        Expanded(
          child: SliderTheme(
            data: SliderThemeData(
              trackHeight: 3,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              activeTrackColor: const Color(0xFFE94560),
              inactiveTrackColor: Colors.white24,
              thumbColor: Colors.white,
            ),
            child: Slider(
              value: value,
              min: min,
              max: max,
              onChanged: onChanged,
            ),
          ),
        ),
        SizedBox(
          width: 48,
          child: Text(
            label,
            style: const TextStyle(color: Colors.white70, fontSize: 11),
            textAlign: TextAlign.right,
          ),
        ),
      ],
    );
  }

  Widget _buildPresetsGrid() {
    final presets = widget.state.brushPresets;

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
        childAspectRatio: 2.5,
      ),
      itemCount: presets.length,
      itemBuilder: (context, index) {
        final preset = presets[index];
        final isActive = widget.state.currentBrush.id == preset.id;
        return Material(
          color: isActive
              ? const Color(0xFFE94560).withOpacity(0.15)
              : const Color(0xFF2A2A3E),
          borderRadius: BorderRadius.circular(8),
          child: InkWell(
            onTap: () => widget.state.setBrush(preset.copyWith()),
            borderRadius: BorderRadius.circular(8),
            child: Container(
              padding: const EdgeInsets.all(8),
              decoration: isActive
                  ? BoxDecoration(
                      border: Border.all(color: const Color(0xFFE94560), width: 1.5),
                      borderRadius: BorderRadius.circular(8),
                    )
                  : null,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    preset.name,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: isActive ? const Color(0xFFE94560) : Colors.white,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${preset.brushType} · ${preset.size.round()}px',
                    style: TextStyle(
                      fontSize: 9,
                      color: Colors.white54,
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String label;
  const _SectionLabel(this.label);

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: const TextStyle(
        color: Colors.white70,
        fontSize: 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
    );
  }
}

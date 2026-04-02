import 'dart:async';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../models/brush.dart';
import '../../services/brush_qr_service.dart';

/// Dialog for scanning and creating brush QR codes.
///
/// Provides two modes:
/// 1. **Scan Mode**: Uses the camera to scan QR codes containing brush data
/// 2. **Create Mode**: Generates a QR code from the current brush settings
class BrushQrDialog extends StatefulWidget {
  final VenBrush currentBrush;
  final ValueChanged<VenBrush> onBrushSelected;

  const BrushQrDialog({
    super.key,
    required this.currentBrush,
    required this.onBrushSelected,
  });

  @override
  State<BrushQrDialog> createState() => _BrushQrDialogState();
}

class _BrushQrDialogState extends State<BrushQrDialog>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  VenBrush? _scannedBrush;
  String? _scanStatus;
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      child: Container(
        width: double.maxFinite,
        constraints: const BoxConstraints(maxHeight: 560),
        decoration: BoxDecoration(
          color: const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(16),
              decoration: const BoxDecoration(
                border: Border(
                  bottom: BorderSide(color: Colors.white12),
                ),
              ),
              child: Row(
                children: [
                  const Icon(Icons.qr_code_2, color: Color(0xFFE94560), size: 24),
                  const SizedBox(width: 12),
                  const Text(
                    'Brush QR Code',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white54),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),

            // Tabs
            TabBar(
              controller: _tabController,
              labelColor: const Color(0xFFE94560),
              unselectedLabelColor: Colors.white54,
              indicatorColor: const Color(0xFFE94560),
              tabs: const [
                Tab(text: 'Create QR'),
                Tab(text: 'Scan QR'),
              ],
            ),

            // Tab content
            Flexible(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _buildCreateTab(),
                  _buildScanTab(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCreateTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          // QR code display
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            child: BrushQrService.generateBrushQR(widget.currentBrush, size: 240),
          ),
          const SizedBox(height: 16),

          // Brush info
          Text(
            widget.currentBrush.name,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '${widget.currentBrush.brushType} · '
            '${widget.currentBrush.size.round()}px · '
            '${(widget.currentBrush.opacity * 100).round()}%',
            style: const TextStyle(color: Colors.white54, fontSize: 13),
          ),
          const SizedBox(height: 16),

          // Compatibility note
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF533483).withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFF533483).withValues(alpha: 0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.info_outline, color: Color(0xFF533483), size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'This QR code is compatible with ibisPaint. '
                    'Scan it with ibisPaint to import the brush.',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildScanTab() {
    return Column(
      children: [
        if (_isScanning)
          Expanded(
            child: Stack(
              children: [
                // Camera scanner
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: MobileScanner(
                    onDetect: _onQRDetected,
                  ),
                ),
                // Scan overlay
                Center(
                  child: Container(
                    width: 240,
                    height: 240,
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: const Color(0xFFE94560),
                        width: 2,
                      ),
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
                // Scan status
                if (_scanStatus != null)
                  Positioned(
                    bottom: 16,
                    left: 16,
                    right: 16,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 10,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _scanStatus!,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
          )
        else if (_scannedBrush != null)
          // Show scanned brush details
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  // Success icon
                  const Icon(
                    Icons.check_circle,
                    color: Color(0xFF4CAF50),
                    size: 48,
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Brush Scanned!',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Brush details card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: const Color(0xFF2A2A3E),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _scannedBrush!.name,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 12),
                        _DetailRow(
                          label: 'Type',
                          value: _scannedBrush!.brushType,
                        ),
                        _DetailRow(
                          label: 'Size',
                          value: '${_scannedBrush!.size.round()}px',
                        ),
                        _DetailRow(
                          label: 'Opacity',
                          value:
                              '${(_scannedBrush!.opacity * 100).round()}%',
                        ),
                        _DetailRow(
                          label: 'Hardness',
                          value:
                              '${(_scannedBrush!.hardness * 100).round()}%',
                        ),
                        _DetailRow(
                          label: 'Spacing',
                          value:
                              '${(_scannedBrush!.spacing * 100).round()}%',
                        ),
                        _DetailRow(
                          label: 'Scatter',
                          value: _scannedBrush!.scatter
                              .toStringAsFixed(1),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            const Text(
                              'Color: ',
                              style: TextStyle(
                                  color: Colors.white54, fontSize: 12),
                            ),
                            Container(
                              width: 24,
                              height: 24,
                              decoration: BoxDecoration(
                                color: _scannedBrush!.color,
                                borderRadius: BorderRadius.circular(4),
                                border: Border.all(color: Colors.white24),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              '#${_scannedBrush!.color.toARGB32().toRadixString(16).substring(2).toUpperCase()}',
                              style: const TextStyle(
                                color: Colors.white70,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Action buttons
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () {
                            setState(() {
                              _scannedBrush = null;
                              _scanStatus = null;
                            });
                          },
                          style: OutlinedButton.styleFrom(
                            foregroundColor: Colors.white54,
                            side: const BorderSide(color: Colors.white24),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                          child: const Text('Scan Again'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: () {
                            if (_scannedBrush != null) {
                              widget.onBrushSelected(_scannedBrush!);
                            }
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFE94560),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                          child: const Text('Use Brush'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          )
        else
          // Initial scan prompt
          Expanded(
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.qr_code_scanner,
                    size: 64,
                    color: Colors.white.withValues(alpha: 0.3),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Ready to Scan',
                    style: TextStyle(
                      color: Colors.white70,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Tap the button below to open\nthe camera scanner',
                    style: TextStyle(
                      color: Colors.white38,
                      fontSize: 13,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: () {
                      setState(() {
                        _isScanning = true;
                        _scanStatus = 'Point camera at a brush QR code';
                      });
                    },
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('Start Scanning'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFE94560),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 14,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }

  void _onQRDetected(BarcodeCapture capture) {
    for (final barcode in capture.barcodes) {
      final data = barcode.rawValue;
      if (data == null || data.isEmpty) continue;

      final brush = BrushQrService.scanBrushQR(data);
      if (brush != null) {
        setState(() {
          _scannedBrush = brush;
          _isScanning = false;
          _scanStatus = null;
        });
        return;
      }

      // Not a brush QR code
      if (mounted) {
        setState(() {
          _scanStatus = 'Not a brush QR code. Try another one.';
        });
      }
    }
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;

  const _DetailRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        children: [
          SizedBox(
            width: 70,
            child: Text(
              label,
              style: const TextStyle(color: Colors.white54, fontSize: 12),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}

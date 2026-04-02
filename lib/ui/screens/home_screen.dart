import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/canvas_state.dart';
import 'canvas_screen.dart';

/// Home screen showing the project gallery and create/import options.
///
/// Users can start a new canvas, import an existing project,
/// or open recent projects from this screen.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      appBar: AppBar(
        title: const Text(
          'VenPaint',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1A1A2E),
          ),
        ),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.settings, color: Color(0xFF1A1A2E)),
            onPressed: () {
              _showSettingsDialog(context);
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),
            // New Canvas button
            _buildNewCanvasCard(context),
            const SizedBox(height: 20),
            // Quick actions row
            _buildQuickActions(context),
            const SizedBox(height: 24),
            // Recent projects section
            const Text(
              'Start Creating',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Color(0xFF1A1A2E),
              ),
            ),
            const SizedBox(height: 12),
            _buildTemplateGrid(context),
            const SizedBox(height: 24),
            // Import section
            const Text(
              'Import Project',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Color(0xFF1A1A2E),
              ),
            ),
            const SizedBox(height: 12),
            _buildImportOptions(context),
            const SizedBox(height: 24),
            // About section
            _buildAboutCard(context),
          ],
        ),
      ),
    );
  }

  Widget _buildNewCanvasCard(BuildContext context) {
    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF1A1A2E), Color(0xFF16213E)],
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(
              Icons.brush,
              size: 40,
              color: Color(0xFFE94560),
            ),
            const SizedBox(height: 12),
            const Text(
              'Create New Canvas',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Start a new artwork with your preferred canvas size',
              style: TextStyle(
                fontSize: 14,
                color: Colors.white70,
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () => _showNewCanvasDialog(context),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFE94560),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
                child: const Text(
                  'New Canvas',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQuickActions(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _QuickActionButton(
            icon: Icons.qr_code_scanner,
            label: 'Scan Brush QR',
            color: const Color(0xFF533483),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => ChangeNotifierProvider(
                    create: (_) => CanvasState(),
                    child: const CanvasScreen(openBrushQrScanner: true),
                  ),
                ),
              );
            },
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _QuickActionButton(
            icon: Icons.folder_open,
            label: 'Open File',
            color: const Color(0xFF0F3460),
            onTap: () {
              _showImportDialog(context);
            },
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _QuickActionButton(
            icon: Icons.info_outline,
            label: 'Brush Library',
            color: const Color(0xFF536878),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => ChangeNotifierProvider(
                    create: (_) => CanvasState(),
                    child: const CanvasScreen(showBrushLibrary: true),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildTemplateGrid(BuildContext context) {
    final templates = [
      _CanvasTemplate('Phone (9:16)', 1080, 1920, Icons.phone_android),
      _CanvasTemplate('Square (1:1)', 1080, 1080, Icons.crop_square),
      _CanvasTemplate('HD (16:9)', 1920, 1080, Icons.desktop_windows),
      _CanvasTemplate('A4 Portrait', 2480, 3508, Icons.description),
      _CanvasTemplate('Twitter Banner', 1500, 500, Icons.view_sidebar),
      _CanvasTemplate('Instagram Post', 1080, 1080, Icons.camera_alt),
    ];

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 12,
        mainAxisSpacing: 12,
        childAspectRatio: 1.4,
      ),
      itemCount: templates.length,
      itemBuilder: (context, index) {
        final template = templates[index];
        return _buildTemplateCard(context, template);
      },
    );
  }

  Widget _buildTemplateCard(BuildContext context, _CanvasTemplate template) {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        onTap: () => _openCanvasWithSize(
          context,
          template.width,
          template.height,
          template.name,
        ),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(template.icon, size: 32, color: const Color(0xFF1A1A2E)),
              const SizedBox(height: 8),
              Text(
                template.name,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF1A1A2E),
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 4),
              Text(
                '${template.width}×${template.height}',
                style: TextStyle(
                  fontSize: 11,
                  color: Colors.grey[600],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildImportOptions(BuildContext context) {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            ListTile(
              leading: const Icon(Icons.file_download, color: Color(0xFFE94560)),
              title: const Text('Import IPv Project'),
              subtitle: const Text('Open .ipv files from ibisPaint or VenPaint'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _showImportDialog(context),
            ),
            const Divider(height: 1),
            ListTile(
              leading: const Icon(Icons.image, color: Color(0xFF533483)),
              title: const Text('Import Image'),
              subtitle: const Text('Open PNG, JPG, or WEBP as a new layer'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Image import coming soon')),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAboutCard(BuildContext context) {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: Colors.white,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: const BoxDecoration(
                color: Color(0xFF1A1A2E),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.brush,
                color: Color(0xFFE94560),
                size: 24,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'VenPaint v1.0.0',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1A1A2E),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Professional drawing app with brush QR sharing.\n'
                    'Compatible with ibisPaint brush formats.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey[600],
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showNewCanvasDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => const _NewCanvasDialog(),
    );
  }

  void _showImportDialog(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('File picker will open to select .ipv file'),
        duration: Duration(seconds: 2),
      ),
    );
    // In production, this would use file_picker to select an .ipv file
  }

  void _showSettingsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Settings'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.palette),
              title: const Text('Default Canvas Size'),
              subtitle: const Text('1080 × 1920'),
              onTap: () => Navigator.pop(context),
            ),
            ListTile(
              leading: const Icon(Icons.auto_fix_high),
              title: const Text('Auto-save'),
              subtitle: const Text('Enabled'),
              onTap: () => Navigator.pop(context),
            ),
            ListTile(
              leading: const Icon(Icons.info),
              title: const Text('About'),
              subtitle: const Text('VenPaint v1.0.0'),
              onTap: () => Navigator.pop(context),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  void _openCanvasWithSize(
      BuildContext context, int width, int height, String name) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => ChangeNotifierProvider(
          create: (_) => CanvasState()..setCanvasSize(width, height)..setProjectName(name),
          child: const CanvasScreen(),
        ),
      ),
    );
  }
}

class _CanvasTemplate {
  final String name;
  final int width;
  final int height;
  final IconData icon;

  const _CanvasTemplate(this.name, this.width, this.height, this.icon);
}

class _QuickActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _QuickActionButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
          child: Column(
            children: [
              Icon(icon, color: Colors.white, size: 24),
              const SizedBox(height: 8),
              Text(
                label,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NewCanvasDialog extends StatefulWidget {
  const _NewCanvasDialog();

  @override
  State<_NewCanvasDialog> createState() => _NewCanvasDialogState();
}

class _NewCanvasDialogState extends State<_NewCanvasDialog> {
  final _widthController = TextEditingController(text: '1080');
  final _heightController = TextEditingController(text: '1920');
  String _selectedPreset = 'Phone (9:16)';

  final _presets = {
    'Phone (9:16)': [1080, 1920],
    'Square (1:1)': [1080, 1080],
    'HD (16:9)': [1920, 1080],
    'A4 Portrait': [2480, 3508],
    'Twitter Banner': [1500, 500],
    'Instagram Post': [1080, 1080],
  };

  @override
  void dispose() {
    _widthController.dispose();
    _heightController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('New Canvas'),
      content: SizedBox(
        width: 300,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Preset:', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: _selectedPreset,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                isDense: true,
              ),
              items: _presets.keys
                  .map((preset) => DropdownMenuItem(
                        value: preset,
                        child: Text(preset, style: const TextStyle(fontSize: 13)),
                      ))
                  .toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _selectedPreset = value;
                    _widthController.text = _presets[value]![0].toString();
                    _heightController.text = _presets[value]![1].toString();
                  });
                }
              },
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _widthController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Width (px)',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: IconButton(
                    icon: const Icon(Icons.swap_horiz),
                    onPressed: () {
                      final width = _widthController.text;
                      _widthController.text = _heightController.text;
                      _heightController.text = width;
                    },
                  ),
                ),
                Expanded(
                  child: TextField(
                    controller: _heightController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Height (px)',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: () {
            final width = int.tryParse(_widthController.text) ?? 1080;
            final height = int.tryParse(_heightController.text) ?? 1920;
            Navigator.pop(context);
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => ChangeNotifierProvider(
                  create: (_) => CanvasState()..setCanvasSize(width, height)..setProjectName(_selectedPreset),
                  child: const CanvasScreen(),
                ),
              ),
            );
          },
          child: const Text('Create'),
        ),
      ],
    );
  }
}

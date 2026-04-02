# VenPaint

A professional drawing app for Android with brush QR code sharing, built with Flutter.

## Features

- **Multi-layer canvas** with full compositing, blend modes, and opacity control
- **Professional brush engine** supporting pen, pencil, airbrush, watercolor, oil, and eraser
- **Pressure-sensitive drawing** with customizable pressure graphs
- **Brush QR code sharing** - share and import brushes via QR codes (ibisPaint IPBZ compatible)
- **VPBZ/IPBZ codec** - generated QR codes are scannable by ibisPaint, and VenPaint scans ibisPaint brush QR codes
- **IPv file import/export** - full project file format with layer data and metadata
- **Color picker** with HSV wheel, RGB sliders, swatches, and recent colors
- **Undo/redo** with up to 50 history states
- **Multiple canvas presets** - phone, square, HD, A4, and custom sizes
- **Gesture controls** - pinch-to-zoom, two-finger pan, and swipe gestures

## Technical Architecture

### State Management
- **Provider pattern** (`ChangeNotifier`) for reactive canvas state updates

### Drawing Engine
- Custom pixel-level `DrawingEngine` with `CustomPainter` rendering
- Supports multiple brush types: pen, pencil, airbrush, watercolor, oil, eraser
- Flood fill, eyedropper, and selection tools
- Layer compositing with alpha blending

### Brush QR Format (VPBZ)
- Binary format: `VPBZ` magic (4B) + version (2B) + flags (2B) + uncompressed length (4B) + zlib-compressed JSON
- Compatible with ibisPaint's IPBZ format
- JSON includes: name, size, opacity, hardness, color, brushType, spacing, scatter, pressureGraph

### IPv File Format
- Chunk-based binary format with 4-byte ID + 4-byte length + data + negative-length checksum
- Required chunks: AddCanvas (0x01000100), MetaInfo (0x01000600), ManageLayer (0x03000600)
- Layers stored as embedded PNG images

## Project Structure

```
lib/
├── main.dart                      # App entry point with theme and Provider setup
├── models/
│   ├── brush.dart                 # VenBrush model with VPBZ encoding
│   ├── layer.dart                 # ArtLayer model with pixel manipulation
│   └── canvas_state.dart          # CanvasState ChangeNotifier
├── services/
│   ├── drawing_engine.dart        # CustomPainter drawing engine
│   ├── ipbz_service.dart          # VPBZ/IPBZ brush codec
│   ├── ipv_service.dart           # IPv file import/export
│   └── brush_qr_service.dart      # QR code generation/scanning
└── ui/
    ├── screens/
    │   ├── home_screen.dart       # Gallery/home with templates and import
    │   └── canvas_screen.dart     # Main drawing canvas with toolbars
    └── widgets/
        ├── brush_settings_panel.dart   # Brush type/parameter controls
        ├── layer_panel.dart           # Layer management with drag reorder
        ├── color_picker_widget.dart   # Color selection with HSV wheel
        └── brush_qr_dialog.dart       # QR scan/create dialog
```

## Getting Started

### Prerequisites
- Flutter SDK >= 3.0.0
- Android SDK with API level 24+
- Kotlin plugin for Gradle

### Building
```bash
flutter pub get
flutter build apk --release
```

### Running Tests
```bash
flutter test
```

## Package: `com.venpaint.app`

## License
MIT

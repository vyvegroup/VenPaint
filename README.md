# VenPaint 🎨

A free, feature-rich Android painting app built with Kotlin. Create stunning digital art with multiple brush types, layers, blend modes, and more.

> **100% Free** • No login required • No premium features • Open source

## ✨ Features

### 🖌️ Brush System
- **8 brush types**: Pen, Pencil, Airbrush, Watercolor, Flat Brush, Round Brush, Crayon, Eraser
- Adjustable **size**, **opacity**, **hardness**, and **spacing**
- **Pressure sensitivity** support (requires compatible stylus)
- Smooth stroke rendering with **Bezier curves**
- Save and load **custom brush presets**

### 📚 Layer System
- Up to **100 layers** per project
- **Blend modes**: Normal, Multiply, Screen, Overlay
- Per-layer **opacity** control
- **Add**, **delete**, **duplicate**, **merge**, and **reorder** layers
- Toggle layer **visibility**
- Layer **thumbnails** for quick identification

### 📱 Brush QR System
- **Generate QR codes** from your custom brush settings
- **Scan QR codes** to import brushes from other VenPaint users
- **Partial ibisPaint compatibility**: Recognizes ibisPaint brush QR codes and offers to open them in the browser
- Custom format: `VP:<base64-encoded-JSON>`

### 📁 Import & Export
- **Import .ipv files** from ibisPaint (extracts layers from ZIP archives)
- **Import images** (PNG, JPG, WEBP)
- **Export as PNG** (lossless, with transparency)
- **Export as JPG** (lossy, white background)
- **Export as PSD** (Adobe Photoshop format)
- **Save/Load projects** (.vpp format - ZIP with layer data + JSON metadata)

### 🎯 Drawing Tools
- **Zoom & Pan** with pinch-to-zoom gestures
- **Double-tap** to reset view
- **Fill tool** for quick area fills
- **Undo/Redo** with up to 30 history states
- Full **immersive mode** for maximum canvas space
- **Checkerboard transparency** indicator

## 🏗️ Architecture

```
VenPaint/
├── app/src/main/java/com/venpaint/app/
│   ├── MainActivity.kt          # Main activity with fullscreen UI
│   ├── ui/                      # Custom Views & UI components
│   │   ├── DrawingCanvas.kt     # Touch handling, zoom/pan, rendering
│   │   ├── BrushSettingsPanel.kt # Sliders for brush parameters
│   │   ├── LayerPanel.kt        # Layer list with controls
│   │   ├── ColorPickerDialog.kt # HSV color picker with palette
│   │   └── ToolBar.kt           # Bottom toolbar with tool buttons
│   ├── engine/                  # Core rendering engine
│   │   ├── BrushEngine.kt       # Brush stroke rendering (Bezier, stamps)
│   │   ├── Layer.kt             # Layer data model with bitmap management
│   │   ├── LayerManager.kt      # Layer stack operations
│   │   └── DrawingHistory.kt    # Undo/redo with layer snapshots
│   ├── brush/                   # Brush data & QR system
│   │   ├── Brush.kt             # Brush parameters data class
│   │   ├── BrushType.kt         # Brush type enum
│   │   ├── BrushQRGenerator.kt  # QR code generation (ZXing)
│   │   ├── BrushQRScanner.kt    # QR code scanning (ZXing)
│   │   └── BrushManager.kt      # Brush preset save/load
│   ├── io/                      # File I/O
│   │   ├── IpvImporter.kt       # ibisPaint .ipv import
│   │   ├── ProjectExporter.kt   # PNG/JPG/PSD export
│   │   └── ProjectSaver.kt      # .vpp project save/load
│   └── util/                    # Utilities
│       ├── ColorUtils.kt        # Color conversion & manipulation
│       └── FileUtils.kt         # File operations helpers
```

## 🔧 Tech Stack

| Technology | Purpose |
|---|---|
| **Kotlin** | Primary language |
| **Android SDK 24-34** | Target platform (Android 7.0+) |
| **ZXing** | QR code generation & scanning |
| **Gson** | JSON serialization |
| **Coroutines** | Async operations |
| **Material Components** | UI components |
| **View system** | Maximum compatibility (non-Compose) |

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API level 34

### Build

```bash
# Clone the repository
git clone https://github.com/yourusername/VenPaint.git
cd VenPaint

# Build debug APK
./gradlew assembleDebug

# The APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

### Install
1. Transfer `app-debug.apk` to your Android device
2. Enable "Install from unknown sources" in Settings
3. Open the APK file to install

## 📱 Usage

1. **Draw** on the canvas using the selected brush
2. **Adjust brush** settings using the bottom panel (size, opacity, hardness)
3. **Switch tools** using the bottom toolbar
4. **Manage layers** by tapping the layers button
5. **Undo/Redo** using the toolbar buttons
6. **Save** your project or **Export** as PNG/JPG/PSD
7. **Pinch to zoom**, **drag with two fingers** to pan
8. **Double-tap** to reset the view

## 🔄 ibisPaint Compatibility

VenPaint provides partial compatibility with ibisPaint files:

- **Import .ipv files**: Opens ZIP-based .ipv files and extracts layer data
- **Scan ibisPaint QR codes**: Recognizes ibisPaint brush URLs and opens them in the browser

## 📋 Minimum Requirements

- Android 7.0 (API 24) or higher
- Camera (for QR scanning - optional)
- Storage permission (for saving/exporting - Android 9 and below)

## 📜 License

This project is open source and available under the [MIT License](LICENSE).

---

**Built with ❤️ for the Android art community**

import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import '../models/canvas_state.dart';
import '../models/layer.dart';

/// Service for importing and exporting IPV files.
///
/// IPV (ibisPaint Video/Project) chunk format:
/// - Each chunk: 4-byte ID (big-endian) + 4-byte length (big-endian) + data
/// - Optional: 4-byte negative-length checksum after data
///
/// Required chunks:
/// - 0x01000100: AddCanvas (canvas dimensions and settings)
/// - 0x01000600: MetaInfo (project metadata, author, app version)
/// - 0x03000600: ManageLayer (layer definitions with embedded PNGs)
///
/// This service provides read/write support for the IPV format
/// used by ibisPaint and compatible applications.
class IpvService {
  /// Chunk IDs used in IPV format
  static const int chunkAddCanvas = 0x01000100;
  static const int chunkMetaInfo = 0x01000600;
  static const int chunkManageLayer = 0x03000600;
  static const int chunkLayerImage = 0x03000200;
  static const int chunkEndOfLayers = 0x03000700;

  /// Magic bytes for IPV file format
  static const List<int> ipvMagic = [0x49, 0x50, 0x56, 0x00]; // "IPV\0"

  /// Current app version for export metadata
  static const String appVersion = '1.0.0';

  /// Imports an IPV file and returns the parsed project data.
  ///
  /// [filePath] should point to a valid .ipv file.
  /// Returns an [IpvProject] containing layers, canvas dimensions, and metadata.
  static Future<IpvProject> importIPv(String filePath) async {
    final file = File(filePath);
    if (!await file.exists()) {
      throw FileSystemException('File not found', filePath);
    }

    final bytes = await file.readAsBytes();
    return parseIPvBytes(bytes);
  }

  /// Parses raw IPV binary data.
  static IpvProject parseIPvBytes(Uint8List bytes) {
    // Validate magic bytes
    if (bytes.length < 4) {
      throw const FormatException('IPV file too small');
    }
    if (!const List<int>.generate(4, (i) => bytes[i]).eq(ipvMagic)) {
      // Try parsing without strict magic check for variant formats
    }

    final reader = _ByteReader(bytes);
    final chunks = <_IpvChunk>[];

    // Read all chunks
    while (reader.remainingBytes >= 8) {
      final chunkId = reader.readUint32BE();
      final chunkLength = reader.readUint32BE();

      if (chunkLength > reader.remainingBytes) {
        break; // Not enough data for this chunk
      }

      final data = reader.readBytes(chunkLength);

      // Optional negative-length checksum (4 bytes)
      if (reader.remainingBytes >= 4) {
        final checksum = reader.readInt32BE();
        if (checksum != -chunkLength) {
          // Some variants may not include checksum; continue parsing
        }
      }

      chunks.add(_IpvChunk(id: chunkId, data: data));
    }

    return _parseChunks(chunks);
  }

  /// Parses chunk data into an [IpvProject].
  static IpvProject _parseChunks(List<_IpvChunk> chunks) {
    int canvasWidth = 1080;
    int canvasHeight = 1920;
    String projectName = 'Untitled';
    String author = '';
    String appInfo = 'VenPaint $appVersion';
    final layers = <ArtLayer>[];

    for (final chunk in chunks) {
      switch (chunk.id) {
        case chunkAddCanvas:
          final reader = _ByteReader(Uint8List.fromList(chunk.data));
          canvasWidth = reader.readInt32BE();
          canvasHeight = reader.readInt32BE();
          // Additional canvas settings can be read here
          break;

        case chunkMetaInfo:
          final jsonString = utf8.decode(chunk.data);
          final meta = jsonDecode(jsonString) as Map<String, dynamic>;
          projectName = meta['projectName'] as String? ?? 'Untitled';
          author = meta['author'] as String? ?? '';
          appInfo = meta['appInfo'] as String? ?? '';
          break;

        case chunkManageLayer:
          final layer = _parseLayerChunk(chunk.data);
          if (layer != null) {
            layers.add(layer);
          }
          break;
      }
    }

    // If no layers were found, create a default white background
    if (layers.isEmpty) {
      layers.add(ArtLayer.whiteBackground(
        width: canvasWidth,
        height: canvasHeight,
      ));
    }

    return IpvProject(
      canvasWidth: canvasWidth,
      canvasHeight: canvasHeight,
      projectName: projectName,
      author: author,
      appInfo: appInfo,
      layers: layers,
    );
  }

  /// Parses a ManageLayer chunk into an [ArtLayer].
  static ArtLayer? _parseLayerChunk(List<int> data) {
    try {
      final reader = _ByteReader(Uint8List.fromList(data));

      // Read layer metadata header (32 bytes)
      final nameLength = reader.readUint16BE();
      final nameBytes = reader.readBytes(nameLength);
      final name = utf8.decode(nameBytes);
      final opacity = reader.readFloat32BE();
      final visible = reader.readUint8() != 0;
      final blendModeIndex = reader.readUint16BE();
      final locked = reader.readUint8() != 0;
      final isBackground = reader.readUint8() != 0;
      final layerWidth = reader.readInt32BE();
      final layerHeight = reader.readInt32BE();

      // Remaining bytes are the PNG image data
      final pngData = reader.readRemainingBytes();

      // Decode PNG to get pixel data
      img.Image? image;
      if (pngData.isNotEmpty) {
        image = img.decodePng(pngData);
      }

      ArtLayer layer;
      if (image != null) {
        final pixels = _imageToRgba(image);
        layer = ArtLayer.fromPixels(
          name: name,
          pixels: pixels,
          width: image.width,
          height: image.height,
        );
      } else {
        layer = ArtLayer.empty(
          name: name,
          width: layerWidth > 0 ? layerWidth : 1080,
          height: layerHeight > 0 ? layerHeight : 1920,
        );
      }

      layer.opacity = opacity;
      layer.visible = visible;
      layer.locked = locked;
      layer.isBackground = isBackground;

      return layer;
    } catch (e) {
      return null;
    }
  }

  /// Converts an image library Image to RGBA Uint8List.
  static Uint8List _imageToRgba(img.Image image) {
    final pixels = Uint8List(image.width * image.height * 4);
    for (int y = 0; y < image.height; y++) {
      for (int x = 0; x < image.width; x++) {
        final pixel = image.getPixel(x, y);
        final offset = (y * image.width + x) * 4;
        pixels[offset] = pixel.r.toInt();
        pixels[offset + 1] = pixel.g.toInt();
        pixels[offset + 2] = pixel.b.toInt();
        pixels[offset + 3] = pixel.a.toInt();
      }
    }
    return pixels;
  }

  /// Exports the current canvas state to an IPV file.
  ///
  /// [state] contains all layers and canvas metadata.
  /// [filePath] is the output file path (.ipv).
  static Future<void> exportIPv(CanvasState state, String filePath) async {
    final output = BytesBuilder();

    // Write magic bytes
    output.addByte(0x49); // I
    output.addByte(0x50); // P
    output.addByte(0x56); // V
    output.addByte(0x00); // \0

    // Write AddCanvas chunk
    _writeChunk(output, chunkAddCanvas, _buildAddCanvasData(state));

    // Write MetaInfo chunk
    _writeChunk(output, chunkMetaInfo, _buildMetaInfoData(state));

    // Write layer chunks
    for (final layer in state.layers) {
      final layerData = await _buildLayerData(layer);
      _writeChunk(output, chunkManageLayer, layerData);
    }

    // Write end-of-layers marker
    _writeChunk(output, chunkEndOfLayers, []);

    // Write to file
    final file = File(filePath);
    await file.writeAsBytes(output.toBytes());
  }

  /// Builds the AddCanvas chunk data.
  static List<int> _buildAddCanvasData(CanvasState state) {
    final data = BytesBuilder();
    _writeInt32BE(data, state.canvasWidth);
    _writeInt32BE(data, state.canvasHeight);
    // Background color (white)
    data.addByte(0xFF);
    data.addByte(0xFF);
    data.addByte(0xFF);
    data.addByte(0xFF);
    // DPI
    _writeInt32BE(data, 72);
    return data.toBytes().toList();
  }

  /// Builds the MetaInfo chunk data.
  static List<int> _buildMetaInfoData(CanvasState state) {
    final meta = {
      'projectName': state.projectName,
      'author': 'VenPaint User',
      'appInfo': 'VenPaint $appVersion',
      'canvasWidth': state.canvasWidth,
      'canvasHeight': state.canvasHeight,
      'layerCount': state.layers.length,
      'createdAt': DateTime.now().toIso8601String(),
    };
    return utf8.encode(jsonEncode(meta));
  }

  /// Builds layer data for a ManageLayer chunk.
  static Future<List<int>> _buildLayerData(ArtLayer layer) async {
    final data = BytesBuilder();

    // Write layer name (UTF-16BE encoded with 2-byte length prefix)
    final nameBytes = utf8.encode(layer.name);
    _writeUint16BE(data, nameBytes.length);
    data.add(nameBytes);

    // Write opacity
    _writeFloat32BE(data, layer.opacity);

    // Write visibility
    data.addByte(layer.visible ? 1 : 0);

    // Write blend mode index
    _writeUint16BE(data, layer.blendMode.index);

    // Write locked
    data.addByte(layer.locked ? 1 : 0);

    // Write isBackground
    data.addByte(layer.isBackground ? 1 : 0);

    // Write dimensions
    _writeInt32BE(data, layer.width);
    _writeInt32BE(data, layer.height);

    // Write PNG image data
    if (layer.pixels != null && layer.hasContent()) {
      final pngBytes = _rgbaToPng(layer.pixels!, layer.width, layer.height);
      data.add(pngBytes);
    }

    return data.toBytes().toList();
  }

  /// Converts RGBA pixel data to PNG bytes using the image library.
  static Uint8List _rgbaToPng(Uint8List pixels, int width, int height) {
    final image = img.Image(width: width, height: height);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final offset = (y * width + x) * 4;
        image.setPixel(
          x,
          y,
          img.ColorRgba8(
            pixels[offset],
            pixels[offset + 1],
            pixels[offset + 2],
            pixels[offset + 3],
          ),
        );
      }
    }
    return Uint8List.fromList(img.encodePng(image));
  }

  /// Writes a chunk with ID, length, data, and checksum.
  static void _writeChunk(BytesBuilder output, int id, List<int> data) {
    _writeUint32BE(output, id);
    _writeUint32BE(output, data.length);
    output.add(data);
    // Negative-length checksum
    _writeInt32BE(output, -data.length);
  }

  /// Writes a 32-bit unsigned integer in big-endian.
  static void _writeUint32BE(BytesBuilder output, int value) {
    output.addByte((value >> 24) & 0xFF);
    output.addByte((value >> 16) & 0xFF);
    output.addByte((value >> 8) & 0xFF);
    output.addByte(value & 0xFF);
  }

  /// Writes a 32-bit signed integer in big-endian.
  static void _writeInt32BE(BytesBuilder output, int value) {
    _writeUint32BE(output, value);
  }

  /// Writes a 16-bit unsigned integer in big-endian.
  static void _writeUint16BE(BytesBuilder output, int value) {
    output.addByte((value >> 8) & 0xFF);
    output.addByte(value & 0xFF);
  }

  /// Writes a 32-bit float in big-endian.
  static void _writeFloat32BE(BytesBuilder output, double value) {
    final data = ByteData(4);
    data.setFloat32(0, value, Endian.big);
    output.addByte(data.getUint8(0));
    output.addByte(data.getUint8(1));
    output.addByte(data.getUint8(2));
    output.addByte(data.getUint8(3));
  }
}

/// Result of importing an IPV file.
class IpvProject {
  final int canvasWidth;
  final int canvasHeight;
  final String projectName;
  final String author;
  final String appInfo;
  final List<ArtLayer> layers;

  const IpvProject({
    required this.canvasWidth,
    required this.canvasHeight,
    required this.projectName,
    required this.author,
    required this.appInfo,
    required this.layers,
  });
}

/// Helper class for sequential reading of binary data.
class _ByteReader {
  final Uint8List data;
  int _offset = 0;

  _ByteReader(this.data);

  int get remainingBytes => data.length - _offset;

  int readUint8() {
    if (_offset >= data.length) throw const FormatException('Unexpected end of data');
    return data[_offset++];
  }

  int readInt32BE() {
    if (_offset + 4 > data.length) throw const FormatException('Unexpected end of data');
    final value = (data[_offset] << 24) |
        (data[_offset + 1] << 16) |
        (data[_offset + 2] << 8) |
        data[_offset + 3];
    _offset += 4;
    return value;
  }

  int readUint32BE() {
    return readInt32BE() & 0xFFFFFFFF;
  }

  int readUint16BE() {
    if (_offset + 2 > data.length) throw const FormatException('Unexpected end of data');
    final value = (data[_offset] << 8) | data[_offset + 1];
    _offset += 2;
    return value;
  }

  double readFloat32BE() {
    if (_offset + 4 > data.length) throw const FormatException('Unexpected end of data');
    final bytes = ByteData.sublistView(data, _offset, _offset + 4);
    final value = bytes.getFloat32(0, Endian.big);
    _offset += 4;
    return value;
  }

  List<int> readBytes(int length) {
    if (_offset + length > data.length) throw const FormatException('Unexpected end of data');
    final result = data.sublist(_offset, _offset + length);
    _offset += length;
    return result;
  }

  Uint8List readRemainingBytes() {
    final result = data.sublist(_offset);
    _offset = data.length;
    return result;
  }
}

/// Extension to compare two byte lists.
extension _ByteListEq on List<int> {
  bool eq(List<int> other) {
    if (length != other.length) return false;
    for (int i = 0; i < length; i++) {
      if (this[i] != other[i]) return false;
    }
    return true;
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'models/canvas_state.dart';
import 'ui/screens/home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  // Set preferred orientations for mobile painting app
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  // Set status bar style
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.dark,
  ));

  runApp(const VenPaintApp());
}

class VenPaintApp extends StatelessWidget {
  const VenPaintApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VenPaint',
      debugShowCheckedModeBanner: false,
      theme: _buildTheme(),
      home: ChangeNotifierProvider(
        create: (_) => CanvasState(),
        child: const HomeScreen(),
      ),
    );
  }

  ThemeData _buildTheme() {
    return ThemeData(
      useMaterial3: true,
      colorScheme: const ColorScheme(
        brightness: Brightness.light,
        primary: Color(0xFF1A1A2E),
        onPrimary: Colors.white,
        primaryContainer: Color(0xFFE94560),
        onPrimaryContainer: Colors.white,
        secondary: Color(0xFF533483),
        onSecondary: Colors.white,
        secondaryContainer: Color(0xFF7B4FBF),
        onSecondaryContainer: Colors.white,
        tertiary: Color(0xFF0F3460),
        onTertiary: Colors.white,
        error: Color(0xFFE94560),
        onError: Colors.white,
        surface: Color(0xFFF5F5F5),
        onSurface: Color(0xFF1A1A2E),
      ),
      fontFamily: 'Roboto',
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: Color(0xFF1A1A2E),
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        elevation: 1,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFFE94560),
          foregroundColor: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),
      sliderTheme: const SliderThemeData(
        activeTrackColor: Color(0xFFE94560),
        thumbColor: Colors.white,
        overlayColor: Color(0x29E94560),
      ),
    );
  }
}

# AAudio Forwarder

## ✨ Fitur Utama

- ✅ **Zero-Copy Audio Transfer** - Direct buffer, tanpa memcpy
- ✅ **CLIENT MODE** - Android connect ke PC (compatible dengan QtScrcpy)
- ✅ **Buffer Optimized** - buffer untuk audio smooth tanpa crackling
- ✅ **Dark UI** - Minimal battery drain, auto-minimize
- ✅ **Kotlin Native** - Lebih clean & maintainable dari Java

## 🔧 Build Requirements

- Android API 29+ (Android 10+)
- NDK r25c atau lebih baru
- Kotlin 1.9+
- CMake 3.22.0+

## 📦 Instalasi

### 1. Build Manual (GitHub Actions)

```bash
# Clone repo
git clone https://github.com/yourusername/aaudio-forwarder
cd aaudio-forwarder

# Build dengan Gradle
./gradlew assembleRelease

# APK ada di: app/build/outputs/apk/release/app-release.apk
```

### 2. Auto Build (GitHub Actions)

Push ke GitHub, Actions akan auto build APK. Download dari tab **Actions** → **Artifacts**.

## 🚀 Cara Pakai dengan QtScrcpy

### Setup Script

1. Copy `sndcpy.sh` ke folder QtScrcpy (folder yang sama dengan `scrcpy`)
2. Copy `app-release.apk` ke folder yang sama
3. Pastikan executable:
   ```bash
   chmod +x sndcpy.sh
   ```

### Struktur Folder

```
/usr/share/qtscrcpy/
├── scrcpy
├── sndcpy.sh          ← Script ini
├── app-release.apk    ← APK ini
└── ...
```

### Di QtScrcpy

1. Connect HP via USB
2. Klik tombol **"Start Audio"**
3. **FIRST TIME**: Izinkan permission screen capture di HP
4. HP akan auto-minimize, audio langsung streaming!

## 🐛 Troubleshooting

### ❌ Audio tidak keluar

**Penyebab**: ADB reverse tunnel gagal atau port conflict

**Solusi**:
```bash
# Check reverse tunnel
adb reverse --list

# Seharusnya ada:
# tcp:28200 tcp:28200

# Kalau nggak ada, manual set:
adb reverse tcp:28200 tcp:28200

# Restart service di HP
adb shell am force-stop com.aaudio.forwarder
```

### ❌ Service tidak jalan

**Check log**:
```bash
adb logcat | grep AAudioFwd
```

**Common issues**:
- **"Failed to connect to PC"** → PC belum ready, atau reverse tunnel salah
- **"AudioRecord read error"** → Permission denied atau MediaProjection error

### ❌ Audio choppy/crackling

**Penyebab**: Network bottleneck atau CPU overload

**Solusi**:
1. Pastikan USB cable berkualitas (USB 3.0 lebih baik)
2. Tutup aplikasi lain yang pakai audio di HP
3. Disable app lain yang recording audio
4. Coba port lain (edit di script):
   ```bash
   SNDCPY_PORT=28201  # Ganti ke port lain
   ```

### ❌ Permission denied (MediaProjection)

**Solusi**:
```bash
# Grant manual
adb shell appops set com.aaudio.forwarder PROJECT_MEDIA allow

# Atau install ulang dengan -g flag
adb uninstall com.aaudio.forwarder
adb install -r -g app-release.apk
```

## ⚙️ Advanced Configuration

### Custom Port

Edit `sndcpy.sh`:
```bash
SNDCPY_PORT=28300  # Ganti sesuai keinginan
```

Atau pass via CLI:
```bash
./sndcpy.sh <device-serial> 28300
```

### Buffer Size Tuning

Edit `AudioForwardService.kt`:
```kotlin
private const val BUFFER_SIZE = 8192  // Default
// Gaming: 4096 (lower latency, higher CPU)
// Music: 16384 (higher latency, smoother)
```

### Sample Rate Change

Edit `AudioForwardService.kt`:
```kotlin
private const val SAMPLE_RATE = 48000  // Default
// Alternative: 44100 (CD quality, lower bandwidth)
```

## 🔬 Technical Details

### Audio Pipeline

```
Android AudioPlaybackCapture
    ↓ (PCM 48kHz Stereo 16-bit)
DirectByteBuffer (zero-copy)
    ↓
Native C++ (sendAudioDirect)
    ↓
TCP Socket (CLIENT MODE)
    ↓
ADB Reverse Tunnel
    ↓
PC QtScrcpy (QAudioSink)
```

### Why CLIENT MODE?

**Original sndcpy**: Server di Android → ADB forward → PC connect ke Android
- Problem: HP batre drain karena server socket always listening
- Problem: Race condition kalau PC belum siap

**AAudio Forwarder**: Server di PC → ADB reverse → Android connect ke PC
- ✅ QtScrcpy sudah punya server socket ready
- ✅ Android hanya connect kalau PC siap
- ✅ No battery drain dari server listening
- ✅ Auto-disconnect kalau PC close connection

### Optimizations Applied

1. **DirectByteBuffer**: No JNI array copy overhead
2. **TCP_NODELAY**: Disable Nagle algorithm untuk low-latency
3. **SO_SNDBUF 256KB**: Large send buffer untuk smooth streaming
5. **Thread.MAX_PRIORITY**: Audio capture thread gets highest CPU priority
6. **Buffer 4KB**: Sweet spot untuk 48kHz stereo (43ms latency)
   
## 🛠️ Development

### Project Structure

```
app/src/main/
├── java/com/aaudio/forwarder/
│   ├── MainActivity.kt
│   └── AudioForwardService.kt
├── cpp/
│   ├── CMakeLists.txt
│   ├── jni_bridge.cpp
│   ├── network_client.cpp
│   └── network_client.h
└── AndroidManifest.xml
```

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (optimized)
./gradlew assembleRelease

# Install to device
./gradlew installRelease

# Clean build
./gradlew clean assembleRelease
```

## 📝 License

MIT License - Feel free to use & modify!

## 🙏 Credits

- Original [sndcpy](https://github.com/rom1v/sndcpy) by rom1v
- [QtScrcpy](https://github.com/barry-ran/QtScrcpy) integration

## 🐞 Bug Reports

Open issue di GitHub dengan info:
- Device model & Android version
- QtScrcpy version
- ADB logcat output
- Steps to reproduce

---

**Made with ☕ for smooth gaming audio forwarding**

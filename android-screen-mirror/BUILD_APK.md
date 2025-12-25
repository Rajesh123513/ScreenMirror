# Building APK for Android TV

This guide explains how to build an executable APK file for your Android TV.

## Prerequisites

1. **Android Studio** - Download from [developer.android.com/studio](https://developer.android.com/studio)
2. **JDK 17** - Required for building
3. **Android SDK** - Installed via Android Studio

## Build Steps

### Method 1: Using Android Studio (Recommended)

1. **Open Project**
   - Launch Android Studio
   - Click "Open" and select the `android-screen-mirror` folder
   - Wait for Gradle sync to complete

2. **Build APK**
   - Go to `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - Wait for build to complete
   - Click "locate" in the notification to find the APK
   - APK will be in: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build Release APK** (for distribution)
   - Go to `Build` → `Generate Signed Bundle / APK`
   - Select "APK"
   - Create a new keystore (or use existing)
   - Fill in keystore details
   - Select "release" build variant
   - Click "Finish"
   - APK will be in: `app/build/outputs/apk/release/app-release.apk`

### Method 2: Using Command Line

1. **Navigate to project directory**
   ```bash
   cd android-screen-mirror
   ```

2. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build Release APK**
   ```bash
   ./gradlew assembleRelease
   ```
   APK location: `app/build/outputs/apk/release/app-release.apk`

## Installing on Android TV

### Option 1: USB Installation

1. Enable **Developer Options** on your Android TV:
   - Go to Settings → About
   - Click on "Build" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging"

2. Connect TV to computer via USB
3. Install ADB on your computer
4. Run:
   ```bash
   adb install app-debug.apk
   ```

### Option 2: Network Installation (ADB over WiFi)

1. Enable Developer Options and USB Debugging (see above)
2. Connect TV and computer to same WiFi
3. Get TV's IP address (Settings → Network)
4. Connect via ADB:
   ```bash
   adb connect <TV_IP_ADDRESS>:5555
   adb install app-debug.apk
   ```

### Option 3: Using File Manager

1. Copy APK to USB drive
2. Insert USB into Android TV
3. Use a file manager app (like ES File Explorer) to install
4. Enable "Install from Unknown Sources" if prompted

### Option 4: Using Google Drive/Dropbox

1. Upload APK to cloud storage
2. Open on Android TV browser
3. Download and install

## APK File Information

- **Package Name**: `com.screenmirror.app`
- **Min SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **File Size**: ~5-10 MB (depending on build type)

## Troubleshooting

### Build Errors

- **Gradle sync failed**: Update Android Studio and SDK tools
- **JDK not found**: Install JDK 17 and set JAVA_HOME
- **SDK missing**: Install required SDK via SDK Manager

### Installation Errors

- **"App not installed"**: Check if app with same package name exists, uninstall first
- **"Unknown sources"**: Enable in Settings → Security
- **ADB not found**: Install Android Platform Tools

## Signing APK for Release

For production use, sign your APK:

1. Generate keystore:
   ```bash
   keytool -genkey -v -keystore screenmirror.keystore -alias screenmirror -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Update `app/build.gradle`:
   ```gradle
   android {
       signingConfigs {
           release {
               storeFile file('screenmirror.keystore')
               storePassword 'your-password'
               keyAlias 'screenmirror'
               keyPassword 'your-password'
           }
       }
       buildTypes {
           release {
               signingConfig signingConfigs.release
           }
       }
   }
   ```

3. Build signed APK:
   ```bash
   ./gradlew assembleRelease
   ```

## Notes

- Debug APKs are larger but easier to debug
- Release APKs are optimized and smaller
- Always test on actual Android TV device
- Keep keystore file secure for release builds


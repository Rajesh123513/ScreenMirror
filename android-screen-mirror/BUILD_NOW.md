# Build APK Now - Step by Step

## Option 1: Using Android Studio (Easiest)

1. **Download Android Studio**
   - Go to https://developer.android.com/studio
   - Download and install Android Studio

2. **Open Project**
   - Launch Android Studio
   - Click "Open" 
   - Navigate to and select the `android-screen-mirror` folder
   - Wait for Gradle sync (may take a few minutes first time)

3. **Build APK**
   - Click `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
   - Wait for build to complete (1-2 minutes)
   - When done, click "locate" in the notification
   - APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

4. **That's it!** The APK is ready to install.

## Option 2: Using Command Line (If you have Android SDK)

### Prerequisites
- Java JDK 17 installed
- Android SDK installed
- Gradle installed (or use wrapper)

### Build Steps

1. **Open Terminal**
   ```bash
   cd /Users/rajeshpothunuri/my-app/android-screen-mirror
   ```

2. **Make build script executable**
   ```bash
   chmod +x build-apk.sh
   ```

3. **Run build script**
   ```bash
   ./build-apk.sh
   ```

   OR manually:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Find your APK**
   - Location: `app/build/outputs/apk/debug/app-debug.apk`

## Option 3: Online Build Service (No Installation)

If you can't install Android Studio, you can use:

1. **GitHub Actions** (if you push to GitHub)
2. **Gitpod** - Online IDE with Android support
3. **CircleCI** or **Travis CI** - CI/CD services

## Quick Build Commands

```bash
# Navigate to project
cd android-screen-mirror

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing)
./gradlew assembleRelease
```

## APK Location After Build

The APK will be located at:
```
android-screen-mirror/app/build/outputs/apk/debug/app-debug.apk
```

## Install APK on Android TV

### Method 1: ADB (Recommended)
```bash
# Connect TV via USB or WiFi
adb connect <TV_IP>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: USB Drive
1. Copy APK to USB drive
2. Insert into Android TV
3. Use file manager to install

### Method 3: Cloud Storage
1. Upload APK to Google Drive
2. Open on TV browser
3. Download and install

## Troubleshooting

### "Gradle wrapper not found"
- Open project in Android Studio first (it will create wrapper)
- Or download Gradle manually

### "SDK not found"
- Install Android SDK via Android Studio
- Set ANDROID_HOME environment variable

### "Java not found"
- Install JDK 17
- Set JAVA_HOME environment variable

### Build Errors
- Make sure you're using Java 17
- Update Android SDK tools
- Sync Gradle files in Android Studio

## Need Help?

If you encounter issues:
1. Check Android Studio is fully installed
2. Ensure all SDK components are installed
3. Try building in Android Studio first (it handles setup automatically)


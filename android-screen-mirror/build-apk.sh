#!/bin/bash

# Build APK Script for Screen Mirror App
# This script builds the Android APK file

echo "=========================================="
echo "Building Screen Mirror APK"
echo "=========================================="

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    echo "Error: Please run this script from the android-screen-mirror directory"
    exit 1
fi

# Check if Gradle wrapper exists
if [ ! -f "gradlew" ]; then
    echo "Gradle wrapper not found. Creating it..."
    # This would normally be done by Android Studio, but we'll create a basic one
    echo "Please use Android Studio to generate the Gradle wrapper, or install Gradle manually"
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

echo "Cleaning previous builds..."
./gradlew clean

echo "Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        echo ""
        echo "=========================================="
        echo "✅ APK built successfully!"
        echo "=========================================="
        echo "APK location: $APK_PATH"
        echo ""
        echo "File size: $(du -h "$APK_PATH" | cut -f1)"
        echo ""
        echo "To install on Android TV:"
        echo "  adb install $APK_PATH"
        echo "=========================================="
    else
        echo "Error: APK file not found at expected location"
        exit 1
    fi
else
    echo "Error: Build failed"
    exit 1
fi


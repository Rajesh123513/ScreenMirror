# Installation Instructions

## Quick Start - No Mac Installation Required!

This app works with **Mac's built-in Screen Mirroring** - no need to install anything on your Mac!

## Step 1: Install APK on Android TV

1. Build the APK (see `BUILD_APK.md`) or download the pre-built APK
2. Install on your Android TV using one of these methods:
   - **USB**: Connect TV to computer, use `adb install app-debug.apk`
   - **Network**: Use ADB over WiFi
   - **USB Drive**: Copy APK to USB, use file manager on TV
   - **Cloud**: Upload to Google Drive, download on TV

## Step 2: Launch the App

1. Open "Screen Mirror" app on your Android TV
2. The app will automatically start waiting for Mac connection
3. You'll see your TV's IP address displayed

## Step 3: Connect from Mac (No Installation Needed!)

### On Your Mac:

1. **Ensure both devices are on the same WiFi network**

2. **Open Screen Mirroring on Mac:**
   - Click the AirPlay icon in the menu bar (top right)
   - OR go to System Settings → Displays → AirPlay Display
   - OR press `Control + F2` (if enabled)

3. **Select "Android TV"** from the list of available devices
   - The TV should appear automatically (thanks to mDNS discovery)
   - If not visible, you can manually enter the IP address shown on TV

4. **Your Mac screen will start mirroring to Android TV!**

## Features

✅ **No Mac Installation** - Uses built-in Screen Mirroring  
✅ **Automatic Discovery** - TV appears in Mac's AirPlay menu  
✅ **4K Support** - High-quality mirroring  
✅ **Real-time Stats** - Network speed and FPS displayed  
✅ **Easy Disconnect** - Stop from TV or Mac  

## Troubleshooting

### TV Not Appearing in Mac's AirPlay Menu

1. **Check WiFi**: Both devices must be on same network
2. **Check Firewall**: Mac firewall might block mDNS
3. **Manual Connection**: Use IP address shown on TV
4. **Restart App**: Close and reopen the app on TV

### Connection Fails

1. **Check Network Speed**: Need at least 25 Mbps for smooth streaming
2. **Check TV IP**: Ensure IP address is correct
3. **Restart Both Devices**: Sometimes helps with network issues
4. **Check Permissions**: Grant all permissions to the app

### Video Quality Issues

1. **Network Speed**: Check displayed network speed on TV
2. **WiFi Signal**: Ensure strong WiFi signal on both devices
3. **Interference**: Move devices closer to router
4. **Other Devices**: Disconnect other bandwidth-heavy devices

## Advanced: Manual IP Connection

If automatic discovery doesn't work:

1. Note the IP address shown on TV (e.g., `192.168.1.100`)
2. On Mac, you may need to use a third-party AirPlay client
3. Or use the manual connection option in the app (if available)

## Security Notes

- Only works on local network
- No data leaves your network
- Safe to use on home WiFi
- Consider VPN if on public WiFi

## Support

For issues:
1. Check both devices are on same WiFi
2. Verify app is running on TV
3. Check Mac's Screen Mirroring settings
4. Review network speed requirements


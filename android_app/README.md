# GeoImage Android App

This Android demo app allows users to select mock GPS locations and add geotags to images with the following features:

## Features

- Select locations on a map and set them as mock GPS location
- Take photos with camera or select images from gallery
- Add geolocation data to images
- View a gallery of geotagged images
- AdMob integration with app open ads, banner ads, interstitial ads, and rewarded ads

## How to build the app

1. Open the project in Android Studio
2. Make sure you have the Android SDK installed and updated
3. Connect your Android device via USB with USB Debugging enabled
4. Click on "Run app" in Android Studio or use the following command:
   ```
   ./gradlew assembleDebug
   ```
5. Install the APK on your device:
   ```
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Directory Structure

- `app/src/main/java/com/geoimage/app/`:
  - `model/`: Contains data model classes like GeoImage, Location
  - `ui/`: Activities for main screen, location selection, image preview, and gallery
  - `util/`: Utility classes for image processing, location handling, and ad management
  - `GeoImageApp.java`: Main application class with AdMob initialization

- `app/src/main/res/`:
  - `layout/`: XML layout files for all activities
  - `values/`: Strings, colors, styles, etc.
  - `drawable/`: Icons and other drawable resources
  - `xml/`: Configuration files like file_paths.xml for FileProvider

## Monetization

The app uses Google AdMob for monetization with:
- App opening ads that show when the app is launched
- Banner ads at the bottom of screens
- Interstitial ads before showing the gallery
- Rewarded ads after processing images

## Permissions

The app requests the following permissions:
- Internet and network state for AdMob
- Location permissions for setting mock locations
- Camera permissions for taking photos
- Storage permissions for reading/writing images
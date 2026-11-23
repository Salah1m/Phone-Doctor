# Phone Doctor

Android app for diagnosing a second Android phone and exploring supported recovery/connection paths.

## Important limitations

- An ordinary Android app cannot silently bypass another phone's lock screen, USB authorization prompt, or secure-screen capture protections.
- Screen viewing/control requires a supported and authorized transport or prior setup on the target device.
- USB detection does not prove that a motherboard is healthy.
- This first milestone includes the app shell and basic USB device discovery. USB data access, screen streaming, remote input, and file transfer are not implemented yet.

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main`.

## Initial device support

Designed for testing with Android phones from Samsung, Xiaomi/Redmi, and Oppo. Actual capabilities vary by model, Android version, USB-C implementation, and device settings.

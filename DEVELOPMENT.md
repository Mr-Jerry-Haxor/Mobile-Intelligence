# Development Guide

This guide helps contributors build, test, and work on Mobile Intelligence locally.

## Prerequisites

- Windows 10/11, macOS, or Linux
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- JDK 17 (minimum)

## Initial Setup

1. Clone the repository.
2. Open the root folder in Android Studio.
3. Let Gradle sync complete.
4. Connect a device or start an emulator (Android 8.0+).

## Common Commands

Run from repository root.

Windows:

```powershell
.\gradlew.bat clean assembleDebug
.\gradlew.bat lint
.\gradlew.bat test
```

If shell wrapper is present in your environment:

```bash
./gradlew clean assembleDebug
./gradlew lint
./gradlew test
```

## Debugging Tips

- Use Logcat tags from service and engine classes for runtime status.
- Verify permissions manually on device for realistic behavior testing.
- Test reboot and process-kill scenarios for service resilience.

## High-Risk Change Areas

Be extra careful when modifying:

- `AndroidManifest.xml` exported components and permissions
- Foreground service and VPN lifecycle logic
- Data deletion and retention logic
- PIN and protected-action flows

## Release Notes for Maintainers

- Do not commit signing keys or credentials.
- Keep keystore files out of version control.
- Verify release signing configuration before publishing artifacts.
- Update [CHANGELOG.md](CHANGELOG.md), [PRIVACY.md](PRIVACY.md), and [PERMISSIONS.md](PERMISSIONS.md) for user-impacting changes.

## Documentation Map

- Project overview: [README.md](README.md)
- Contribution process: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security reporting: [SECURITY.md](SECURITY.md)
- Privacy policy: [PRIVACY.md](PRIVACY.md)
- Permission rationale: [PERMISSIONS.md](PERMISSIONS.md)

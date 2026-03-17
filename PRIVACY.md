# Privacy Policy

This project is designed as a local-first Android app. This document explains what data is processed, where it is stored, and what user controls exist.

## Summary

- Core telemetry is processed on-device.
- No third-party analytics SDK is included.
- DNS feature requires network access to resolve DNS and optionally download blocklists.
- Users can clear stored data from the app.

## Data Collected and Stored

### Screen and Usage Intelligence

Examples of stored fields:

- Screen on/off and session timestamps
- Session duration and classification
- Foreground app package name and usage duration
- Unlock timing and session context

Primary storage:

- `mobile_intelligence.db` (Room database)

### DNS Protection Data (When DNS Feature Is Enabled)

Examples of stored fields:

- Queried domain name
- Allowed/blocked decision and reason
- Query response time
- Resolved app package (best effort)
- Daily aggregate DNS stats

Primary storage:

- `dns_firewall.db` (Room database)

### Preferences and Security State

Examples:

- Feature toggles and retention settings
- Theme and startup preferences
- PIN enabled state and PIN hash

Primary storage:

- DataStore `mi_settings`
- DataStore `dns_preferences`

## Data Retention

Current defaults in code include:

- Screen intelligence retention: configurable; default 3 years
- DNS log retention preference: default 30 days

Retention can evolve between versions. See release notes and source for exact behavior in your build.

## Network Access

The app requests `INTERNET` and `ACCESS_NETWORK_STATE` to support DNS protection features, including:

- Upstream DNS resolution
- Optional blocklist updates from configured URLs

Core screen/activity intelligence does not require cloud telemetry.

## Permissions

See [PERMISSIONS.md](PERMISSIONS.md) for a full permission-by-permission explanation.

## User Controls

From in-app settings, users can:

- Enable/disable monitoring features
- Configure retention and DNS behavior
- Wipe stored data
- Configure or remove local PIN protection

## Data Deletion

Users can delete app data by:

1. Using in-app delete/clear controls.
2. Clearing app storage from Android system settings.
3. Uninstalling the app.

## Backup Behavior

Android backup behavior is controlled by `android:allowBackup` in the manifest. Review the current value in your build and adjust if your deployment policy requires stricter local data protection.

## Children and Sensitive Use

This project is an open-source utility and is not intended as a medical, legal, or parental-control guarantee.

## Changes to This Policy

Policy updates should be tracked in [CHANGELOG.md](CHANGELOG.md).

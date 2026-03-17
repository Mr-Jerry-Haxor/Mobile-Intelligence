# Android Permissions Rationale

This file explains why each declared permission exists and whether it is required for all users or only for specific features.

## Core Permissions

| Permission | Required | Why It Is Used |
| --- | --- | --- |
| `RECEIVE_BOOT_COMPLETED` | Yes | Restart monitoring services/workers after reboot when enabled. |
| `FOREGROUND_SERVICE` | Yes | Run persistent foreground services for reliability. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Yes | Declare special-use foreground service subtype for long-running monitoring/DNS services. |
| `WAKE_LOCK` | Yes | Keep CPU active during critical service operations. |
| `POST_NOTIFICATIONS` | Android 13+ | Show required persistent service notifications. |
| `SCHEDULE_EXACT_ALARM` | Feature-dependent | Schedule precise maintenance/restart tasks. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional but recommended | Reduce system kills for long-running monitoring. |

## Usage and App Visibility

| Permission | Required | Why It Is Used |
| --- | --- | --- |
| `PACKAGE_USAGE_STATS` | Required for app usage insights | Read foreground usage events to compute per-app/session metrics. |
| `QUERY_ALL_PACKAGES` | Required for DNS per-app attribution | Map network activity to app packages for DNS logs and stats. |

## DNS Firewall Permissions

| Permission | Required | Why It Is Used |
| --- | --- | --- |
| `INTERNET` | Required for DNS feature | Resolve DNS upstream and optionally download blocklists. |
| `ACCESS_NETWORK_STATE` | Required for DNS feature | Detect connectivity state changes and react safely. |
| `BIND_VPN_SERVICE` | Required for DNS feature | Run local VPN-based DNS interception service. |

## Optional Security Hardening Feature

| Permission | Required | Why It Is Used |
| --- | --- | --- |
| `BIND_DEVICE_ADMIN` | Optional | Enable Device Admin-based uninstall protection when user opts in. |

## Notes for Forks and Distributions

- If you remove DNS features, also remove DNS/VPN-related permissions.
- If you remove uninstall protection, remove Device Admin receiver and related permission.
- Keep this file updated when `AndroidManifest.xml` changes.

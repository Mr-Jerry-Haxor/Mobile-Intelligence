# Mobile Intelligence

**Advanced Android Screen Activity & Privacy Intelligence App**

A production-grade Android application for long-term passive monitoring of device screen activity, app usage behavior, and network privacy — all processed on-device with zero cloud dependencies.

---

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
- [How to Use — Complete Guide](#how-to-use--complete-guide)
  - [Bottom Navigation](#bottom-navigation)
  - [Dashboard](#1-dashboard)
  - [Intelligence Console](#2-intelligence-console)
  - [Apps](#3-apps)
  - [Network Protection](#4-network-protection-dns-firewall)
  - [Timeline](#5-timeline)
  - [Settings](#6-settings)
  - [Usage Heatmap](#7-usage-heatmap)
  - [NumLock PIN Security](#11-numlock-pin-security)
  - [Focus Mode](#8-focus-mode)
  - [Timeline Replay](#9-timeline-replay)
  - [Blocklist Management](#10-blocklist-management)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Build Instructions](#build-instructions)
- [Themes](#themes)
- [Privacy](#privacy)
- [Open Source](#open-source)
- [Community](#community)
- [Security Reporting](#security-reporting)
- [License](#license)

---

## Features

### Screen Intelligence

- **Behavioral session modeling** — classifies sessions as micro, quick-check, normal, extended, doom-scroll, or compulsive-recheck
- **Unlock pattern analysis** — detects compulsive loops, night wakeups, rapid succession unlocks, and peak usage hours
- **Foreground app tracking** — app switch timeline, usage durations, daily rankings via UsageStatsManager
- **Device state correlation** — brightness, charging state, and battery level enrichment per session
- **Wake source inference** — heuristic-based detection of notification vs. manual unlock

### DNS Firewall (Pi-hole-like)

- **Local VPN DNS sinkhole** — intercepts DNS queries system-wide via a lightweight VPN service
- **Multi-tier blocklists** — ads, trackers, malware, phishing with category tagging
- **Custom blocklist management** — add URLs from 20+ pre-configured Pi-hole community lists or your own custom URLs
- **DNS caching** — LRU cache with TTL for faster resolution (10k entries)
- **DoH detection** — identifies apps attempting DNS-over-HTTPS bypass
- **Per-app DNS stats** — query counts, blocked domains, and tracker profiles per app
- **Daily stats aggregation** — automatic midnight rollup of per-app and per-domain stats

### Intelligence Engine

- **Digital Addiction Index (DAI)** — 0-100 score based on unlock frequency, session length, and app switching patterns
- **Focus Stability Score (FSS)** — 0-100 score measuring sustained attention vs. rapid context switching
- **Privacy Exposure Score (PES)** — 0-100 score derived from tracker DNS queries, blocked ratio, and DoH evasion
- **Pattern detection** — doom scrolling, compulsive checking, night disruption, extended continuous use, rapid app switching
- **Correlation engine** — cross-analyzes screen + DNS data to produce human-readable privacy insights

### Focus Mode

- **Zen mode** — distraction-free timer with interruption counting
- **Pomodoro timer** — 25/5 work-break cycles with session tracking
- **Deep focus** — extended focus sessions with event-bus interruption detection

### UI & Themes

- **Material You** — dynamic colors from wallpaper (Android 12+)
- **AMOLED Black** — pure black background for OLED screens
- **Monochrome** — grayscale minimal design
- **Pastel Analytics** — soft purple analytics theme
- **Interactive heatmap** — hourly/daily activity visualization with DNS overlay
- **Timeline replay** — animated session playback

### Security & App Protection

- **NumLock PIN** — 4–8 digit numeric PIN (SHA-256 hashed) protects all destructive actions
- **Protected operations** — Wipe All Data, Clear DNS Data, and Disable Tracking all require PIN verification
- **Device Admin protection** — Android Device Administrator enrollment prevents unauthorized uninstall, force-stop, or data clearing
- **PIN management** — set, change, or remove PIN from Settings > Security (removal requires current PIN verification)
- **Tamper-resistant** — Device Admin must be deactivated before app can be removed; deactivation requires PIN

### Reliability

- **Boot-persistent** — auto-starts on device reboot
- **24/7 operation** — foreground service with WakeLock, START_STICKY, AlarmManager restart safety net
- **Watchdog workers** — WorkManager periodic checks to restart service if killed
- **Battery optimization exemption** — auto-requests unrestricted battery on launch
- **3-year data retention** — configurable retention with automatic midnight rollup and VACUUM

---

## Getting Started

### Install & First Launch

1. **Install the APK** on your Android device (Android 8.0+)
2. **Grant permissions** when prompted:
   - **Notification Permission** (Android 13+) — allows the persistent monitoring notification
   - **Battery Optimization** — tap "Allow" to let the app run unrestricted in the background
3. **Grant Usage Access** manually:
   - Go to **Settings → Apps → Special Access → Usage Access**
   - Find **Mobile Intelligence** and enable it
   - This is required for app usage tracking
4. The monitoring service starts automatically and shows a persistent notification

### What Happens Automatically

Once set up, Mobile Intelligence runs silently in the background:

- Records every screen ON/OFF event and session duration
- Tracks which apps you use and for how long
- Computes behavioral intelligence scores every 60 seconds
- Performs midnight data rollup (daily summaries, hourly breakdowns)
- A foreground notification keeps the service alive

---

## How to Use — Complete Guide

### Bottom Navigation

The app has **6 tabs** in the bottom navigation bar:

| Tab                    | Icon          | Screen               | Purpose                                            |
| ---------------------- | ------------- | -------------------- | -------------------------------------------------- |
| **Dashboard**    | Grid icon     | Main dashboard       | Today's usage overview and quick stats             |
| **Intelligence** | Brain icon    | Intelligence Console | AI-powered behavioral scores and pattern detection |
| **Apps**         | Apps icon     | App Stats            | Per-app usage rankings and breakdowns              |
| **Network**      | Shield icon   | Network Protection   | DNS Firewall controls, query log, and stats        |
| **Timeline**     | Timeline icon | Timeline             | Chronological session history                      |
| **Settings**     | Gear icon     | Settings             | App configuration, themes, data management         |

---

### 1. Dashboard

**What it shows:**

- Today's total screen time with trend indicator
- Number of unlocks today
- Number of sessions today
- Average session duration
- Night usage time
- Quick overview cards for behavioral scores

**How to use:**

- Open the app — Dashboard is the default landing page
- Glance at your daily stats to get a quick health check
- Tap on any card for more detailed breakdowns (navigates to respective screens)

---

### 2. Intelligence Console

**What it shows:**

- **Three Score Rings** — animated circular indicators for:
  - **Addiction Index (DAI)** — 0-100, higher = more addictive usage (red = bad)
  - **Focus Stability (FSS)** — 0-100, higher = better focus (green = good)
  - **Privacy Exposure (PES)** — 0-100, higher = more tracker exposure (red = bad)
- **Overall Digital Health Bar** — combined score with label (Excellent/Good/Fair/Poor/Critical)
- **Active Alerts** — detected behavioral patterns like doom scrolling, compulsive checking, night disruption
- **Insights** — correlation intelligence messages (e.g., app + tracker activity connections)
- **Engine Status** — health of all 4 engines (ScreenState, DnsFirewall, Storage, Intelligence)

**How to use:**

1. Tap the **Intelligence** tab in the bottom nav
2. Wait a moment for the engine to connect (a loading indicator appears while connecting)
3. Review your three scores — aim for low Addiction, high Focus, low Privacy Exposure
4. Scroll down to see any active pattern alerts — these highlight concerning usage behaviors
5. Check **Insights** for cross-analysis of your screen and DNS activity
6. The **Engine Status** section shows if all monitoring engines are running properly

**Navigation from this screen:**

- Tap the **grid icon** (top-right) to open the **Usage Heatmap**
- Tap the **gear icon** (top-right) to open **Settings**

**Scoring guide:**

| Score            | 80-100    | 60-79 | 40-59    | 20-39 | 0-19      |
| ---------------- | --------- | ----- | -------- | ----- | --------- |
| Addiction Index  | Critical  | Poor  | Moderate | Good  | Excellent |
| Focus Stability  | Excellent | Good  | Moderate | Poor  | Critical  |
| Privacy Exposure | Critical  | Poor  | Moderate | Good  | Excellent |

---

### 3. Apps

**What it shows:**

- Ranked list of apps by usage time
- Per-app stats: total time, session count, percentage of total usage
- Daily and weekly breakdowns

**How to use:**

1. Tap the **Apps** tab
2. See your apps ranked from most to least used
3. Identify which apps consume the most time
4. Use this data to make conscious choices about app usage

> **Note:** Requires Usage Access permission to be granted in system settings.

---

### 4. Network Protection (DNS Firewall)

**What it shows:**

- VPN DNS Firewall toggle (ON/OFF)
- Today's query stats: total queries, blocked, cached, average response time
- Quick-access buttons to Query Log, Stats, and Settings

**How to use:**

#### Enabling the DNS Firewall

1. Tap the **Network** tab
2. Tap the toggle to **enable DNS protection**
3. Accept the VPN connection prompt from Android
4. The firewall is now active — all DNS queries pass through the local filter

#### Viewing DNS Query Log

1. From Network Protection, tap **Query Log**
2. See real-time DNS queries with:
   - Domain name
   - Source app
   - Blocked/Allowed status
   - Response time
3. Use the search bar to find specific domains
4. Filter by blocked-only queries
5. Tap the **back arrow** to return

#### Viewing DNS Stats

1. From Network Protection, tap **Stats**
2. See daily statistics:
   - Total queries, blocked count, block percentage
   - Top queried domains
   - Top blocked domains
   - Per-app query breakdowns
3. Tap the **back arrow** to return

#### DNS Settings

1. From Network Protection, tap **Settings**
2. Configure:
   - **Upstream DNS** — choose your DNS resolver (Cloudflare, Google, Quad9, etc.)
   - **Blocking categories** — toggle ads, trackers, malware, phishing blocking
   - **Whitelist** — add domains that should never be blocked
   - **Manage Blocklists** — open the Blocklist Management screen
3. Tap the **back arrow** to return

---

### 5. Timeline

**What it shows:**

- Chronological list of screen sessions
- Each entry shows: start time, duration, session type classification
- App usage within each session

**How to use:**

1. Tap the **Timeline** tab
2. Scroll through your session history
3. Each session shows the time, duration, and behavioral classification (micro, quick-check, normal, extended, doom-scroll, etc.)
4. Use this to understand your usage patterns through the day

---

### 6. Settings

**What it shows:**

- **Monitoring** — tracking toggle (pause/resume), permission grants, battery optimization
- **Security** — NumLock PIN setup/change/remove, uninstall protection toggle
- **Appearance** — theme selector (Auto, AMOLED Black, Monochrome, Pastel Analytics)
- **Data & Privacy** — total records, data age, retention policy, wipe all data
- **About** — version info and privacy statement

**How to use:**

1. Tap the **Settings** tab
2. **Pause tracking** — toggle the monitoring switch (if PIN is set, you must enter it to disable tracking)
3. **Set NumLock PIN** — see "NumLock PIN Security" section below
4. **Change theme** — select from 4 themes; changes apply immediately
5. **Wipe data** — permanently delete all collected data (requires PIN if set)
6. Review your data storage stats (total records, oldest data date)

---

### 7. Usage Heatmap

**What it shows:**

- 7-day × 24-hour color-coded grid of usage intensity
- Three overlay modes: Screen Time, DNS Queries, Privacy Exposure
- Summary stats: Weekly Total screen time, Peak Hour
- Hour-by-hour bar chart for the busiest day

**How to navigate here:**

- From the **Intelligence Console**, tap the **grid icon** in the top-right corner

**How to use:**

1. The default view shows **Screen Time** intensity — brighter cells = more usage
2. Tap the **overlay chips** at the top to switch between:
   - **Screen Time** — minutes of screen use per hour (blue scale)
   - **DNS Queries** — number of DNS queries per hour (green scale)
   - **Privacy** — privacy exposure level (red scale)
3. Read the grid: rows = days (last 7 days), columns = hours (0-23)
4. Check the **Summary Stats** cards for weekly total and peak hour
5. Scroll down to see the **Hour-by-Hour** bar chart for the busiest day
6. Tap the **refresh icon** in the top-right to reload data
7. Tap the **back arrow** to return to the Intelligence Console

**Color legend:**

- Dark/dim = low activity
- Bright/light = high activity
- The legend bar at the bottom shows the color gradient from Low to High

---

### 8. Focus Mode

**What it shows:**

- Three focus session types: Zen, Pomodoro, Deep Focus
- Active timer with countdown
- Interruption counter (how many times you picked up your phone during focus)
- Session history

**How to use:**

1. Navigate to Focus Mode from the Dashboard or Intelligence Console
2. Select a mode:
   - **Zen Mode** — open-ended focus timer, counts interruptions
   - **Pomodoro** — 25 minutes work, 5 minutes break, repeating cycles
   - **Deep Focus** — extended focus session with stricter interruption tracking
3. Tap **Start** to begin
4. Put your phone down — any screen unlock during the session counts as an interruption
5. Tap **Stop** when done
6. Review your focus session results

---

### 9. Timeline Replay

**What it shows:**

- Animated playback of your day's activity
- Visual timeline that scrolls through sessions
- Shows app transitions and session types

**How to use:**

1. Access from the Timeline screen or Dashboard
2. Tap play to watch an animated replay of your day
3. See how your usage unfolded hour by hour
4. Tap the **back arrow** to return

---

### 11. NumLock PIN Security

**What it does:**

- Protects destructive actions (data deletion, disable tracking) with a numeric PIN
- Optional Device Admin enrollment prevents app uninstall, force-stop, and clear data via Android system settings

**How to set up:**

1. Go to **Settings** → scroll to the **Security** section
2. Tap **Set PIN**
3. Enter a 4–8 digit numeric PIN on the keypad
4. Confirm the PIN by entering it again
5. PIN is now active — a lock icon appears in the Security section

**What is now protected (when PIN is set):**

- **Wipe All Data** (Settings > Data & Privacy) — requires PIN before the confirmation dialog appears
- **Clear All DNS Data** (DNS Settings) — requires PIN before the confirmation dialog appears
- **Disable Tracking** (Settings > Monitoring toggle) — requires PIN to turn off monitoring

**Uninstall Protection:**

1. After setting a PIN, toggle **Uninstall Protection** ON in the Security section
2. Accept the Android Device Administrator prompt
3. The app can no longer be uninstalled, force-stopped, or have its data cleared from Android system settings
4. To uninstall: first enter PIN → toggle off Uninstall Protection → then uninstall normally

**Changing or Removing PIN:**

- **Change** — tap "Change", enter current PIN first, then set new PIN
- **Remove** — tap "Remove", enter current PIN; this also auto-deactivates Device Admin

**Technical details:**

- PIN is stored as a SHA-256 hash (never plaintext)
- PIN verification happens locally with no network dependency
- Device Admin uses Android's `DevicePolicyManager` API

---

### 10. Blocklist Management

**What it shows:**

- List of active and available DNS blocklists
- Pre-configured community blocklists (20+ Pi-hole compatible lists)
- Custom URL input for adding your own blocklists
- Download status and domain counts for each list

**How to navigate here:**

- **Network** tab → **Settings** → **Manage Blocklists**

**How to use:**

1. Browse the **pre-configured lists** — includes popular Pi-hole community lists categorized as:
   - **Ads** — ad-serving domains
   - **Trackers** — analytics and tracking domains
   - **Malware** — known malware and phishing domains
   - **Social** — social media tracking domains
   - **Mixed** — multi-category blocklists
2. Tap a list to **enable/disable** it
3. Tap **Download** to fetch the latest version of a blocklist
4. To add a **custom blocklist**:
   - Tap the **+** button
   - Enter the URL of a hosts-file or domain-list format blocklist
   - Give it a name and category
   - Tap **Add**
5. The domain filter automatically applies enabled blocklists to DNS queries
6. Tap the **back arrow** to return to DNS Settings

**Supported formats:**

- Hosts file format (`0.0.0.0 domain.com` or `127.0.0.1 domain.com`)
- Domain list format (one domain per line)
- Adblock Plus format
- Comments and blank lines are automatically skipped

---

## Architecture

The app is built around a **4-engine system** managed by a central `EngineManager`:

```
┌─────────────────────────────────────────────────┐
│                 EngineManager                    │
│  (SupervisorJob, watchdog, crash recovery)       │
├─────────────┬───────────┬───────────┬───────────┤
│ Engine 1    │ Engine 2  │ Engine 3  │ Engine 4  │
│ ScreenState │ DnsFirewall│ Storage  │Intelligence│
│ (priority=10)│(priority=20)│(priority=30)│(priority=40)│
└──────┬──────┴─────┬─────┴─────┬─────┴─────┬─────┘
       │            │           │           │
       └────────────┴───────────┴───────────┘
                        │
              EngineEventBus (SharedFlow)
              extraBufferCapacity = 256
```

**Event Flow:**

1. `ScreenStateEngine` detects screen ON/OFF/unlock → publishes `ScreenOnEvent`, `ScreenOffEvent`, `UserUnlockedEvent`, `AppTransitionEvent`, `SessionClassifiedEvent`
2. `DnsFirewallEngine` intercepts DNS queries → publishes `DnsQueryProcessedEvent`, `DoHAttemptDetectedEvent`
3. `IntelligenceEngine` consumes all events → feeds scorers (`DAI`, `FSS`, `PES`), `PatternDetector`, and `CorrelationEngine`
4. `StorageEngine` handles data rollup, retention policies, and database maintenance

---

## Tech Stack

| Component    | Technology                                            |
| ------------ | ----------------------------------------------------- |
| Language     | Kotlin                                                |
| Architecture | 4-Engine System + MVVM + Clean Architecture           |
| UI           | Jetpack Compose + Material 3                          |
| Database     | Room (2 databases: screen intelligence + DNS)         |
| Background   | Foreground Service + WorkManager + BroadcastReceivers |
| Async        | Coroutines + Flow + SharedFlow EventBus               |
| Preferences  | DataStore (2 stores: app + DNS)                       |
| Build        | AGP 8.2.2, Gradle 8.5, JDK 17+, Compose BOM 2024.06.00 |
| Min SDK      | 26 (Android 8.0)                                      |
| Target SDK   | 34 (Android 14)                                       |

---

## Project Structure

```
app/src/main/java/com/mobileintelligence/app/
├── MobileIntelligenceApp.kt                    # Application entry point
├── analytics/
│   └── AnalyticsEngine.kt                      # Legacy behavioral AI & predictions
├── data/
│   ├── database/
│   │   ├── IntelligenceDatabase.kt              # Room DB: mobile_intelligence.db
│   │   ├── dao/                                 # Screen/app/summary DAOs
│   │   └── entity/                              # ScreenSession, AppSession, DailySummary, etc.
│   ├── preferences/
│   │   └── AppPreferences.kt                    # DataStore: app_preferences
│   └── repository/
│       └── IntelligenceRepository.kt            # Data access layer
├── dns/
│   ├── core/
│   │   ├── AppResolver.kt                       # UID→package mapping via /proc/net/udp
│   │   ├── DnsCache.kt                          # LRU DNS cache (10k entries, TTL-aware)
│   │   ├── DnsParser.kt                         # DNS packet parser
│   │   ├── DnsQueryLogger.kt                    # Persists DNS queries to Room
│   │   ├── PacketParser.kt                      # IP/UDP packet parser
│   │   └── UpstreamResolver.kt                  # Upstream DNS forwarder
│   ├── data/
│   │   ├── DnsDatabase.kt                       # Room DB: dns_firewall.db
│   │   ├── DnsPreferences.kt                    # DataStore: dns_preferences
│   │   ├── DnsRepository.kt                     # DNS data access layer
│   │   ├── dao/                                 # DnsQueryDao, DnsStatsDao, BlocklistDao
│   │   └── entity/                              # DnsQueryEntity, DnsDailyStats, BlocklistEntity
│   ├── filter/
│   │   ├── BlocklistManager.kt                  # Domain blocklist management
│   │   ├── BlocklistRepository.kt               # Blocklist CRUD, download, parsing
│   │   ├── PreConfiguredBlocklists.kt           # 20+ Pi-hole community list definitions
│   │   └── DomainFilter.kt                      # Domain matching engine
│   ├── receiver/                                # DnsBootReceiver, ConnectivityChange, etc.
│   ├── service/
│   │   └── LocalDnsVpnService.kt                # VPN-based DNS interception service
│   ├── ui/
│   │   ├── screens/                             # DnsProtection, QueryLog, Stats, Settings, Blocklist
│   │   └── viewmodel/                           # DNS UI ViewModels
│   └── worker/
│       ├── DnsCacheCleanupWorker.kt             # Periodic cache cleanup
│       └── DnsDailyStatsWorker.kt               # Daily aggregation (app+domain stats)
├── engine/
│   ├── Engine.kt                                # Engine interface
│   ├── EngineEventBus.kt                        # SharedFlow event bus + event types
│   ├── EngineManager.kt                         # Lifecycle, watchdog, crash recovery
│   ├── HealthMonitor.kt                         # Engine health diagnostics
│   ├── dns/
│   │   ├── DnsFirewallEngine.kt                 # Engine 2: DNS interception
│   │   ├── DoHDetector.kt                       # DNS-over-HTTPS bypass detection
│   │   ├── DomainCategoryEngine.kt              # Domain categorization
│   │   ├── RingBufferPool.kt                    # Buffer pool for packet processing
│   │   └── TieredBlocklistManager.kt            # Multi-tier blocklist system
│   ├── features/
│   │   ├── FocusMode.kt                         # Focus/Pomodoro timer engine
│   │   ├── SilentModeScheduler.kt               # DND scheduler
│   │   ├── SmartSuggestions.kt                   # AI-driven suggestions
│   │   └── TrackerSimulation.kt                 # Tracker visualization
│   ├── intelligence/
│   │   ├── IntelligenceEngine.kt                # Engine 4: scoring brain
│   │   ├── CorrelationEngine.kt                 # Screen+DNS cross-analysis
│   │   ├── DigitalAddictionIndex.kt             # DAI scorer
│   │   ├── FocusStabilityScore.kt               # FSS scorer
│   │   ├── PatternDetector.kt                   # Behavioral pattern detection
│   │   └── PrivacyExposureScore.kt              # PES scorer
│   ├── screen/
│   │   ├── ScreenStateEngine.kt                 # Engine 1: screen/app tracking
│   │   ├── DeviceStateTracker.kt                # Brightness, battery, charging
│   │   ├── SessionClassifier.kt                 # Session type classification
│   │   └── UnlockPatternAnalyzer.kt             # Compulsive unlock detection
│   └── storage/
│       ├── StorageEngine.kt                     # Engine 3: data lifecycle
│       ├── DataRollupManager.kt                 # Multi-level data rollup + purge
│       └── RetentionPolicy.kt                   # Configurable retention rules
├── receiver/
│   ├── AppProtectionAdmin.kt                    # Device Admin receiver (uninstall protection)
│   ├── BootReceiver.kt                          # Auto-start on boot
│   ├── MidnightReceiver.kt                      # Midnight rollover alarm
│   └── ScreenStateReceiver.kt                   # Screen ON/OFF events
├── service/
│   └── MonitoringService.kt                     # Foreground service with WakeLock
├── ui/
│   ├── MainActivity.kt                          # Main activity + Compose navigation
│   ├── components/
│   │   ├── Charts.kt                            # Reusable chart components
│   │   └── NumLockDialog.kt                     # Numeric PIN entry/verify dialog
│   ├── navigation/
│   │   └── Screen.kt                            # Navigation routes
│   ├── screens/
│   │   ├── DashboardScreen.kt                   # Main dashboard
│   │   ├── TimelineScreen.kt                    # Session timeline
│   │   ├── TimelineReplayScreen.kt              # Animated timeline replay
│   │   ├── AppStatsScreen.kt                    # App usage rankings
│   │   ├── InsightsScreen.kt                    # Behavioral insights
│   │   ├── IntelligenceConsoleScreen.kt         # Intelligence brain dashboard
│   │   ├── HeatmapScreen.kt                     # Activity heatmap
│   │   ├── FocusModeScreen.kt                   # Focus/Pomodoro UI
│   │   ├── ConsentScreen.kt                     # Privacy consent
│   │   └── SettingsScreen.kt                    # Settings & privacy
│   ├── theme/
│   │   └── Theme.kt                             # Material You themes
│   └── viewmodel/                               # MVVM ViewModels for each screen
├── util/
│   ├── DateUtils.kt                             # Thread-safe java.time utilities
│   └── UsageStatsHelper.kt                      # UsageStatsManager helper
└── worker/
    ├── MidnightRolloverWorker.kt                # Nightly summary generation
    ├── PeriodicCheckpointWorker.kt              # Service keepalive
    └── ServiceWatchdogWorker.kt                 # 15-min service health check
```

---

## Database Schema

### Intelligence Database (`mobile_intelligence.db`)

| Table               | Purpose                                                             |
| ------------------- | ------------------------------------------------------------------- |
| `screen_sessions` | Every screen ON/OFF event with duration, brightness, charging state |
| `app_sessions`    | Foreground app tracking per screen session                          |
| `daily_summary`   | Aggregated daily stats with behavioral scores                       |
| `hourly_summary`  | Per-hour activity breakdown                                         |
| `app_usage_daily` | Daily per-app usage aggregation                                     |
| `unlock_events`   | Every device unlock with timing and wake source                     |

### DNS Database (`dns_firewall.db`)

| Table                | Purpose                                                           |
| -------------------- | ----------------------------------------------------------------- |
| `dns_queries`      | Raw DNS query log with domain, app, blocked status, response time |
| `dns_daily_stats`  | Daily DNS aggregation (total, blocked, cached, by category)       |
| `dns_app_stats`    | Per-app daily DNS summary                                         |
| `dns_domain_stats` | Per-domain daily query counts and block status                    |
| `blocklists`       | Custom and pre-configured blocklist entries with download status  |

---

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Steps

1. **Open project** in Android Studio:

   ```
   File → Open → select the project root folder
   ```
2. **Sync Gradle** — Android Studio will auto-download dependencies
3. **Build debug APK:**

   ```bash
   # macOS/Linux
   ./gradlew assembleDebug

   # Windows
   .\gradlew.bat assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`
4. **Build release APK:**

   ```bash
   # macOS/Linux
   ./gradlew assembleRelease

   # Windows
   .\gradlew.bat assembleRelease
   ```
5. **Install on device:**

   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Required Permissions (Grant after install)

1. **Usage Access** — Settings → Apps → Special access → Usage access → Enable for Mobile Intelligence
2. **Notification Permission** — Prompted on first launch (Android 13+)
3. **Battery Optimization** — Auto-requested; confirm "Unrestricted" for 24/7 operation
4. **VPN Permission** — Prompted when enabling DNS Firewall (for DNS-level ad blocking)

---

## Themes

| Theme            | Description                                |
| ---------------- | ------------------------------------------ |
| Auto             | Dynamic Material You colors from wallpaper |
| AMOLED Black     | Pure black background for OLED screens     |
| Monochrome       | Grayscale minimal design                   |
| Pastel Analytics | Soft purple analytics theme                |

---

## Performance Targets

- APK size: < 20MB
- RAM usage: < 120MB
- Battery impact: < 2% per day (event-driven, no polling when screen off)
- Wake lock: 10-minute timeout with auto-renewal

---

## Privacy

- **No third-party analytics** — no analytics SDKs or telemetry endpoints are used
- **Network use is feature-scoped** — internet access is used for DNS resolution and optional blocklist updates when DNS protection is enabled
- **All data local** — stored in Room databases on device only
- **User controls** — pause tracking, wipe history, configure retention from Settings
- **PIN protection** — optional NumLock PIN prevents unauthorized data deletion or app removal
- **Transparent** — persistent notification shows monitoring status
- **DNS Firewall** — blocks trackers and ads at the DNS level before they can load

---

## Open Source

- [Development Guide](DEVELOPMENT.md)
- [Permission Rationale](PERMISSIONS.md)
- [Privacy Policy](PRIVACY.md)
- [Security Policy](SECURITY.md)
- [Changelog](CHANGELOG.md)

---

## Community

- [Contributing Guide](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- Use GitHub Issues and Pull Requests for feature and bug collaboration.

---

## Security Reporting

Please report vulnerabilities privately according to [SECURITY.md](SECURITY.md). Do not open public issues for security-sensitive reports.

---

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

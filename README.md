# Wifi Monitor

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](#)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![API](https://img.shields.io/badge/minSdk-26%20%7C%20targetSdk-35-555555)](#)
[![Version](https://img.shields.io/badge/version-1.0-0D6742)](#)

A native Android client for **Tenda home routers**. Wifi Monitor talks to the router over the local network, shows who is connected, alerts you when an unknown device joins, and lets you block, time-limit, and throttle devices from your phone.

It is **not** an official Tenda product. Credentials stay on the device; the app never sends router data to a cloud backend.

---

## Screenshots

<p align="center">
  <img width="30%" src="https://github.com/user-attachments/assets/7f718d6e-d329-4eda-8829-6abf5ab06339" />
  <img width="30%" src="https://github.com/user-attachments/assets/e3460ca0-d6b2-4f84-bc82-b6a6198655a6" />
  <img width="30%" src="https://github.com/user-attachments/assets/2406f6ec-b9ae-4d19-88a5-a0d146ca9a6e" />
  <img width="30%" src="https://github.com/user-attachments/assets/0c7a923b-69d0-420d-9b8d-52cbd790409c" />
  <img width="30%" src="https://github.com/user-attachments/assets/f5afd230-6317-4d81-a43a-fa63365955a4" />

https://github.com/user-attachments/assets/00a7d861-61c5-43a8-a4c1-27eaf0054cc4


</p>

---

## Features

### Live dashboard
- Online, unknown, and known-device counts, plus WAN IP, uptime, and traffic indicators
- Filter by online / all / unknown; search by name, IP, MAC, or manufacturer
- Pull-to-refresh and a Glance home-screen widget for the online-device count

### Intruder detection
- Foreground scan service (default **30 s**, configurable 15 s / 1 min / 5 min)
- High-priority notifications when an unrecognized MAC appears
- Optional **auto-block** of unknown devices on the router
- WorkManager backup scan when the foreground service is not running; monitoring resumes after reboot

### Device control
- Whitelist, rename, and custom icons
- Immediate internet block / unblock via router QoS
- Timed blocks with dual recovery (`AlarmManager` + WorkManager)
- Per-device download / upload limits on the Bandwidth tab
- Merge identities when a device rotates its MAC (known-MAC fingerprinting)

### Router status
- WAN type, IP, gateway, DNS, firmware, uptime, and live WAN speeds while the Status tab is open

### History
- Connection and disconnection sessions stored locally in Room, grouped per device

### Reliability
- Multiple Tenda login strategies to handle firmware differences
- Automatic re-login when the router drops the admin session
- ARP-table fallback if the router API is unavailable
- Manufacturer lookup from an onboard OUI database; device type inferred from hostname / vendor

### Appearance
- Dark and light themes (Material 3), optional Material You dynamic color on Android 12+
- Contrast-tuned semantic colors across both palettes

---

## How it works

```
Phone (Wifi Monitor)  ──LAN──►  Tenda router admin API  (default http://192.168.0.1)
         │
         ├─ EncryptedSharedPreferences  router password (AES-256, Android Keystore)
         ├─ DataStore                   URL, scan interval, alerts, theme
         └─ Room                        devices, sessions, timed blocks, known MACs
```

1. **Setup** — enter the router admin password (optional custom base URL). On success, monitoring starts.
2. **Scan** — `NetworkScanService` polls the router, reconciles the live list with Room, and records joins / leaves.
3. **Alert** — new unknown devices raise notifications; auto-block can cut internet immediately.
4. **Control** — block, timed block, and bandwidth edits are applied through the router’s QoS / access APIs and mirrored in Room.
5. **Fallback** — if the goform API fails, the app reads `/proc/net/arp` so the dashboard still reflects the LAN.

---

## Screens

| Screen | Purpose |
| --- | --- |
| Setup | First-run login to the router |
| Home | Live device list, stats, search, filters |
| Status | Full WAN / LAN / firmware snapshot |
| Bandwidth | Per-device throughput and limits |
| History | Session log |
| Device detail | Identity, trust, block, timed block, merge |
| Settings | Router URL/password, scan interval, auto-block, alerts, theme |

---

## Tech stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin 2.0, Jetpack Compose, Material 3 |
| Architecture | MVVM, Hilt, single `DeviceRepository` orchestration layer |
| Persistence | Room 2.6 (schema v6), DataStore, EncryptedSharedPreferences |
| Networking | OkHttp to Tenda `goform` endpoints |
| Background | Foreground service, WorkManager, `AlarmManager`, boot receiver |
| Widget | Jetpack Glance |
| Tests | JUnit + MockK (scan reconciliation, MAC matching, Room migrations) |

**Requirements:** Android 8.0 (API 26)+ · JDK 17 · a Tenda router on the same LAN (F3-style web admin / QoS APIs).

---

## Getting started

### Build and run

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Open the project in Android Studio, connect a device on the same Wi-Fi as the router, and run the `app` configuration.

### First launch

1. Confirm the phone is on the router’s LAN.
2. Enter the **router admin password** (the same one used at `192.168.0.1` in a browser).
3. Change the router URL in Setup → Advanced if your gateway is not `http://192.168.0.1`.
4. Grant notification permission so unknown-device alerts can fire.
5. Optionally disable battery optimization so background scanning stays reliable.

Release builds use R8 minify and resource shrinking (`./gradlew :app:assembleRelease`).

---

## Project layout

```
app/src/main/java/com/tenda/wifimonitor/
├── MainActivity.kt / WifiMonitorApp.kt
├── ui/                 Compose screens, navigation, theme
├── data/
│   ├── db/             Room entities, DAOs, migrations
│   ├── network/        TendaApiService, ArpScanner, OUI, MAC fingerprint
│   └── repository/     Scan diff, block, QoS, timed-block coordination
├── domain/model/
├── service/            Scan service, workers, receivers, notifications
├── widget/             Online-count Glance widget
└── di/                 Hilt modules
```

---

## Privacy and security

- Router password is stored with **EncryptedSharedPreferences** (AES-256-SIV / AES-256-GCM, Keystore-backed master key). Other preferences use DataStore.
- Traffic is **LAN-only** to the router. The app enables cleartext HTTP because typical Tenda admin UIs are `http://`, not HTTPS.
- This project is for administering **your own** router. Do not point it at networks you do not control.

---

## License

Personal / portfolio project. Not affiliated with Tenda Technology Co., Ltd.


Uploading SVID_20261007_022616_1 (2) (1).mp4…


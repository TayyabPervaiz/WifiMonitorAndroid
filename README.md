# WiFi Monitor 📶

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%7C%20Clean-FF9900)

A native Android application built entirely with **Kotlin** and **Jetpack Compose** that interfaces with Tenda routers to provide real-time network monitoring, instant intruder alerts, and a detailed device connection history. 

This app solves the problem of routers silently allowing unknown devices to join your network. By running a resilient foreground service, it constantly polls your router (with ARP fallback capabilities) to ensure your home network remains secure.

*(Insert a GIF or Screenshot of your Dashboard here)*

---

## ✨ Features

- **Real-Time Network Dashboard:** See exactly who is connected to your WiFi at a glance with beautiful, auto-updating Compose UI.
- **Instant Intruder Alerts:** A robust foreground service (`NetworkScanService`) monitors the network even when the app is closed. If an unknown device joins, a high-priority Android Notification is fired instantly.
- **Intelligent Device Parsing:** Determines device types (Phone, Laptop, TV, IoT) and manufacturers automatically by comparing MAC addresses against an onboard OUI database.
- **Connection History & Analytics:** Every connection and disconnection is logged to a local Room Database. The UI groups sessions logically by device in a clean, accordion-style interface.
- **Offline / ARP Fallback Mode:** Overcomes router API limitations and session expiries by automatically falling back to ARP table scanning if the router API is unresponsive.
- **Material You Design:** Built ground-up with a premium dark-themed interface, micro-animations, and support for Android 12+ dynamic colors.

## 🛠 Tech Stack & Architecture

This project was built following **Clean Architecture** principles and modern Android best practices:

- **UI:** Jetpack Compose, Material Design 3, Coroutines Flow collection.
- **Architecture:** MVVM (Model-View-ViewModel).
- **Dependency Injection:** Dagger-Hilt.
- **Local Persistence:** Room Database (Daos, Entities, relations).
- **Network / API:** Ktor/Retrofit (for REST API calls to the router) & Raw UDP/ARP socket reading.
- **Background Work:** Foreground Services (`ServiceCompat`), WorkManager, and structured concurrency with Coroutines.

## 🚀 How it Works

1. **Authentication:** The app securely encrypts your router admin password (Base64/MD5 depending on firmware) to request a temporary session cookie.
2. **Polling:** The `DeviceRepository` queries the router's `/goform/getWpsWdsOk` (or similar endpoints) every 30 seconds.
3. **Diffing Engine:** The repository compares the live network state against the cached `Room` database state to detect *Departed* or *New* devices.
4. **Self-Healing Sessions:** Tenda routers notoriously drop sessions silently. The background service proactively manages re-logins and handles HTML-redirect errors to ensure 24/7 uptime.

## 📱 Screenshots

<img width="324" height="709" alt="image" src="https://github.com/user-attachments/assets/a767756a-972c-4fd9-bf24-a08564eaaa93" /> <img width="330" height="504" alt="image" src="https://github.com/user-attachments/assets/92e7484c-7d49-456c-95e8-24a2d93260f5" />
<img width="344" height="696" alt="image" src="https://github.com/user-attachments/assets/c190e1fe-f58c-40db-9567-bea26044328d" />




## 🤝 Let's Connect
I built this project to deepen my understanding of Android system services, complex Compose layouts, and reactive data streams. Feel free to reach out if you have any questions or feedback!

- [LinkedIn Profile](https://www.linkedin.com/)
- [Portfolio](#)

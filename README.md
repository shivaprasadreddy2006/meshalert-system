# Mesh-based Alerting System with Localization Support

> **Hackathon Prototype**  
> **Team:** The Inevitables  
> **Stack:** Android Native App (BLE 5.0, Bluetooth SIG Mesh Proxy & GATT Relay Server, Background Foreground Service)

---

## 1. System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                  BLE Mesh Network / Nodes                    │
│  - BLE 5.0 Mesh Nodes & Beacons                              │
│  - SIG Mesh Proxy Service (UUID: 0x1828)                     │
│  - Alert Broadcast & Localization Pointers                   │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               │  Bluetooth SIG Mesh Proxy (GATT)
                               ▼
┌──────────────────────────────────────────────────────────────┐
│             Android BLE Helper App (BLEHelper)               │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                    Core Mesh Engine                    │  │
│  │  - `BleMeshManager`: SIG Proxy Client & Scanner        │  │
│  │  - `BleMeshServerManager`: GATT Server & Mesh Node Adv │  │
│  │  - `MeshPacket`: PDU Serialization & SAR Segmentation  │  │
│  │  - `BleDiagnosticsHelper`: Link Metrics & Hardware Adv │  │
│  └───────────────────────────┬────────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────▼────────────────────────────┐  │
│  │              Background Service Layer                  │  │
│  │  - `BleMeshBackgroundService`: Foreground Service      │  │
│  │  - Partial WakeLock & Sticky Link Maintenance          │  │
│  └───────────────────────────┬────────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────▼────────────────────────────┐  │
│  │                   UI & Presentation                    │  │
│  │  - `FirstFragment`: Live Telemetry & Alert Stream      │  │
│  │  - `SecondFragment`: BLE Device Scanner & Node Server  │  │
│  │  - Hardware Capabilities & Permission Diagnostics      │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Message Format & Mesh Protocol

The system utilizes Bluetooth SIG standard Mesh Proxy PDUs combined with custom alert opcodes for emergency broadcasts and localization data.

### Bluetooth SIG Assigned UUIDs:
- **Mesh Proxy Service**: `00001828-0000-1000-8000-00805f9b34fb` (`0x1828`)
- **Mesh Proxy Data In**: `00002ade-0000-1000-8000-00805f9b34fb` (`0x2ADE` - Write Without Response)
- **Mesh Proxy Data Out**: `00002adf-0000-1000-8000-00805f9b34fb` (`0x2ADF` - Notify)
- **Mesh Provisioning Service**: `00001827-0000-1000-8000-00805f9b34fb` (`0x1827`)

### Mesh Alert Packet Protocol:
- **Opcode**: `0xA1` (`OPCODE_MESH_ALERT`)
- **Alert Levels**:
  - `0x01`: `INFO`
  - `0x02`: `WARN`
  - `0x03`: `EMERGENCY`

### Sample Alert Data Payload:
```json
{
  "type": "ALERT",
  "alertType": "FIRE",
  "priority": "CRITICAL",
  "message": "Fire detected on Floor 1. Move towards North Exit.",
  "area": "Floor 1",
  "alertId": 101,
  "sender": "Mesh Node 0x05E3",
  "timestamp": "2026-08-23T07:00:00+05:30"
}
```

---

## 3. Android Architecture & Implementation (`BLEHelper`)

The native Android application is engineered to operate seamlessly as both a **Mesh Proxy Client** and a **GATT Advertising Mesh Server**.

### Key Modules:
1. **`BleMeshManager`**:
   - Manages scanning with `ScanFilter` targeting Mesh Proxy Service (`0x1828`).
   - Handles GATT connection lifecycle, MTU negotiation (up to 517 bytes), and CCCD descriptor notifications (`0x2902`).
   - Performs periodic RSSI polling for link quality diagnostics.
2. **`BleMeshServerManager`**:
   - Implements local GATT server advertising capabilities.
   - Allows an Android phone to act as an active mesh relay node broadcasting custom emergency alerts.
3. **`BleMeshBackgroundService`**:
   - Android Foreground Service with persistent notification (`foregroundServiceType="connectedDevice"`).
   - Holds a `WakeLock` to prevent OS CPU throttling and radio sleep during critical emergency scenarios.
4. **`BleDiagnosticsHelper`**:
   - Queries hardware capabilities (LE 2M PHY, Coded PHY, Extended Advertising, Offloaded Batching).
   - Manages modern Android 12+ (API 31–36) granular Bluetooth permissions.

### Sample Code — Alert Transmission via GATT Client:
```java
// Broadcast an emergency alert to connected BLE Mesh Proxy node
BleMeshManager meshManager = BleMeshManager.getInstance(context);
meshManager.sendMeshAlert(
    101,                                  // Alert ID
    BleConstants.ALERT_LEVEL_EMERGENCY,   // Emergency Level (0x03)
    "FIRE: Evacuate via North Exit"       // Alert Message
);
```

---

## 4. Step-by-Step Build & Setup Instructions

### Prerequisites:
- **Android Studio** (Ladybug / Iguana or later recommended).
- Physical Android device running **Android 12 (API 31) to Android 15/16 (API 36)** with Bluetooth Low Energy support.

### Step 1: Open Project
1. Open Android Studio.
2. Select **Open** and choose the `BLEHelper` directory:
   ```bash
   # Path to Android project
   meshalert-system/BLEHelper
   ```

### Step 2: Sync Gradle & Build
1. Allow Android Studio to sync Gradle files.
2. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

### Step 3: Run on Device
1. Connect your Android device via USB with Developer Mode & USB Debugging enabled.
2. Click **Run 'app'** (`Shift + F10`).
3. When prompted on the device, grant the required permissions:
   - **Nearby Devices / Bluetooth permissions** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`)
   - **Location permissions** (`ACCESS_FINE_LOCATION`)
   - **Notifications** (`POST_NOTIFICATIONS`)

---

## 5. Project Directory Structure

```
meshalert-system/
└── BLEHelper/                                    # Native Android BLE Mesh Application
    ├── app/
    │   ├── src/
    │   │   ├── main/
    │   │   │   ├── java/com/inevitables/blehelper/
    │   │   │   │   ├── mesh/                     # Bluetooth SIG Mesh Engine
    │   │   │   │   │   ├── BleConstants.java     # SIG UUIDs (0x1828, 0x2ADE), Opcodes
    │   │   │   │   │   ├── BleMeshManager.java   # Central BLE scanner & GATT client
    │   │   │   │   │   ├── BleMeshServerManager.java # GATT server & advertiser node
    │   │   │   │   │   ├── BleDiagnosticsHelper.java # Link quality, RSSI & HW info
    │   │   │   │   │   ├── DiscoveredBleDevice.java  # Scanned device model
    │   │   │   │   │   └── MeshPacket.java       # PDU parsing & SAR segmentation
    │   │   │   │   ├── net/
    │   │   │   │   │   └── WebBridgeManager.java # Network & Bridge helpers
    │   │   │   │   ├── service/
    │   │   │   │   │   └── BleMeshBackgroundService.java # Foreground Service & WakeLock
    │   │   │   │   ├── ui/                       # RecyclerView adapters & UI binders
    │   │   │   │   │   ├── DeviceAdapter.java    # Discovered devices list adapter
    │   │   │   │   │   └── LogAdapter.java       # Real-time event log adapter
    │   │   │   │   ├── MainActivity.java         # App shell & permission handling
    │   │   │   │   ├── FirstFragment.java        # Live monitor & alert trigger UI
    │   │   │   │   └── SecondFragment.java       # BLE scanner & diagnostic dashboard
    │   │   │   ├── res/                          # Layouts, drawables, menus & themes
    │   │   │   └── AndroidManifest.xml           # BLE features & background permissions
    │   │   └── androidTest/                      # Android instrumentation tests
    │   ├── build.gradle.kts                      # Module build configuration (SDK 36)
    │   └── proguard-rules.pro
    ├── gradle/                                   # Gradle wrapper
    ├── build.gradle.kts                          # Top-level build configuration
    ├── settings.gradle.kts                       # Module settings
    └── gradle.properties
```

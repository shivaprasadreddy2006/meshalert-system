# Mesh-based Alerting System with Localization Support

> **Hackathon Prototype**  
> **Team:** The Inevitables  
> **Stack:** Android (BLE & TCP Client) $\rightarrow$ Node.js (TCP & Socket.IO Server) $\rightarrow$ React Frontend

---

## 1. System Architecture

```
┌────────────────────────────────────────┐
│          Android Native App            │  (Teammate's Responsibility)
│  - BLE Scanning, TX/RX, Mesh Relays    │
│  - Connects to Backend via TCP Socket  │
└──────────────────┬─────────────────────┘
                   │
                   │  Raw Local TCP Socket (Port 7000)
                   ▼
┌────────────────────────────────────────┐
│          Node.js Backend               │  (Our Bridge Responsibility)
│  - Native TCP Server (`net` module)    │
│  - Validates incoming JSON stream      │
│  - In-memory state manager             │
│  - Socket.IO Real-time Broadcaster     │
└──────────────────┬─────────────────────┘
                   │
                   │  Socket.IO (Port 5000)
                   ▼
┌────────────────────────────────────────┐
│           React Frontend               │  (Our UI Responsibility)
│  ├── Role Selection (/)                │
│  ├── Client Dashboard (/client)        │
│  └── Admin Dashboard (/admin)          │
└────────────────────────────────────────┘
```

---

## 2. TCP Message Format (Android $\rightarrow$ Node.js)

The Android application sends JSON packets over the raw TCP socket on **port 7000** (newline-terminated recommended).

### Sample Emergency Alert JSON Payload:
```json
{
  "type": "ALERT",
  "alertType": "FIRE",
  "priority": "HIGH",
  "message": "Fire detected on Floor 1. Move towards the North Exit. Do not use elevators.",
  "area": "Floor 1",
  "timestamp": "2026-08-22T18:42:10+05:30"
}
```

### Supported Alert Types & Priorities:
- **`alertType`**: `FIRE`, `STAMPEDE`, `MEDICAL`, `EVACUATION`, `GENERAL`
- **`priority`**: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

---

## 3. How Android Connects & Sends Messages (Kotlin / Java)

In your teammate's native Android app:

```kotlin
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackendTcpClient(private val hostIp: String, private val port: Int = 7000) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    suspend fun connectAndSend(alertJson: JSONObject) = withContext(Dispatchers.IO) {
        try {
            // 1. Connect TCP Socket
            socket = Socket(hostIp, port)
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream()), true)
            
            // 2. Send Alert Payload with newline
            writer?.println(alertJson.toString())
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

---

## 4. How to Test Without an Android Device

We have included two ways to test:

### Option A: CLI TCP Simulation Script
Run the built-in Node.js TCP test client to simulate an Android phone connecting, transmitting an alert, and disconnecting:
```bash
cd server
node test-tcp-client.js
```
*(Watch the React dashboard immediately flip to 🟢 Connected, display the alert, and then flip to 🔴 Disconnected when closed!)*

### Option B: Built-in Admin Dashboard Test Simulator
1. Open `http://localhost:5173`.
2. Click **Admin**.
3. Use the **DEV / TEST MODE** card at the bottom to dispatch custom alerts into the pipeline.

---

## 5. Step-by-Step Local Run Instructions

### Step 1: Install Dependencies
Open a terminal in the project root:

```bash
# Install Server Dependencies
cd server
npm install

# Install Client Dependencies
cd ../client
npm install
```

### Step 2: Start the Backend Server
```bash
cd server
npm run dev
```
*Output will display:*
```text
=============================================================
🚀 MESH ALERT SYSTEM — TCP TO WEBSOCKET BRIDGE SERVER
   Team: The Inevitables
=============================================================
📡 Web / Socket.IO URL:    http://localhost:5000
📱 Android TCP Server:     10.9.0.179:7000
👉 In Android Native App, connect raw TCP socket to:
   Host: "10.9.0.179" | Port: 7000
=============================================================
```

### Step 3: Start the React Frontend (in a second terminal)
```bash
cd client
npm run dev
```
Open **`http://localhost:5173`** in your browser.

---

## 6. Project Directory Structure

```
C:\MAS\
├── client/
│   ├── src/
│   │   ├── components/
│   │   │   ├── Navbar.jsx          # Brand & Role Switcher
│   │   │   ├── StatusBadge.jsx     # Green/Red Connection Indicators
│   │   │   └── AlertCard.jsx       # Structured Emergency Alert Box
│   │   ├── pages/
│   │   │   ├── RoleSelect.jsx      # Role Picker ("Client" or "Admin")
│   │   │   ├── ClientDashboard.jsx # Client Read-Only Monitor
│   │   │   └── AdminDashboard.jsx  # Admin Monitor & Dev Test Tool
│   │   ├── services/
│   │   │   └── socket.js           # Real-time Socket.IO connection
│   │   ├── App.jsx                 # Main Application Router
│   │   ├── main.jsx
│   │   └── index.css
│   └── package.json
│
├── server/
│   ├── src/
│   │   ├── tcp/
│   │   │   └── tcpServer.js        # Native Node.js net TCP Server (Port 7000)
│   │   ├── socket/
│   │   │   └── socketServer.js     # Socket.IO Broadcaster (Port 5000)
│   │   ├── services/
│   │   │   └── stateService.js     # In-memory State Management
│   │   ├── routes/
│   │   │   └── testRoutes.js       # Status & Test Simulation endpoints
│   │   └── server.js               # Main Entry Point
│   ├── test-tcp-client.js          # CLI Script to simulate Android TCP
│   └── package.json
│
└── README.md
```

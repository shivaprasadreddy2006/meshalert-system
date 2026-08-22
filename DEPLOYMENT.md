# Deployment Guide — Mesh Alert System

This project consists of:
1. **Node.js Backend**: Native TCP Socket Server (**Port 7000**) + Express API & Socket.IO Broadcaster (**Port 5000**).
2. **React Frontend**: Mobile-optimized emergency alert monitor & control center with Web Audio alarm.
3. **Android Native App**: Connects to the backend via TCP socket on Port 7000.

Because the system requires a **persistent raw TCP socket (Port 7000)** for Android communication and persistent **WebSockets (Port 5000)**, the recommended deployment methods are outlined below.

---

## 🚀 Option 1: Unified Single-Server Deployment (Recommended)

The Node.js server is configured to serve the compiled React web application directly from `client/dist`. One server handles everything!

### Steps:
```bash
# 1. Build the React frontend
npm run build

# 2. Start the unified production server
npm start
```
- Open `http://localhost:5000` (or `http://YOUR_SERVER_IP:5000`) in any desktop or mobile browser.
- In the Android app, point the server IP to `YOUR_SERVER_IP` and TCP port to `7000`.

---

## 🌐 Option 2: Free Cloud Container Hosting

### A. Deploying to Railway (Supports WebSockets + Custom TCP Ports)
1. Push your project to a GitHub repository:
   ```bash
   git init
   git add .
   git commit -m "Production release"
   git branch -M main
   git remote add origin <YOUR_GITHUB_REPO_URL>
   git push -u origin main
   ```
2. Go to [railway.app](https://railway.app) and sign in with GitHub.
3. Click **New Project** $\rightarrow$ **Deploy from GitHub repo** $\rightarrow$ Select your repo.
4. Railway will automatically detect the `Dockerfile` and build both the frontend and backend.
5. In **Service Settings** $\rightarrow$ **Networking**, generate a public domain for HTTP (Port 5000) and add a TCP Proxy for Port 7000.

### B. Deploying to Render
1. Go to [render.com](https://render.com) and click **New Web Service**.
2. Connect your GitHub repository.
3. Choose **Docker** as the Runtime environment.
4. Set Environment Variables:
   - `PORT` = `5000`
   - `TCP_PORT` = `7000`
5. Click **Create Web Service**.

### C. Deploying to Fly.io
```bash
# Install flyctl
flyctl auth login

# Launch container
flyctl launch
flyctl deploy
```

---

## ⚡ Option 3: Instant Live Hackathon Demo (Cloudflare Tunnel / Ngrok)

If you are running the system on your laptop during a hackathon and want your teammates' Android phones or judges to access it over cellular data / Wi-Fi:

### Using Cloudflare Tunnel (100% Free, No Account Needed):
```bash
# In terminal 1: Start your server
npm start

# In terminal 2: Expose HTTP/WebSockets to a public HTTPS URL
npx cloudflared tunnel --url http://localhost:5000
```
- Cloudflare will print a live public HTTPS URL (e.g. `https://xxxx.trycloudflare.com`) that anyone can open on their phones!

### Exposing TCP Port 7000 for Android Phones via Ngrok:
```bash
ngrok tcp 7000
```
- Ngrok will give a public TCP address (e.g. `tcp://0.tcp.ngrok.io:12345`). In the Android app, enter `0.tcp.ngrok.io` as Host and `12345` as Port.

---

## 🐳 Option 4: Docker & Docker Compose (Any VPS)

To deploy on DigitalOcean, AWS EC2, Linode, or any Linux VPS:

```bash
# Build and run with Docker Compose
docker-compose up -d --build
```
- The container starts automatically and exposes ports `5000` (Web UI) and `7000` (Android TCP).

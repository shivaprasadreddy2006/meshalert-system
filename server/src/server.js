const express = require('express');
const http = require('http');
const cors = require('cors');
const dotenv = require('dotenv');
const os = require('os');
const path = require('path');
const fs = require('fs');

dotenv.config();

const { initTcpServer, setTargetHost } = require('./tcp/tcpServer');
const { initSocketServer } = require('./socket/socketServer');
const stateService = require('./services/stateService');
const testRoutes = require('./routes/testRoutes');

const app = express();
const httpServer = http.createServer(app);

// Enable trust proxy so req.ip and X-Forwarded-For work correctly behind Railway / Cloudflare / Reverse Proxies
app.set('trust proxy', true);

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json());

// Helper: Extract clean client IP
function getClientIp(req) {
  let ip = req.headers['cf-connecting-ip'] ||
           req.headers['x-real-ip'] ||
           req.headers['x-client-ip'] ||
           req.headers['x-forwarded-for'] ||
           req.ip ||
           req.socket?.remoteAddress;

  if (ip && typeof ip === 'string') {
    if (ip.includes(',')) {
      ip = ip.split(',')[0].trim();
    }
    if (ip.startsWith('::ffff:')) {
      ip = ip.replace('::ffff:', '');
    }
  }
  return ip || '127.0.0.1';
}

// Global IP detection & auto-bind middleware: ALWAYS target the accessing device's IP!
app.use((req, res, next) => {
  const clientIp = getClientIp(req);
  if (clientIp && clientIp !== '127.0.0.1' && clientIp !== '::1') {
    stateService.setDetectedClientIp(clientIp);
    setTargetHost(clientIp, 7000);
  }
  next();
});

// API Routes
app.use('/api', testRoutes);

// Static Client Serving (Single-Server Unified Deployment)
const possiblePaths = [
  path.join(__dirname, '../../client/dist'),
  path.resolve('/app/client/dist'),
  path.join(__dirname, '../public'),
  path.join(__dirname, '../../dist')
];

let staticDir = null;
for (const p of possiblePaths) {
  if (fs.existsSync(p)) {
    staticDir = p;
    break;
  }
}

if (staticDir) {
  console.log(`📦 [STATIC] Serving React production build from: ${staticDir}`);
  app.use(express.static(staticDir));
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api') || req.path.startsWith('/socket.io')) {
      return next();
    }
    res.sendFile(path.join(staticDir, 'index.html'));
  });
} else {
  app.get('/', (req, res) => {
    const clientIp = getClientIp(req);
    res.json({
      name: 'Mesh-based Alerting System Bridge (Team: The Inevitables)',
      version: '1.0.0',
      deployment: 'Railway / Cloud Container',
      detectedClientIp: clientIp,
      targetAndroidDevice: stateService.getState().targetDeviceIp,
      tcpStatus: stateService.getState().androidConnected ? 'CONNECTED' : 'CONNECTING',
      httpStatus: 'OPERATIONAL'
    });
  });
}

// Helper: Get local network IP addresses
function getLocalIPs() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        addresses.push(iface.address);
      }
    }
  }
  return addresses;
}

// Configurable Ports — uses Railway's dynamic PORT (8080 or assigned)
const HTTP_PORT = process.env.PORT || 5000;
const TCP_PORT = parseInt(process.env.TCP_PORT, 10) || 7000;
const ANDROID_HOST = (process.env.ANDROID_HOST && process.env.ANDROID_HOST !== '127.0.0.1') ? process.env.ANDROID_HOST : null;

// Initialize Socket.IO
initSocketServer(httpServer);

// Start HTTP Server & Native TCP Client
httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
  const localIPs = getLocalIPs();
  const primaryIP = localIPs[0] || '127.0.0.1';

  // Initialize TCP client
  initTcpServer(TCP_PORT, ANDROID_HOST);

  console.log(`\n=============================================================`);
  console.log(`🚀 MESH ALERT SYSTEM — UNIFIED PRODUCTION SERVER (RAILWAY)`);
  console.log(`   Team: The Inevitables`);
  console.log(`=============================================================`);
  console.log(`📡 Web UI & Socket.IO URL: http://${primaryIP}:${HTTP_PORT}`);
  console.log(`📱 TCP Client Target:      Dynamic (Binds to accessing device IP)`);
  console.log(`=============================================================\n`);
});

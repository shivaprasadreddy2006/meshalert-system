const express = require('express');
const http = require('http');
const cors = require('cors');
const dotenv = require('dotenv');
const os = require('os');
const path = require('path');
const fs = require('fs');

dotenv.config();

const { initSocketServer } = require('./socket/socketServer');
const stateService = require('./services/stateService');
const testRoutes = require('./routes/testRoutes');

const app = express();
const httpServer = http.createServer(app);

// Trust Railway / Cloudflare reverse proxy headers
app.set('trust proxy', true);

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json());

// API Routes
app.use('/api', testRoutes);

// Static Client Serving
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
    if (req.path.startsWith('/api') || req.path.startsWith('/socket.io')) return next();
    res.sendFile(path.join(staticDir, 'index.html'));
  });
} else {
  app.get('/', (req, res) => {
    res.json({
      name: 'Mesh-based Alerting System Bridge (Team: The Inevitables)',
      version: '1.0.0',
      deployment: 'Railway / Cloud Container',
      status: 'OPERATIONAL',
      alertEndpoint: 'POST /api/alert'
    });
  });
}

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

const HTTP_PORT = process.env.PORT || 5000;

// Initialize Socket.IO
initSocketServer(httpServer);

// Start HTTP Server
httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
  const localIPs = getLocalIPs();
  const primaryIP = localIPs[0] || '127.0.0.1';

  console.log(`\n=============================================================`);
  console.log(`🚀 MESH ALERT SYSTEM — UNIFIED PRODUCTION SERVER (RAILWAY)`);
  console.log(`   Team: The Inevitables`);
  console.log(`=============================================================`);
  console.log(`📡 Web UI & Socket.IO URL:  http://${primaryIP}:${HTTP_PORT}`);
  console.log(`📱 Android Bridge:          POST /api/alert`);
  console.log(`💡 Architecture:            Android → HTTPS POST → Railway → Socket.IO → Browser`);
  console.log(`=============================================================\n`);
});

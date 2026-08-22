const express = require('express');
const http = require('http');
const cors = require('cors');
const dotenv = require('dotenv');
const os = require('os');
const path = require('path');
const fs = require('fs');

dotenv.config();

const { initTcpServer } = require('./tcp/tcpServer');
const { initSocketServer } = require('./socket/socketServer');
const testRoutes = require('./routes/testRoutes');

const app = express();
const httpServer = http.createServer(app);

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json());

// API Routes
app.use('/api', testRoutes);

// Static Client Serving (Single-Server Unified Deployment)
const clientDistPath = path.join(__dirname, '../../client/dist');
const altClientDistPath = path.join(__dirname, '../public');

let staticDir = null;
if (fs.existsSync(clientDistPath)) {
  staticDir = clientDistPath;
} else if (fs.existsSync(altClientDistPath)) {
  staticDir = altClientDistPath;
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
    res.json({
      name: 'Mesh-based Alerting System Bridge (Team: The Inevitables)',
      version: '1.0.0',
      tcpStatus: 'LISTENING',
      httpStatus: 'OPERATIONAL',
      hint: 'Build client with "npm run build" in /client to serve Web UI here'
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

// Configurable Ports
const HTTP_PORT = process.env.PORT || 5000;
const TCP_PORT = process.env.TCP_PORT || 7000;

// Initialize Socket.IO
initSocketServer(httpServer);

// Start HTTP Server & Native TCP Server
httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
  const localIPs = getLocalIPs();
  const primaryIP = localIPs[0] || '127.0.0.1';

  // Start Native TCP Server for Android
  initTcpServer(TCP_PORT, '0.0.0.0');

  console.log(`\n=============================================================`);
  console.log(`🚀 MESH ALERT SYSTEM — UNIFIED PRODUCTION SERVER`);
  console.log(`   Team: The Inevitables`);
  console.log(`=============================================================`);
  console.log(`📡 Web UI & Socket.IO URL: http://${primaryIP}:${HTTP_PORT}`);
  console.log(`📱 Android TCP Socket:     ${primaryIP}:${TCP_PORT}`);
  console.log(`👉 In Android Native App, connect raw TCP socket to:`);
  console.log(`   Host: "${primaryIP}" | Port: ${TCP_PORT}`);
  console.log(`=============================================================\n`);
});

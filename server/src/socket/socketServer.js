const { Server } = require('socket.io');
const stateService = require('../services/stateService');
const { setTargetHost, getTargetInfo } = require('../tcp/tcpServer');

function getSocketIp(socket) {
  let ip = socket.handshake.headers['x-forwarded-for'] || socket.handshake.address;
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

function initSocketServer(httpServer) {
  const io = new Server(httpServer, {
    cors: {
      origin: '*',
      methods: ['GET', 'POST']
    }
  });

  // Connect state service to this Socket.IO instance
  stateService.setSocketIO(io);

  io.on('connection', (socket) => {
    const clientIp = getSocketIp(socket);
    console.log(`💻 [WEB SOCKET] Client connected: ${socket.id} (Device IP: ${clientIp})`);

    // Track detected client IP
    stateService.setDetectedClientIp(clientIp);

    // Always automatically bind TCP client target to this accessing device IP
    if (clientIp && clientIp !== '127.0.0.1' && clientIp !== '::1') {
      console.log(`✨ [AUTO-IP] Automatically targeting TCP to accessing device: ${clientIp}:7000`);
      setTargetHost(clientIp, 7000);
    }

    // Send immediate snapshot of current system state
    socket.emit('initial_state', {
      ...stateService.getState(),
      yourDetectedIp: clientIp,
      targetInfo: getTargetInfo()
    });

    // Admin can clear active alerts
    socket.on('admin:clear_alert', () => {
      console.log(`🛡️ [ADMIN ACTION] Clear active alert requested by React client ${socket.id}`);
      stateService.clearAlert();
    });

    socket.on('disconnect', () => {
      console.log(`💻 [WEB SOCKET] Client disconnected: ${socket.id}`);
    });
  });

  return io;
}

module.exports = { initSocketServer };

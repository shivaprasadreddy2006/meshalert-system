const { Server } = require('socket.io');
const stateService = require('../services/stateService');

function initSocketServer(httpServer) {
  const io = new Server(httpServer, {
    cors: {
      origin: '*',
      methods: ['GET', 'POST']
    }
  });

  stateService.setSocketIO(io);

  io.on('connection', (socket) => {
    console.log(`💻 [SOCKET] Browser client connected: ${socket.id}`);

    // Send current system state immediately on connect
    socket.emit('initial_state', {
      ...stateService.getState()
    });

    // Browser reports its own public IP (fetched from api.ipify.org on the client side)
    socket.on('device:report_ip', (data) => {
      if (data && data.ip) {
        console.log(`🌐 [CLIENT SELF-REPORTED IP]: ${data.ip} (socket: ${socket.id})`);
        // We just log it — the browser already stores it in React state directly.
        // No server action needed for this.
      }
    });

    // Admin clears active alert
    socket.on('admin:clear_alert', () => {
      console.log(`🛡️ [ADMIN] Alert cleared by client ${socket.id}`);
      stateService.clearAlert();
    });

    socket.on('disconnect', () => {
      console.log(`💻 [SOCKET] Browser client disconnected: ${socket.id}`);
    });
  });

  return io;
}

module.exports = { initSocketServer };

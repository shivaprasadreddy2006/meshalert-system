const { Server } = require('socket.io');
const stateService = require('../services/stateService');

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
    console.log(`💻 [WEB SOCKET] React UI Client connected: ${socket.id}`);

    // Send immediate snapshot of current system state
    socket.emit('initial_state', stateService.getState());

    // Admin can clear active alerts
    socket.on('admin:clear_alert', () => {
      console.log(`🛡️ [ADMIN ACTION] Clear active alert requested by React client ${socket.id}`);
      stateService.clearAlert();
    });

    socket.on('disconnect', () => {
      console.log(`💻 [WEB SOCKET] React UI Client disconnected: ${socket.id}`);
    });
  });

  return io;
}

module.exports = { initSocketServer };

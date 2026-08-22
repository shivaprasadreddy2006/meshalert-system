const net = require('net');
const stateService = require('../services/stateService');

let tcpServer = null;
const activeSockets = new Set();

function initTcpServer(port = 7000, host = '0.0.0.0') {
  tcpServer = net.createServer((socket) => {
    const clientAddress = `${socket.remoteAddress}:${socket.remotePort}`;
    console.log(`\n======================================================`);
    console.log(`📱 [TCP] Android device CONNECTED from ${clientAddress}`);
    console.log(`======================================================`);

    activeSockets.add(socket);
    stateService.setAndroidConnected(true, activeSockets.size);

    let buffer = '';

    // Receive data from Android
    socket.on('data', (chunk) => {
      const rawString = chunk.toString();
      console.log(`📩 [TCP INCOMING] Raw bytes from Android (${clientAddress}): ${rawString.trim()}`);
      buffer += rawString;

      // Process complete JSON objects (supports newline-delimited JSON or standalone chunks)
      let boundaryIndex;
      while ((boundaryIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, boundaryIndex).trim();
        buffer = buffer.slice(boundaryIndex + 1);
        if (line) {
          processRawMessage(line, socket);
        }
      }

      // If no newline, try parsing the whole trimmed buffer if it looks like complete JSON
      if (buffer.trim().startsWith('{') && buffer.trim().endsWith('}')) {
        processRawMessage(buffer.trim(), socket);
        buffer = '';
      }
    });

    // Android client disconnected
    socket.on('end', () => {
      console.log(`📱 [TCP] Android client initiated disconnect: ${clientAddress}`);
    });

    socket.on('close', (hadError) => {
      console.log(`📱 [TCP] Android socket CLOSED (${clientAddress})${hadError ? ' due to transmission error' : ''}`);
      activeSockets.delete(socket);
      stateService.setAndroidConnected(activeSockets.size > 0, activeSockets.size);
    });

    // Handle TCP Socket errors gracefully without crashing the server
    socket.on('error', (err) => {
      console.error(`⚠️ [TCP ERROR] Socket error with ${clientAddress}:`, err.message);
      activeSockets.delete(socket);
      stateService.setAndroidConnected(activeSockets.size > 0, activeSockets.size);
    });
  });

  tcpServer.on('error', (err) => {
    console.error(`❌ [TCP SERVER ERROR] Fatal TCP Server error:`, err.message);
  });

  tcpServer.listen(port, host, () => {
    console.log(`🚀 [TCP SERVER] Listening for Android TCP connections on ${host}:${port}`);
  });

  return tcpServer;
}

function processRawMessage(rawMessageString, socket) {
  try {
    const parsedData = JSON.parse(rawMessageString);
    
    // Validation
    if (!parsedData || typeof parsedData !== 'object') {
      console.warn(`⚠️ [TCP VALIDATION] Ignored non-object payload:`, rawMessageString);
      return;
    }

    if (!parsedData.message && !parsedData.alertType && !parsedData.type) {
      console.warn(`⚠️ [TCP VALIDATION] Missing essential message fields in payload:`, parsedData);
      return;
    }

    console.log(`✅ [TCP PARSED & VALIDATED]:`, JSON.stringify(parsedData, null, 2));

    // Store and forward to React clients via Socket.IO
    stateService.setLatestMessage(parsedData);

    // Send ACK back to Android if socket is still writable
    if (socket && socket.writable) {
      socket.write(JSON.stringify({ status: 'ACK', receivedAt: new Date().toISOString() }) + '\n');
    }
  } catch (err) {
    console.error(`❌ [TCP PARSE ERROR] Invalid JSON received from Android: "${rawMessageString}" - Error: ${err.message}`);
  }
}

module.exports = { initTcpServer };

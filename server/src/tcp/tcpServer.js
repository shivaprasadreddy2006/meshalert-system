const net = require('net');
const stateService = require('../services/stateService');

let tcpClient = null;
let reconnectTimer = null;
let isConnecting = false;
let buffer = '';

const RECONNECT_DELAY_MS = 3000;

function initTcpClient(port = 7000, host = '127.0.0.1') {
  console.log(`🔌 [TCP CLIENT] Will connect to Android TCP Server at ${host}:${port}`);
  connect(host, port);
}

function connect(host, port) {
  if (isConnecting) return;
  isConnecting = true;

  console.log(`📡 [TCP CLIENT] Attempting connection to Android at ${host}:${port}...`);

  const socket = new net.Socket();
  tcpClient = socket;
  buffer = '';

  socket.connect(port, host, () => {
    isConnecting = false;
    console.log('\n======================================================');
    console.log(`📱 [TCP CLIENT] Connected to Android TCP Server at ${host}:${port}`);
    console.log('======================================================');

    stateService.setAndroidConnected(true, 1);

    // Send a registration handshake so Android knows the server connected
    const reg = JSON.stringify({
      type: 'REGISTRATION',
      client: 'NodeJS Web Server',
      timestamp: new Date().toISOString()
    });
    socket.write(reg + '\n');
  });

  socket.on('data', (chunk) => {
    const rawString = chunk.toString();
    console.log(`📩 [TCP CLIENT] Data from Android: ${rawString.trim()}`);
    buffer += rawString;

    let boundaryIndex;
    while ((boundaryIndex = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, boundaryIndex).trim();
      buffer = buffer.slice(boundaryIndex + 1);
      if (line) processRawMessage(line, socket);
    }

    // Handle JSON without trailing newline
    if (buffer.trim().startsWith('{') && buffer.trim().endsWith('}')) {
      processRawMessage(buffer.trim(), socket);
      buffer = '';
    }
  });

  socket.on('close', () => {
    console.log(`🔴 [TCP CLIENT] Connection to Android closed. Reconnecting in ${RECONNECT_DELAY_MS / 1000}s...`);
    isConnecting = false;
    stateService.setAndroidConnected(false, 0);
    scheduleReconnect(host, port);
  });

  socket.on('error', (err) => {
    console.error(`⚠️ [TCP CLIENT ERROR] ${err.message} — retrying in ${RECONNECT_DELAY_MS / 1000}s...`);
    isConnecting = false;
    stateService.setAndroidConnected(false, 0);
    socket.destroy();
    scheduleReconnect(host, port);
  });
}

function scheduleReconnect(host, port) {
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => connect(host, port), RECONNECT_DELAY_MS);
}

function processRawMessage(rawMessageString, socket) {
  try {
    const parsedData = JSON.parse(rawMessageString);

    if (!parsedData || typeof parsedData !== 'object') {
      console.warn(`⚠️ [TCP VALIDATION] Ignored non-object payload:`, rawMessageString);
      return;
    }

    // Ignore registration/ping messages, only process ALERT payloads
    if (parsedData.type === 'REGISTRATION' || parsedData.type === 'PING') {
      console.log(`ℹ️ [TCP CLIENT] Received handshake: ${parsedData.type}`);
      return;
    }

    if (!parsedData.message && !parsedData.alertType && !parsedData.type) {
      console.warn(`⚠️ [TCP VALIDATION] Missing essential fields:`, parsedData);
      return;
    }

    console.log(`✅ [TCP CLIENT PARSED & VALIDATED]:`, JSON.stringify(parsedData, null, 2));

    // Store and broadcast to React clients via Socket.IO
    stateService.setLatestMessage(parsedData);

    // Send ACK back to Android
    if (socket && !socket.destroyed) {
      socket.write(JSON.stringify({ status: 'ACK', receivedAt: new Date().toISOString() }) + '\n');
    }
  } catch (err) {
    console.error(`❌ [TCP CLIENT PARSE ERROR] Invalid JSON: "${rawMessageString}" — ${err.message}`);
  }
}

module.exports = { initTcpServer: initTcpClient }; // keep export name compatible with server.js

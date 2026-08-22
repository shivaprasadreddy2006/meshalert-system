const net = require('net');
const stateService = require('../services/stateService');

let tcpClient = null;
let reconnectTimer = null;
let isConnecting = false;
let buffer = '';

let currentTargetHost = (process.env.ANDROID_HOST && process.env.ANDROID_HOST !== '127.0.0.1') ? process.env.ANDROID_HOST : null;
let currentTargetPort = parseInt(process.env.TCP_PORT, 10) || 7000;

const RECONNECT_DELAY_MS = 3000;

function initTcpClient(port = currentTargetPort, host = currentTargetHost) {
  currentTargetPort = port;
  if (host && host !== '127.0.0.1' && host !== 'localhost') {
    currentTargetHost = host;
    stateService.setTargetDevice(currentTargetHost, currentTargetPort);
    console.log(`🔌 [TCP CLIENT] Target Android TCP Server configured: ${currentTargetHost}:${currentTargetPort}`);
    connect(currentTargetHost, currentTargetPort);
  } else {
    currentTargetHost = null;
    console.log(`⏳ [TCP CLIENT] Standby — waiting for device to open Web App to auto-bind device IP on port ${currentTargetPort}...`);
  }
}

function setTargetHost(newHost, newPort = currentTargetPort) {
  if (!newHost) return;

  // Clean IPv6-mapped IPv4 prefix if present
  let cleanHost = newHost.trim();
  if (cleanHost.startsWith('::ffff:')) {
    cleanHost = cleanHost.replace('::ffff:', '');
  }

  // Do not bind to 127.0.0.1 in production when looking for real devices
  if (cleanHost === '127.0.0.1' || cleanHost === 'localhost' || cleanHost === '::1') {
    return;
  }

  // If already targeting this host and connected or connecting, do nothing
  if (cleanHost === currentTargetHost && newPort === currentTargetPort && (tcpClient && !tcpClient.destroyed)) {
    return;
  }

  console.log(`\n======================================================`);
  console.log(`📱 [TCP CLIENT] Switching target to Device IP: ${cleanHost}:${newPort}`);
  console.log(`======================================================`);

  currentTargetHost = cleanHost;
  currentTargetPort = newPort;
  stateService.setTargetDevice(currentTargetHost, currentTargetPort);

  // Clear existing reconnect timer and close previous socket
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }

  if (tcpClient) {
    try {
      tcpClient.destroy();
    } catch (e) {
      // ignore
    }
    tcpClient = null;
  }

  isConnecting = false;
  stateService.setAndroidConnected(false, 0);

  // Connect to the device IP immediately
  connect(currentTargetHost, currentTargetPort);
}

function getTargetInfo() {
  return {
    host: currentTargetHost,
    port: currentTargetPort,
    isConnected: stateService.getState().androidConnected
  };
}

function connect(host, port) {
  if (!host || host === '127.0.0.1' || isConnecting) return;
  isConnecting = true;

  console.log(`📡 [TCP CLIENT] Attempting connection to Android Phone at ${host}:${port}...`);

  const socket = new net.Socket();
  tcpClient = socket;
  buffer = '';

  // Timeout connection attempt after 5 seconds
  socket.setTimeout(5000);

  socket.connect(port, host, () => {
    isConnecting = false;
    socket.setTimeout(0); // clear timeout once connected

    console.log('\n======================================================');
    console.log(`🟢 [TCP CLIENT] Connected to Android Phone TCP Server at ${host}:${port}!`);
    console.log('======================================================');

    stateService.setAndroidConnected(true, 1);

    // Send a registration handshake so Android knows the server connected
    const reg = JSON.stringify({
      type: 'REGISTRATION',
      client: 'NodeJS Web Server (Railway)',
      timestamp: new Date().toISOString()
    });
    socket.write(reg + '\n');
  });

  socket.on('data', (chunk) => {
    const rawString = chunk.toString();
    console.log(`📩 [TCP CLIENT] Data from Android (${host}): ${rawString.trim()}`);
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

  socket.on('timeout', () => {
    console.log(`⏱️ [TCP CLIENT] Connection to ${host}:${port} timed out.`);
    socket.destroy();
  });

  socket.on('close', () => {
    isConnecting = false;
    stateService.setAndroidConnected(false, 0);
    // Only reconnect if this is still the active device host
    if (host === currentTargetHost && port === currentTargetPort && currentTargetHost) {
      console.log(`🔴 [TCP CLIENT] Connection to Android (${host}:${port}) closed. Reconnecting in ${RECONNECT_DELAY_MS / 1000}s...`);
      scheduleReconnect(host, port);
    }
  });

  socket.on('error', (err) => {
    isConnecting = false;
    stateService.setAndroidConnected(false, 0);
    socket.destroy();
    if (host === currentTargetHost && port === currentTargetPort && currentTargetHost) {
      console.error(`⚠️ [TCP CLIENT] ${host}:${port} — ${err.message} (retrying in ${RECONNECT_DELAY_MS / 1000}s)`);
      scheduleReconnect(host, port);
    }
  });
}

function scheduleReconnect(host, port) {
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => {
    if (host === currentTargetHost && port === currentTargetPort && currentTargetHost) {
      connect(host, port);
    }
  }, RECONNECT_DELAY_MS);
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

module.exports = { 
  initTcpServer: initTcpClient,
  initTcpClient,
  setTargetHost,
  getTargetInfo
};

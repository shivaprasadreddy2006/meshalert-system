// Simulated Android TCP Client (Test Script)
// Run with: node test-tcp-client.js

const net = require('net');

const TCP_HOST = process.env.TCP_HOST || '127.0.0.1';
const TCP_PORT = process.env.TCP_PORT || 7000;

console.log(`📱 [TEST ANDROID] Attempting TCP connection to ${TCP_HOST}:${TCP_PORT}...`);

const client = net.connect({ host: TCP_HOST, port: TCP_PORT }, () => {
  console.log(`\n======================================================`);
  console.log(`🟢 [TEST ANDROID] Connected to Node.js TCP Server!`);
  console.log(`   (React Dashboard should now show: Android Connected 🟢)`);
  console.log(`======================================================\n`);

  // Sample alert payload matching hackathon format
  const sampleAlert = {
    type: "ALERT",
    alertType: "FIRE",
    priority: "HIGH",
    message: "Fire detected on Floor 1. Move towards the North Exit. Do not use elevators.",
    area: "Floor 1",
    timestamp: new Date().toISOString()
  };

  console.log(`📤 Sending sample JSON emergency alert to TCP server...`);
  client.write(JSON.stringify(sampleAlert) + '\n');
  console.log(`   Sent: [${sampleAlert.alertType}] ${sampleAlert.message}`);
});

client.on('data', (data) => {
  console.log(`📩 [TEST ANDROID] Received ACK from TCP Server:`, data.toString().trim());
});

client.on('close', () => {
  console.log(`\n🔴 [TEST ANDROID] TCP Connection closed.`);
  console.log(`   (React Dashboard should now show: Android Disconnected 🔴)\n`);
  process.exit(0);
});

client.on('error', (err) => {
  console.error(`❌ [TEST ANDROID ERROR]`, err.message);
});

// Keep connection open for 15 seconds, then close automatically (or press Ctrl+C)
console.log(`⏳ Keeping connection alive. Press Ctrl+C anytime to disconnect.`);
setTimeout(() => {
  console.log(`\n⏱️ 15s test timeout reached. Gracefully closing TCP connection...`);
  client.end();
}, 15000);

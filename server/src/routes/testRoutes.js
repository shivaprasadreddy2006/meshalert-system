const express = require('express');
const router = express.Router();
const stateService = require('../services/stateService');
const { setTargetHost, getTargetInfo } = require('../tcp/tcpServer');

// Helper to extract clean client IP behind Railway / Reverse Proxy
function getClientIp(req) {
  let ip = req.headers['cf-connecting-ip'] ||
           req.headers['x-real-ip'] ||
           req.headers['x-client-ip'] ||
           req.headers['x-forwarded-for'] ||
           req.socket?.remoteAddress ||
           req.ip;

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

// Get current system & device connection status
router.get('/status', (req, res) => {
  const clientIp = getClientIp(req);
  res.json({
    success: true,
    clientIp,
    targetInfo: getTargetInfo(),
    data: stateService.getState()
  });
});

// View what IP the server sees for this client
router.get('/device/my-ip', (req, res) => {
  const clientIp = getClientIp(req);
  if (clientIp && clientIp !== '127.0.0.1') {
    stateService.setDetectedClientIp(clientIp);
    setTargetHost(clientIp, 7000);
  }
  res.json({
    success: true,
    yourIp: clientIp,
    currentTarget: stateService.getState().targetDeviceIp,
    currentTargetPort: stateService.getState().targetDevicePort,
    androidConnected: stateService.getState().androidConnected
  });
});

// Connect / Switch TCP client to a specified IP (or auto-detected client IP)
router.post(['/device/connect', '/device/set-target-ip'], (req, res) => {
  const clientIp = getClientIp(req);
  const targetIp = (req.body?.ip || req.query?.ip || clientIp).trim();
  const targetPort = parseInt(req.body?.port || req.query?.port, 10) || 7000;

  console.log(`📡 [HTTP DEVICE CONNECT] Switching TCP target to: ${targetIp}:${targetPort} (Requested by: ${clientIp})`);
  setTargetHost(targetIp, targetPort);

  res.json({
    success: true,
    message: `TCP Client now targeting ${targetIp}:${targetPort}`,
    targetIp,
    targetPort,
    detectedClientIp: clientIp
  });
});

// Auto-connect endpoint: Sets target host to the device making the request
router.all('/device/auto-connect', (req, res) => {
  const clientIp = getClientIp(req);
  console.log(`📡 [HTTP AUTO-CONNECT] Setting TCP target to caller IP: ${clientIp}:7000`);
  setTargetHost(clientIp, 7000);

  res.json({
    success: true,
    message: `TCP Client switched to caller IP: ${clientIp}:7000`,
    connectedToIp: clientIp,
    port: 7000
  });
});

// Production & Test Alert Ingestion Endpoint
router.post(['/alert', '/test/alert'], (req, res) => {
  const payload = req.body && Object.keys(req.body).length > 0 ? req.body : {
    type: 'ALERT',
    alertType: 'FIRE',
    priority: 'HIGH',
    message: 'Fire detected on Floor 1. Move towards the North Exit. Do not use elevators.',
    area: 'Floor 1',
    timestamp: new Date().toISOString()
  };

  console.log(`📡 [HTTP ALERT INGESTION] Alert payload received:`, payload);
  const formatted = stateService.setLatestMessage(payload);

  res.json({
    success: true,
    message: 'Alert dispatched to WebSocket clients',
    alert: formatted
  });
});

// Development / Testing Endpoint: Toggle simulated Android TCP connection
router.post('/test/toggle-connection', (req, res) => {
  const current = stateService.getState().androidConnected;
  const newStatus = !current;
  stateService.setAndroidConnected(newStatus, newStatus ? 1 : 0);

  res.json({
    success: true,
    androidConnected: newStatus
  });
});

// Clear active alert
router.post('/test/clear', (req, res) => {
  stateService.clearAlert();
  res.json({ success: true, message: 'Alert cleared' });
});

module.exports = router;

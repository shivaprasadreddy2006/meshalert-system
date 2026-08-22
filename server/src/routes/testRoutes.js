const express = require('express');
const router = express.Router();
const stateService = require('../services/stateService');

// Helper: extract clean client IP behind Railway / Reverse Proxy
function getClientIp(req) {
  let ip =
    req.headers['cf-connecting-ip'] ||
    req.headers['x-real-ip'] ||
    req.headers['x-client-ip'] ||
    (req.headers['x-forwarded-for'] || '').split(',')[0].trim() ||
    req.ip ||
    req.socket?.remoteAddress ||
    '127.0.0.1';

  if (ip && ip.startsWith('::ffff:')) {
    ip = ip.replace('::ffff:', '');
  }
  return ip || '127.0.0.1';
}

// ── Debug: inspect all headers ──────────────────────────────────────────────
router.get('/debug', (req, res) => {
  res.json({
    detectedIp: getClientIp(req),
    headers: req.headers,
    state: stateService.getState()
  });
});

// ── What is my IP? (browser fetch fallback) ─────────────────────────────────
router.get('/device/my-ip', (req, res) => {
  const ip = getClientIp(req);
  console.log(`🔍 [/api/device/my-ip] Caller IP: ${ip}`);
  res.json({ success: true, yourIp: ip });
});

// ── Android Heartbeat: keeps "Connected" status alive ───────────────────────
// Android app should POST here every ~10s to maintain the green badge
router.post('/device/heartbeat', (req, res) => {
  const ip = getClientIp(req);
  console.log(`💓 [HEARTBEAT] Android device alive at: ${ip}`);
  stateService.setAndroidConnected(true, ip);
  res.json({ success: true, message: 'Heartbeat received', yourIp: ip });
});

// ── Android sends this when app connects ────────────────────────────────────
router.all('/device/auto-connect', (req, res) => {
  const ip = getClientIp(req);
  console.log(`🤝 [AUTO-CONNECT] Android device connected from: ${ip}`);
  stateService.setAndroidConnected(true, ip);
  res.json({
    success: true,
    message: 'Connected to Mesh Alert Cloud Server',
    serverTime: new Date().toISOString()
  });
});

// ── MAIN: Emergency Alert from Android BLE Mesh ─────────────────────────────
// Android app POSTs here when it receives/sends a BLE mesh alert
router.post(['/alert', '/test/alert'], (req, res) => {
  const senderIp = getClientIp(req);

  // Use test payload if body is empty
  const payload =
    req.body && Object.keys(req.body).length > 0
      ? req.body
      : {
          type: 'ALERT',
          alertType: 'FIRE',
          priority: 'CRITICAL',
          message: 'Fire detected on Floor 1. Move towards the North Exit. Do not use elevators.',
          area: 'Floor 1',
          sender: 'Test Node',
          timestamp: new Date().toISOString()
        };

  console.log(`🚨 [ALERT] Received from Android (${senderIp}):`, payload);

  // Mark Android as connected and broadcast the alert
  stateService.setAndroidConnected(true, senderIp);
  const formatted = stateService.setLatestMessage(payload, senderIp);

  res.json({
    success: true,
    message: 'Alert received and broadcast to all web clients',
    alert: formatted,
    receivedFrom: senderIp
  });
});

// ── Status endpoint ──────────────────────────────────────────────────────────
router.get('/status', (req, res) => {
  res.json({
    success: true,
    state: stateService.getState()
  });
});

// ── Admin: Clear active alert ────────────────────────────────────────────────
router.post('/test/clear', (req, res) => {
  stateService.clearAlert();
  res.json({ success: true, message: 'Alert cleared' });
});

// ── Test: Fire a sample alert without Android app ────────────────────────────
router.post('/test/trigger', (req, res) => {
  const ip = getClientIp(req);
  const sampleAlert = {
    type: 'ALERT',
    alertType: 'FIRE',
    priority: 'CRITICAL',
    message: 'Test: Fire detected on Floor 2! Evacuate immediately via staircase.',
    area: 'Floor 2',
    sender: 'Test Trigger',
    timestamp: new Date().toISOString()
  };
  stateService.setAndroidConnected(true, ip);
  const formatted = stateService.setLatestMessage(sampleAlert, ip);
  res.json({ success: true, alert: formatted });
});

module.exports = router;

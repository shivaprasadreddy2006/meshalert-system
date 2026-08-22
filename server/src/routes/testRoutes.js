const express = require('express');
const router = express.Router();
const stateService = require('../services/stateService');

// Get current state snapshot
router.get('/status', (req, res) => {
  res.json({
    success: true,
    data: stateService.getState()
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

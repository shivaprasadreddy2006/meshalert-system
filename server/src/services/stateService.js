// In-memory State Service (Single Source of Truth)

class StateService {
  constructor() {
    this.state = {
      androidConnected: false,
      targetDeviceIp: process.env.ANDROID_HOST || '127.0.0.1',
      targetDevicePort: parseInt(process.env.TCP_PORT, 10) || 7000,
      detectedClientIp: null,
      latestMessage: null,
      lastMessageTime: null,
      tcpConnectionsCount: 0,
      alertHistory: []
    };
    this.io = null;
  }

  setSocketIO(ioInstance) {
    this.io = ioInstance;
  }

  getState() {
    return { 
      ...this.state,
      alertHistory: [...this.state.alertHistory]
    };
  }

  setTargetDevice(host, port = 7000) {
    this.state.targetDeviceIp = host;
    this.state.targetDevicePort = port;
    console.log(`[STATE] Target Device IP updated: ${host}:${port}`);

    if (this.io) {
      this.io.emit('device:target_updated', {
        targetDeviceIp: this.state.targetDeviceIp,
        targetDevicePort: this.state.targetDevicePort,
        androidConnected: this.state.androidConnected
      });
    }
  }

  setDetectedClientIp(ip) {
    if (!ip) return;
    this.state.detectedClientIp = ip;
    if (this.io) {
      this.io.emit('device:detected_ip', { detectedClientIp: ip });
    }
  }

  setAndroidConnected(status, count = 0) {
    this.state.androidConnected = status;
    this.state.tcpConnectionsCount = count;
    console.log(`[STATE] Android Connection State: ${status ? '🟢 CONNECTED' : '🔴 DISCONNECTED'} (Active TCP Sockets: ${count})`);
    
    if (this.io) {
      this.io.emit('android:status', {
        androidConnected: this.state.androidConnected,
        tcpConnectionsCount: this.state.tcpConnectionsCount,
        targetDeviceIp: this.state.targetDeviceIp,
        targetDevicePort: this.state.targetDevicePort
      });
    }
  }

  setLatestMessage(messageData) {
    const formattedMessage = {
      id: messageData.alertId || `alert-${Date.now()}`,
      type: messageData.type || 'ALERT',
      alertType: messageData.alertType || 'GENERAL',
      priority: messageData.priority || 'MEDIUM',
      message: messageData.message || 'Emergency message received.',
      area: messageData.area || 'Floor 1',
      sender: messageData.sender || 'Mesh Node',
      timestamp: messageData.timestamp || new Date().toISOString(),
      receivedAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    };

    this.state.latestMessage = formattedMessage;
    this.state.lastMessageTime = formattedMessage.receivedAt;

    // Maintain recent history (last 20 alerts)
    this.state.alertHistory = [
      formattedMessage,
      ...this.state.alertHistory.filter(a => a.id !== formattedMessage.id)
    ].slice(0, 20);

    console.log(`[STATE] New Alert Stored: [${formattedMessage.alertType}] ${formattedMessage.message}`);

    if (this.io) {
      this.io.emit('emergency:alert', formattedMessage);
    }

    return formattedMessage;
  }

  clearAlert() {
    this.state.latestMessage = null;
    this.state.lastMessageTime = null;
    console.log(`[STATE] Active alert cleared.`);

    if (this.io) {
      this.io.emit('alert:cleared');
    }
  }
}

const stateService = new StateService();
module.exports = stateService;

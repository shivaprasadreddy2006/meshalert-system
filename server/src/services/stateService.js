// In-memory State Service (Single Source of Truth)

class StateService {
  constructor() {
    this.state = {
      androidConnected: false,
      androidDeviceIp: null,       // IP of the Android device (from HTTP POST headers)
      androidLastSeen: null,       // Timestamp of last Android POST
      latestMessage: null,
      lastMessageTime: null,
      alertHistory: []
    };
    this.io = null;
    this._androidTimeoutTimer = null;
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

  setAndroidConnected(status, androidIp = null) {
    this.state.androidConnected = status;
    if (androidIp) this.state.androidDeviceIp = androidIp;
    if (status) this.state.androidLastSeen = new Date().toISOString();

    console.log(`[STATE] Android: ${status ? '🟢 CONNECTED' : '🔴 DISCONNECTED'} (IP: ${this.state.androidDeviceIp || 'unknown'})`);

    if (this.io) {
      this.io.emit('android:status', {
        androidConnected: this.state.androidConnected,
        androidDeviceIp: this.state.androidDeviceIp,
        androidLastSeen: this.state.androidLastSeen
      });
    }

    // Auto-disconnect if no activity for 30 seconds
    if (status) {
      if (this._androidTimeoutTimer) clearTimeout(this._androidTimeoutTimer);
      this._androidTimeoutTimer = setTimeout(() => {
        this.state.androidConnected = false;
        console.log(`[STATE] Android timed out (no activity for 30s)`);
        if (this.io) {
          this.io.emit('android:status', {
            androidConnected: false,
            androidDeviceIp: this.state.androidDeviceIp
          });
        }
      }, 30000);
    }
  }

  setLatestMessage(messageData, senderIp = null) {
    const formattedMessage = {
      id: messageData.alertId || `alert-${Date.now()}`,
      type: messageData.type || 'ALERT',
      alertType: messageData.alertType || 'GENERAL',
      priority: messageData.priority || 'MEDIUM',
      message: messageData.message || 'Emergency message received.',
      area: messageData.area || 'Floor 1',
      sender: messageData.sender || 'Mesh Node',
      senderIp: senderIp || messageData.senderIp || null,
      timestamp: messageData.timestamp || new Date().toISOString(),
      receivedAt: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    };

    this.state.latestMessage = formattedMessage;
    this.state.lastMessageTime = formattedMessage.receivedAt;

    // Mark Android as connected when it sends an alert
    if (senderIp) {
      this.state.androidDeviceIp = senderIp;
      this.state.androidConnected = true;
      this.state.androidLastSeen = new Date().toISOString();
    }

    // Maintain recent history (last 20 alerts)
    this.state.alertHistory = [
      formattedMessage,
      ...this.state.alertHistory.filter(a => a.id !== formattedMessage.id)
    ].slice(0, 20);

    console.log(`[STATE] 🚨 Alert: [${formattedMessage.alertType}] "${formattedMessage.message}" (from ${senderIp || 'unknown'})`);

    if (this.io) {
      this.io.emit('emergency:alert', formattedMessage);
      // Also update Android connection status badge
      this.io.emit('android:status', {
        androidConnected: true,
        androidDeviceIp: senderIp || this.state.androidDeviceIp
      });
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

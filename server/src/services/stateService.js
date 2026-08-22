// In-memory State Service (Single Source of Truth)

class StateService {
  constructor() {
    this.state = {
      androidConnected: false,
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

  setAndroidConnected(status, count = 0) {
    this.state.androidConnected = status;
    this.state.tcpConnectionsCount = count;
    console.log(`[STATE] Android Connection State: ${status ? '🟢 CONNECTED' : '🔴 DISCONNECTED'} (Active TCP Sockets: ${count})`);
    
    if (this.io) {
      this.io.emit('android:status', {
        androidConnected: this.state.androidConnected,
        tcpConnectionsCount: this.state.tcpConnectionsCount
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

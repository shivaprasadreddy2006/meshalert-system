import React, { useState, useEffect } from 'react';
import { socket } from './services/socket';
import Navbar from './components/Navbar';
import FullScreenAlert from './components/FullScreenAlert';
import RoleSelect from './pages/RoleSelect';
import ClientDashboard from './pages/ClientDashboard';
import AdminDashboard from './pages/AdminDashboard';
import { playEmergencyAlarm, stopEmergencyAlarm, unlockAudio } from './services/audioAlarm';

export default function App() {
  const [role, setRole] = useState(null); // null | 'client' | 'admin'
  const [androidConnected, setAndroidConnected] = useState(false);
  const [targetDeviceIp, setTargetDeviceIp] = useState('127.0.0.1');
  const [targetDevicePort, setTargetDevicePort] = useState(7000);
  const [detectedClientIp, setDetectedClientIp] = useState(null);
  const [alert, setAlert] = useState(null);
  const [alertHistory, setAlertHistory] = useState([]);
  const [showFullScreen, setShowFullScreen] = useState(false);

  useEffect(() => {
    // Unlock Web Audio on any click/touch anywhere on the page
    const handleFirstInteraction = () => {
      unlockAudio();
    };
    window.addEventListener('click', handleFirstInteraction, { once: true });
    window.addEventListener('touchstart', handleFirstInteraction, { once: true });

    // Fetch IP info immediately via REST API on landing
    const BACKEND_URL = window.location.hostname === 'localhost' && window.location.port === '5173'
      ? 'http://localhost:5000'
      : window.location.origin;

    fetch(`${BACKEND_URL}/api/device/my-ip`)
      .then((res) => res.json())
      .then((data) => {
        if (data && data.yourIp) {
          setDetectedClientIp(data.yourIp);
          if (data.currentTarget) setTargetDeviceIp(data.currentTarget);
          if (data.currentTargetPort) setTargetDevicePort(data.currentTargetPort);
        }
      })
      .catch(() => {});

    // 1. Initial State Snapshot from Backend
    socket.on('initial_state', (data) => {
      console.log('📡 [INIT STATE] Snapshot from backend:', data);
      if (data) {
        setAndroidConnected(Boolean(data.androidConnected));
        setAlert(data.latestMessage || null);
        if (data.targetDeviceIp) setTargetDeviceIp(data.targetDeviceIp);
        if (data.targetDevicePort) setTargetDevicePort(data.targetDevicePort);
        if (data.yourDetectedIp) setDetectedClientIp(data.yourDetectedIp);
        if (data.alertHistory && Array.isArray(data.alertHistory)) {
          setAlertHistory(data.alertHistory);
        }
      }
    });

    // 2. Android TCP Connection State Change
    socket.on('android:status', (data) => {
      console.log('📱 [TCP STATUS] Android connection updated:', data);
      setAndroidConnected(Boolean(data.androidConnected));
      if (data.targetDeviceIp) setTargetDeviceIp(data.targetDeviceIp);
      if (data.targetDevicePort) setTargetDevicePort(data.targetDevicePort);
    });

    // 3. Dynamic Target IP updated
    socket.on('device:target_updated', (data) => {
      console.log('🔄 [TARGET IP UPDATED]:', data);
      if (data.targetDeviceIp) setTargetDeviceIp(data.targetDeviceIp);
      if (data.targetDevicePort) setTargetDevicePort(data.targetDevicePort);
      setAndroidConnected(Boolean(data.androidConnected));
    });

    // 4. Detected client IP
    socket.on('device:detected_ip', (data) => {
      if (data.detectedClientIp) setDetectedClientIp(data.detectedClientIp);
    });

    // 5. Live Emergency Alert from Android TCP / Web Bridge
    socket.on('emergency:alert', (newAlert) => {
      console.log('🚨 [ALERT RECEIVED] Emergency broadcast:', newAlert);
      setAlert(newAlert);

      // Add to alert history
      setAlertHistory((prev) => [
        newAlert,
        ...prev.filter((a) => a.id !== newAlert.id)
      ].slice(0, 20));

      // Trigger Full-Screen Alert Takeover & Web Audio Alarm!
      setShowFullScreen(true);
      playEmergencyAlarm();
    });

    // 6. Alert Cleared Event
    socket.on('alert:cleared', () => {
      console.log('✅ [ALERT CLEARED]');
      setAlert(null);
      setShowFullScreen(false);
      stopEmergencyAlarm();
    });

    // Fallback: If socket disconnects, mark as disconnected
    socket.on('disconnect', () => {
      setAndroidConnected(false);
    });

    return () => {
      window.removeEventListener('click', handleFirstInteraction);
      window.removeEventListener('touchstart', handleFirstInteraction);
      socket.off('initial_state');
      socket.off('android:status');
      socket.off('device:target_updated');
      socket.off('device:detected_ip');
      socket.off('emergency:alert');
      socket.off('alert:cleared');
      socket.off('disconnect');
      stopEmergencyAlarm();
    };
  }, []);

  const handleClearAlert = () => {
    stopEmergencyAlarm();
    setShowFullScreen(false);
    socket.emit('admin:clear_alert');
    setAlert(null);
  };

  const handleAcknowledgeAlert = () => {
    stopEmergencyAlarm();
    setShowFullScreen(false);
  };

  const handleDismissFullScreen = () => {
    stopEmergencyAlarm();
    setShowFullScreen(false);
  };

  // Resolved active device IP to display
  const activeDeviceIp = detectedClientIp || targetDeviceIp;

  return (
    <div className="min-h-screen bg-[#070a13] text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      
      {/* Top Navigation */}
      <Navbar 
        role={role} 
        onSwitchRole={() => setRole(null)} 
        androidConnected={androidConnected}
        deviceIp={activeDeviceIp}
        hasActiveAlert={Boolean(alert)}
        onOpenFullScreen={() => setShowFullScreen(true)}
      />

      {/* Main Role Content */}
      <main className="flex-1 px-3 sm:px-6 py-4 sm:py-6">
        {!role ? (
          <RoleSelect 
            onSelectRole={(selectedRole) => setRole(selectedRole)} 
            androidConnected={androidConnected}
            deviceIp={activeDeviceIp} 
          />
        ) : role === 'client' ? (
          <ClientDashboard 
            androidConnected={androidConnected}
            targetDeviceIp={activeDeviceIp}
            detectedClientIp={detectedClientIp}
            alert={alert} 
            alertHistory={alertHistory}
            onOpenFullScreen={() => setShowFullScreen(true)}
          />
        ) : (
          <AdminDashboard 
            androidConnected={androidConnected}
            targetDeviceIp={activeDeviceIp}
            targetDevicePort={targetDevicePort}
            detectedClientIp={detectedClientIp}
            alert={alert} 
            alertHistory={alertHistory}
            onClearAlert={handleClearAlert} 
            onOpenFullScreen={() => setShowFullScreen(true)}
          />
        )}
      </main>

      {/* Full-Screen Emergency Alert Overlay */}
      {showFullScreen && alert && (
        <FullScreenAlert
          alert={alert}
          onDismiss={handleDismissFullScreen}
          onAcknowledge={handleAcknowledgeAlert}
        />
      )}

      {/* Responsive Footer */}
      <footer className="mt-auto border-t border-dark-700/60 bg-dark-900/50 py-3 px-4 text-center text-[11px] text-slate-500 font-mono">
        Mesh-based Alerting System with Localization Support • Team The Inevitables
      </footer>

    </div>
  );
}

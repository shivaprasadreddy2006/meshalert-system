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
  const [androidDeviceIp, setAndroidDeviceIp] = useState(null);
  const [myPublicIp, setMyPublicIp] = useState(null);        // This browser device's real public IP
  const [alert, setAlert] = useState(null);
  const [alertHistory, setAlertHistory] = useState([]);
  const [showFullScreen, setShowFullScreen] = useState(false);

  useEffect(() => {
    // Unlock Web Audio on any click/touch
    const handleFirstInteraction = () => unlockAudio();
    window.addEventListener('click', handleFirstInteraction, { once: true });
    window.addEventListener('touchstart', handleFirstInteraction, { once: true });

    // ── Step 1: Detect THIS browser device's real public IP ──────────────────
    // api.ipify.org is the most reliable public IP lookup service available.
    // Doing it in the browser bypasses all Railway proxy header issues.
    fetch('https://api.ipify.org?format=json')
      .then((r) => r.json())
      .then((data) => {
        if (data && data.ip) {
          console.log('🌐 [PUBLIC IP DETECTED]:', data.ip);
          setMyPublicIp(data.ip);
          // Report our real IP back to the backend so it can display it
          socket.emit('device:report_ip', { ip: data.ip });
        }
      })
      .catch(() => {
        // Fallback: try the backend's perspective of our IP
        const BACKEND_URL =
          window.location.port === '5173'
            ? 'http://localhost:5000'
            : window.location.origin;

        fetch(`${BACKEND_URL}/api/device/my-ip`)
          .then((r) => r.json())
          .then((data) => {
            if (data && data.yourIp && data.yourIp !== '127.0.0.1') {
              setMyPublicIp(data.yourIp);
            }
          })
          .catch(() => {}); // Silent fallback
      });

    // ── Step 2: Socket.IO Real-time Events ───────────────────────────────────

    // Initial state snapshot from server
    socket.on('initial_state', (data) => {
      console.log('📡 [INIT STATE]:', data);
      if (!data) return;
      setAndroidConnected(Boolean(data.androidConnected));
      if (data.androidDeviceIp) setAndroidDeviceIp(data.androidDeviceIp);
      setAlert(data.latestMessage || null);
      if (data.alertHistory && Array.isArray(data.alertHistory)) {
        setAlertHistory(data.alertHistory);
      }
    });

    // Android device connected via HTTP POST
    socket.on('android:status', (data) => {
      console.log('📱 [ANDROID STATUS]:', data);
      setAndroidConnected(Boolean(data.androidConnected));
      if (data.androidDeviceIp) setAndroidDeviceIp(data.androidDeviceIp);
    });

    // Emergency alert received from Android app
    socket.on('emergency:alert', (newAlert) => {
      console.log('🚨 [ALERT]:', newAlert);
      setAndroidConnected(true); // Android sent an alert so it's connected
      if (newAlert.senderIp) setAndroidDeviceIp(newAlert.senderIp);
      setAlert(newAlert);
      setAlertHistory((prev) =>
        [newAlert, ...prev.filter((a) => a.id !== newAlert.id)].slice(0, 20)
      );
      setShowFullScreen(true);
      playEmergencyAlarm();
    });

    // Alert cleared
    socket.on('alert:cleared', () => {
      setAlert(null);
      setShowFullScreen(false);
      stopEmergencyAlarm();
    });

    socket.on('disconnect', () => setAndroidConnected(false));

    return () => {
      window.removeEventListener('click', handleFirstInteraction);
      window.removeEventListener('touchstart', handleFirstInteraction);
      socket.off('initial_state');
      socket.off('android:status');
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

  const handleDismissFullScreen = () => {
    stopEmergencyAlarm();
    setShowFullScreen(false);
  };

  return (
    <div className="min-h-screen bg-[#070a13] text-slate-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">

      <Navbar
        role={role}
        onSwitchRole={() => setRole(null)}
        androidConnected={androidConnected}
        myPublicIp={myPublicIp}
        androidDeviceIp={androidDeviceIp}
        hasActiveAlert={Boolean(alert)}
        onOpenFullScreen={() => setShowFullScreen(true)}
      />

      <main className="flex-1 px-3 sm:px-6 py-4 sm:py-6">
        {!role ? (
          <RoleSelect
            onSelectRole={(r) => setRole(r)}
            androidConnected={androidConnected}
            myPublicIp={myPublicIp}
            androidDeviceIp={androidDeviceIp}
          />
        ) : role === 'client' ? (
          <ClientDashboard
            androidConnected={androidConnected}
            androidDeviceIp={androidDeviceIp}
            myPublicIp={myPublicIp}
            alert={alert}
            alertHistory={alertHistory}
            onOpenFullScreen={() => setShowFullScreen(true)}
          />
        ) : (
          <AdminDashboard
            androidConnected={androidConnected}
            androidDeviceIp={androidDeviceIp}
            myPublicIp={myPublicIp}
            alert={alert}
            alertHistory={alertHistory}
            onClearAlert={handleClearAlert}
            onOpenFullScreen={() => setShowFullScreen(true)}
          />
        )}
      </main>

      {showFullScreen && alert && (
        <FullScreenAlert
          alert={alert}
          onDismiss={handleDismissFullScreen}
          onAcknowledge={handleDismissFullScreen}
        />
      )}

      <footer className="mt-auto border-t border-dark-700/60 bg-dark-900/50 py-3 px-4 text-center text-[11px] text-slate-500 font-mono">
        Mesh-based Alerting System with Localization Support • Team The Inevitables
      </footer>

    </div>
  );
}

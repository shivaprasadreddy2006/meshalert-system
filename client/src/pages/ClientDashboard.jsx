import React from 'react';
import StatusBadge from '../components/StatusBadge';
import AlertCard from '../components/AlertCard';
import { ShieldCheck, Info, History, MapPin, Clock, Flame, Users, HeartPulse, LogOut, ShieldAlert } from 'lucide-react';

export default function ClientDashboard({ 
  androidConnected, 
  targetDeviceIp = '127.0.0.1', 
  detectedClientIp = null, 
  alert, 
  alertHistory = [], 
  onOpenFullScreen 
}) {
  const getSmallIcon = (type) => {
    switch (type) {
      case 'FIRE': return <Flame className="w-3.5 h-3.5 text-rose-400" />;
      case 'STAMPEDE': return <Users className="w-3.5 h-3.5 text-amber-400" />;
      case 'MEDICAL': return <HeartPulse className="w-3.5 h-3.5 text-emerald-400" />;
      case 'EVACUATION': return <LogOut className="w-3.5 h-3.5 text-rose-400" />;
      default: return <ShieldAlert className="w-3.5 h-3.5 text-slate-400" />;
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6 pb-8">
      
      {/* System Header */}
      <div className="space-y-3">
        <div>
          <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight">
            Emergency Alert Monitor
          </h2>
          <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
            Real-time emergency broadcast receiver connected via Android BLE Mesh.
          </p>
        </div>

        {/* Connection Status Badges */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <StatusBadge
            label="Android BLE Mesh TCP Bridge"
            isConnected={androidConnected}
            activeText="Online & Relaying 🟢"
            inactiveText="Connecting 🔴"
            subtitle={`Target: ${targetDeviceIp}:7000`}
          />

          <StatusBadge
            label="Your Device IP"
            isConnected={Boolean(detectedClientIp)}
            activeText={detectedClientIp ? `${detectedClientIp} 🟢` : 'Auto-detected'}
            inactiveText="Detecting..."
            subtitle="Auto-bound target address"
          />
        </div>
      </div>

      {/* Primary Alert Section */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400">
          <span>Active Emergency Alert</span>
          {alert && <span className="text-rose-400 animate-pulse">● BROADCAST ACTIVE</span>}
        </div>
        <AlertCard 
          alert={alert} 
          onOpenFullScreen={onOpenFullScreen}
          isAdmin={false} 
        />
      </div>

      {/* Safety & Evacuation Guidance Box */}
      <div className="bg-dark-900/60 border border-dark-700/60 rounded-2xl p-4 sm:p-5 space-y-3 text-xs text-slate-300">
        <div className="flex items-center gap-2 font-bold text-slate-200">
          <ShieldCheck className="w-4 h-4 text-blue-400" />
          <span>Safety Guidelines for Localized Incidents</span>
        </div>
        <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-slate-400 text-[11px] sm:text-xs">
          <li className="flex items-start gap-1.5 bg-dark-950/40 p-2 rounded-lg border border-dark-700/30">
            <span className="text-blue-400 font-bold">1.</span>
            <span>Do not use elevators during fire or structural alerts.</span>
          </li>
          <li className="flex items-start gap-1.5 bg-dark-950/40 p-2 rounded-lg border border-dark-700/30">
            <span className="text-blue-400 font-bold">2.</span>
            <span>Follow marked illuminated emergency exit signage.</span>
          </li>
          <li className="flex items-start gap-1.5 bg-dark-950/40 p-2 rounded-lg border border-dark-700/30">
            <span className="text-blue-400 font-bold">3.</span>
            <span>Assist elderly and individuals needing mobility help.</span>
          </li>
          <li className="flex items-start gap-1.5 bg-dark-950/40 p-2 rounded-lg border border-dark-700/30">
            <span className="text-blue-400 font-bold">4.</span>
            <span>Maintain mesh node proximity for localization continuity.</span>
          </li>
        </ul>
      </div>

      {/* Alert History Section */}
      {alertHistory && alertHistory.length > 0 && (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-xs font-mono font-bold uppercase text-slate-400">
            <History className="w-3.5 h-3.5 text-slate-400" />
            <span>Recent Alert History ({alertHistory.length})</span>
          </div>

          <div className="space-y-2">
            {alertHistory.map((item, idx) => (
              <div 
                key={item.id || idx}
                className="bg-dark-900/90 border border-dark-700/70 rounded-xl p-3 sm:p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs transition hover:border-dark-600"
              >
                <div className="flex items-start sm:items-center gap-2.5 min-w-0">
                  <div className="mt-0.5 sm:mt-0 p-1.5 rounded-lg bg-dark-950 border border-dark-700 shrink-0">
                    {getSmallIcon(item.alertType)}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-white uppercase text-[11px] sm:text-xs">
                        {item.alertType || 'ALERT'}
                      </span>
                      <span className="text-[10px] font-mono text-slate-400 px-1.5 py-0.2 rounded bg-dark-950 border border-dark-700">
                        {item.priority || 'MEDIUM'}
                      </span>
                    </div>
                    <p className="text-slate-300 text-xs truncate mt-0.5">{item.message}</p>
                  </div>
                </div>

                <div className="flex items-center gap-3 text-[11px] font-mono text-slate-400 shrink-0 pl-8 sm:pl-0">
                  {item.area && (
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3 h-3 text-blue-400" />
                      {item.area}
                    </span>
                  )}
                  <span className="flex items-center gap-1 text-slate-500">
                    <Clock className="w-3 h-3 text-slate-500" />
                    {item.receivedAt}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  );
}

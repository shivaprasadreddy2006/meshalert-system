import React from 'react';
import StatusBadge from '../components/StatusBadge';
import AlertCard from '../components/AlertCard';
import { ShieldCheck, Info, History, MapPin, Clock, Flame, Users, HeartPulse, LogOut, ShieldAlert } from 'lucide-react';

export default function ClientDashboard({ 
  androidConnected, 
  androidDeviceIp,
  myPublicIp, 
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
            label="Android BLE Mesh Relay"
            isConnected={androidConnected}
            activeText="Online & Relaying 🟢"
            inactiveText="Standby / Ready ⚪"
            subtitle={androidConnected ? `Connected device: ${androidDeviceIp || 'Mesh Node'}` : 'Waiting for Android app'}
          />

          <StatusBadge
            label="Your Device IP"
            isConnected={Boolean(myPublicIp)}
            activeText={myPublicIp ? `${myPublicIp} 🟢` : 'Detecting...'}
            inactiveText="Detecting..."
            subtitle="Public network address"
          />
        </div>
      </div>

      {/* Primary Alert Section */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400">
          <span>Active Emergency Status</span>
          {alert && (
            <button
              onClick={onOpenFullScreen}
              className="text-rose-400 hover:text-rose-300 transition text-[11px] underline underline-offset-2 flex items-center gap-1"
            >
              Take Over Screen ↗
            </button>
          )}
        </div>

        {alert ? (
          <AlertCard alert={alert} onOpenFullScreen={onOpenFullScreen} />
        ) : (
          <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-6 sm:p-8 text-center space-y-3 shadow-lg">
            <div className="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto shadow-inner">
              <ShieldCheck className="w-7 h-7" />
            </div>
            <div>
              <h3 className="text-base sm:text-lg font-bold text-white">All Clear — No Active Emergency</h3>
              <p className="text-xs sm:text-sm text-slate-400 max-w-md mx-auto mt-1">
                Listening for BLE mesh emergency beacons broadcast from Android nodes.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Alert History Section */}
      <div className="space-y-3 pt-2">
        <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400 border-b border-dark-700/60 pb-2">
          <span className="flex items-center gap-1.5">
            <History className="w-3.5 h-3.5 text-blue-400" />
            Broadcast Log History
          </span>
          <span>{alertHistory.length} Recorded</span>
        </div>

        {alertHistory.length === 0 ? (
          <div className="bg-dark-900/50 border border-dashed border-dark-700 rounded-xl p-5 text-center text-xs text-slate-500">
            No incident broadcasts recorded yet.
          </div>
        ) : (
          <div className="space-y-2">
            {alertHistory.map((item, index) => (
              <div 
                key={item.id || index}
                className="bg-dark-900 border border-dark-700/60 rounded-xl p-3.5 flex items-start justify-between gap-3 text-xs"
              >
                <div className="flex items-start gap-2.5 min-w-0">
                  <div className="mt-0.5 shrink-0">
                    {getSmallIcon(item.alertType)}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-white uppercase text-[11px] font-mono">
                        {item.alertType || 'ALERT'}
                      </span>
                      <span className="text-slate-500">•</span>
                      <span className="text-slate-300 font-medium flex items-center gap-1 text-[11px]">
                        <MapPin className="w-3 h-3 text-slate-400" />
                        {item.area || 'Floor 1'}
                      </span>
                    </div>
                    <p className="text-slate-400 text-xs mt-1 line-clamp-2">
                      {item.message}
                    </p>
                  </div>
                </div>

                <div className="text-right shrink-0 font-mono text-[10px] text-slate-500 flex items-center gap-1">
                  <Clock className="w-3 h-3 text-slate-600" />
                  <span>{item.receivedAt || 'Recent'}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

    </div>
  );
}

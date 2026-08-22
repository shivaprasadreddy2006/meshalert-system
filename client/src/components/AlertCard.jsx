import React, { useState } from 'react';
import { 
  ShieldAlert, 
  Clock, 
  MapPin, 
  CheckCircle2, 
  Maximize2, 
  Copy, 
  Check, 
  Flame, 
  Users, 
  HeartPulse, 
  LogOut,
  Radio
} from 'lucide-react';

export default function AlertCard({ alert, onClearAlert, onOpenFullScreen, isAdmin = false }) {
  const [copied, setCopied] = useState(false);

  if (!alert) {
    return (
      <div className="bg-dark-900/80 border border-dark-700/80 rounded-2xl p-6 sm:p-8 text-center space-y-3 shadow-md">
        <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto">
          <CheckCircle2 className="w-6 h-6" />
        </div>
        <div className="space-y-1 max-w-sm mx-auto">
          <h3 className="text-sm sm:text-base font-bold text-slate-200">No Active Emergency Alert</h3>
          <p className="text-xs text-slate-400">
            BLE Mesh is quiet and monitoring for emergency broadcast beacons.
          </p>
        </div>
      </div>
    );
  }

  const isHighPriority = alert.priority === 'HIGH' || alert.priority === 'CRITICAL';
  const alertType = (alert.alertType || alert.type || 'GENERAL').toUpperCase();

  const handleCopy = () => {
    if (alert.message) {
      navigator.clipboard.writeText(alert.message);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getTypeIcon = () => {
    switch (alertType) {
      case 'FIRE': return <Flame className="w-4 h-4 text-rose-400" />;
      case 'STAMPEDE': return <Users className="w-4 h-4 text-amber-400" />;
      case 'MEDICAL': return <HeartPulse className="w-4 h-4 text-emerald-400" />;
      case 'EVACUATION': return <LogOut className="w-4 h-4 text-rose-400" />;
      default: return <ShieldAlert className="w-4 h-4 text-rose-400" />;
    }
  };

  return (
    <div className={`rounded-2xl border-2 p-4 sm:p-6 space-y-4 transition-all shadow-xl ${
      isHighPriority 
        ? 'bg-rose-950/30 border-rose-500/70 shadow-rose-950/50' 
        : 'bg-dark-900 border-dark-700/90 shadow-dark-950/60'
    }`}>
      
      {/* Header Badges */}
      <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-dark-700/60">
        <div className="flex items-center gap-2">
          {getTypeIcon()}
          <span className="text-xs font-mono font-bold text-white tracking-wide uppercase">
            {alertType} EMERGENCY
          </span>
        </div>

        <div className="flex items-center gap-2">
          {alert.priority && (
            <span className={`text-[11px] font-mono font-bold px-2.5 py-0.5 rounded-full border uppercase ${
              alert.priority === 'CRITICAL' ? 'bg-red-500/20 text-red-300 border-red-500/40 animate-pulse' :
              alert.priority === 'HIGH' ? 'bg-rose-500/20 text-rose-300 border-rose-500/40' :
              'bg-amber-500/20 text-amber-300 border-amber-500/40'
            }`}>
              {alert.priority}
            </span>
          )}

          {onOpenFullScreen && (
            <button
              onClick={onOpenFullScreen}
              className="p-1.5 rounded-lg bg-dark-800 hover:bg-dark-700 text-slate-300 hover:text-white border border-dark-600 transition"
              title="Expand Full Screen Alert"
            >
              <Maximize2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Message Body */}
      <div className="space-y-1.5">
        <p className="text-base sm:text-lg font-bold text-white bg-dark-950/90 p-4 rounded-xl border border-dark-700/60 leading-relaxed select-all">
          {alert.message}
        </p>
      </div>

      {/* Metadata Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs font-mono text-slate-300 pt-1">
        {alert.area && (
          <div className="flex items-center gap-2 bg-dark-950/60 p-2.5 rounded-xl border border-dark-700/40 truncate">
            <MapPin className="w-3.5 h-3.5 text-blue-400 shrink-0" />
            <span className="truncate">Area: <strong className="text-white">{alert.area}</strong></span>
          </div>
        )}

        <div className="flex items-center gap-2 bg-dark-950/60 p-2.5 rounded-xl border border-dark-700/40 truncate">
          <Clock className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span className="truncate">Time: <strong className="text-white">{alert.receivedAt || 'Just now'}</strong></span>
        </div>
      </div>

      {/* Footer Controls: Copy, Full Screen, Clear */}
      <div className="pt-2 border-t border-dark-700/60 flex flex-wrap items-center justify-between gap-2">
        <button
          onClick={handleCopy}
          className="px-3 py-1.5 text-xs font-mono font-semibold text-slate-300 hover:text-white bg-dark-800 hover:bg-dark-700 rounded-lg border border-dark-600 transition flex items-center gap-1.5"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? 'Copied' : 'Copy Text'}</span>
        </button>

        <div className="flex items-center gap-2">
          {onOpenFullScreen && (
            <button
              onClick={onOpenFullScreen}
              className="px-3 py-1.5 text-xs font-bold text-rose-300 hover:text-white bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 rounded-lg transition"
            >
              Full-Screen Mode
            </button>
          )}

          {isAdmin && onClearAlert && (
            <button
              onClick={onClearAlert}
              className="px-3 py-1.5 text-xs font-bold text-slate-300 hover:text-white bg-dark-800 hover:bg-dark-700 rounded-lg border border-dark-600 transition"
            >
              Clear Alert
            </button>
          )}
        </div>
      </div>

    </div>
  );
}

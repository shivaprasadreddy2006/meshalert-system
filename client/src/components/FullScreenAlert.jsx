import React, { useEffect, useState } from 'react';
import { 
  AlertTriangle, 
  Volume2, 
  VolumeX, 
  MapPin, 
  Clock, 
  Radio, 
  X, 
  CheckCircle2, 
  ShieldAlert,
  Flame,
  Users,
  HeartPulse,
  LogOut,
  BellRing
} from 'lucide-react';
import { stopEmergencyAlarm, toggleAudioMute, isAudioMuted } from '../services/audioAlarm';

export default function FullScreenAlert({ alert, onDismiss, onAcknowledge }) {
  const [muted, setMuted] = useState(isAudioMuted());

  useEffect(() => {
    // Lock body scrolling while full screen alert is open
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, []);

  if (!alert) return null;

  const isCritical = alert.priority === 'CRITICAL' || alert.priority === 'HIGH';
  const alertType = (alert.alertType || alert.type || 'GENERAL').toUpperCase();

  const handleToggleMute = () => {
    const newMuted = toggleAudioMute();
    setMuted(newMuted);
  };

  const handleSilence = () => {
    stopEmergencyAlarm();
  };

  const getAlertIcon = () => {
    switch (alertType) {
      case 'FIRE':
        return <Flame className="w-16 h-16 sm:w-20 sm:h-20 text-rose-500 animate-bounce" />;
      case 'STAMPEDE':
        return <Users className="w-16 h-16 sm:w-20 sm:h-20 text-amber-500 animate-bounce" />;
      case 'MEDICAL':
        return <HeartPulse className="w-16 h-16 sm:w-20 sm:h-20 text-emerald-400 animate-pulse" />;
      case 'EVACUATION':
        return <LogOut className="w-16 h-16 sm:w-20 sm:h-20 text-rose-400 animate-pulse" />;
      default:
        return <ShieldAlert className="w-16 h-16 sm:w-20 sm:h-20 text-rose-500 animate-pulse" />;
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/90 backdrop-blur-xl overflow-y-auto animate-in fade-in duration-300">
      
      {/* Pulsing Warning Backdrop Glow */}
      <div className={`fixed inset-0 pointer-events-none opacity-40 animate-pulse ${
        isCritical ? 'bg-gradient-to-b from-rose-900/60 via-black to-red-950/60' : 'bg-gradient-to-b from-amber-900/60 via-black to-orange-950/60'
      }`} />

      {/* Main Alert Modal Container */}
      <div className={`relative w-full max-w-2xl rounded-3xl border-4 p-6 sm:p-8 space-y-6 shadow-2xl transition-all my-auto ${
        isCritical 
          ? 'bg-[#0d070b] border-rose-500 shadow-rose-900/60' 
          : 'bg-[#0f0d07] border-amber-500 shadow-amber-900/60'
      }`}>

        {/* Top Action Bar */}
        <div className="flex items-center justify-between gap-2 border-b border-white/10 pb-4">
          <div className="flex items-center gap-2">
            <span className="flex h-3 w-3 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-rose-500"></span>
            </span>
            <span className="text-xs sm:text-sm font-mono font-black uppercase tracking-wider text-rose-400">
              LIVE EMERGENCY BROADCAST
            </span>
          </div>

          <div className="flex items-center gap-2">
            {/* Audio Mute/Silence Toggle */}
            <button
              onClick={handleToggleMute}
              className="p-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-white transition flex items-center gap-1 text-xs font-mono"
              title={muted ? "Unmute Alarm" : "Silence Alarm"}
            >
              {muted ? <VolumeX className="w-4 h-4 text-slate-400" /> : <Volume2 className="w-4 h-4 text-emerald-400 animate-pulse" />}
              <span className="hidden sm:inline">{muted ? "Muted" : "Siren ON"}</span>
            </button>

            {/* Minimize / Close */}
            <button
              onClick={() => {
                handleSilence();
                if (onDismiss) onDismiss();
              }}
              className="p-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-slate-300 hover:text-white transition"
              title="Minimize Alert"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Center Alert Icon & Category */}
        <div className="text-center space-y-3 pt-2">
          <div className="inline-flex items-center justify-center p-4 rounded-3xl bg-white/5 border border-white/10 shadow-inner">
            {getAlertIcon()}
          </div>

          <div className="space-y-1">
            <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full text-xs font-mono font-bold tracking-widest uppercase border border-white/20 bg-white/5 text-slate-200">
              PRIORITY: <span className={isCritical ? 'text-rose-400 font-extrabold' : 'text-amber-400 font-extrabold'}>{alert.priority || 'HIGH'}</span>
            </div>
            <h1 className="text-2xl sm:text-4xl font-black text-white uppercase tracking-tight">
              {alertType} ALERT
            </h1>
          </div>
        </div>

        {/* Message Highlight Box */}
        <div className="rounded-2xl bg-black/60 border-2 border-white/15 p-5 sm:p-6 text-center space-y-2 shadow-inner">
          <p className="text-lg sm:text-2xl font-black text-white leading-snug tracking-normal select-all">
            "{alert.message}"
          </p>
        </div>

        {/* Localization & Metadata Pills */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs sm:text-sm font-mono text-slate-300">
          <div className="flex items-center gap-2.5 bg-white/5 p-3 rounded-xl border border-white/10">
            <MapPin className="w-4 h-4 text-blue-400 shrink-0" />
            <span>Area: <strong className="text-white text-sm">{alert.area || 'Venue / Floor 1'}</strong></span>
          </div>

          <div className="flex items-center gap-2.5 bg-white/5 p-3 rounded-xl border border-white/10">
            <Clock className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Time: <strong className="text-white text-sm">{alert.receivedAt || 'Just Now'}</strong></span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="pt-2 flex flex-col sm:flex-row gap-3">
          <button
            onClick={() => {
              handleSilence();
              if (onAcknowledge) onAcknowledge();
            }}
            className="flex-1 py-4 px-6 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-sm sm:text-base flex items-center justify-center gap-2 shadow-lg shadow-emerald-900/40 transition active:scale-[0.98]"
          >
            <CheckCircle2 className="w-5 h-5" />
            <span>Acknowledge & Silence</span>
          </button>

          <button
            onClick={() => {
              handleSilence();
              if (onDismiss) onDismiss();
            }}
            className="py-4 px-6 rounded-2xl bg-white/10 hover:bg-white/20 text-slate-200 font-semibold text-sm sm:text-base transition"
          >
            Minimize to Dashboard
          </button>
        </div>

      </div>
    </div>
  );
}

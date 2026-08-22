import React, { useState } from 'react';
import { Radio, RefreshCw, Volume2, VolumeX, Bell, Smartphone, Globe } from 'lucide-react';
import { toggleAudioMute, isAudioMuted, playTestChime, unlockAudio } from '../services/audioAlarm';

export default function Navbar({ role, onSwitchRole, androidConnected, myPublicIp, androidDeviceIp, onOpenFullScreen, hasActiveAlert }) {
  const [muted, setMuted] = useState(isAudioMuted());

  const handleToggleMute = () => {
    unlockAudio();
    const newMuted = toggleAudioMute();
    setMuted(newMuted);
    if (!newMuted) playTestChime();
  };

  // "Your IP" = the browser device's public IP (detected via api.ipify.org)
  const myIpLabel = myPublicIp || 'Detecting…';

  return (
    <header className="bg-dark-900/95 backdrop-blur-md border-b border-dark-700/80 px-3 sm:px-6 py-3 sticky top-0 z-30">
      <div className="max-w-4xl mx-auto flex items-center justify-between gap-2">

        {/* Brand */}
        <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
          <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-600/30 shrink-0">
            <Radio className="w-4 h-4 sm:w-5 sm:h-5 animate-pulse" />
          </div>
          <div className="min-w-0">
            <h1 className="text-sm sm:text-base font-bold text-white tracking-tight truncate">
              Mesh Alert System
            </h1>
            <p className="text-[10px] sm:text-[11px] text-slate-400 truncate">BLE Mesh Broadcast Monitor</p>
          </div>
        </div>

        {/* Right side */}
        <div className="flex items-center gap-2 shrink-0">

          {/* This device's public IP badge */}
          <div
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-mono font-semibold border bg-blue-500/10 text-blue-400 border-blue-500/30"
            title={`Your device public IP: ${myIpLabel}`}
          >
            <Globe className="w-3 h-3 shrink-0" />
            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${myPublicIp ? 'bg-emerald-400' : 'bg-amber-400 animate-pulse'}`} />
            <span className="truncate max-w-[120px] sm:max-w-[180px]">{myIpLabel}</span>
          </div>

          {/* Android connection status badge */}
          <div
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-mono font-semibold border transition-all ${
              androidConnected
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                : 'bg-slate-800 text-slate-500 border-slate-700'
            }`}
            title={androidConnected ? `Android connected: ${androidDeviceIp || 'Unknown IP'}` : 'Android app not connected'}
          >
            <Smartphone className="w-3 h-3 shrink-0" />
            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${androidConnected ? 'bg-emerald-500 animate-pulse' : 'bg-slate-600'}`} />
            <span className="hidden sm:inline">{androidConnected ? 'Android 🟢' : 'Android ⚫'}</span>
          </div>

          {/* Audio Mute */}
          <button
            onClick={handleToggleMute}
            className={`p-2 rounded-lg border text-xs font-mono transition flex items-center gap-1 ${
              muted
                ? 'bg-dark-800 text-slate-400 border-dark-700 hover:text-slate-200'
                : 'bg-blue-500/10 text-blue-400 border-blue-500/30 hover:bg-blue-500/20'
            }`}
            title={muted ? 'Sound Muted' : 'Audio Alarm Enabled'}
          >
            {muted ? <VolumeX className="w-3.5 h-3.5" /> : <Volume2 className="w-3.5 h-3.5" />}
            <span className="hidden md:inline">{muted ? 'Muted' : 'Alarm ON'}</span>
          </button>

          {/* Full screen alert button */}
          {hasActiveAlert && onOpenFullScreen && (
            <button
              onClick={onOpenFullScreen}
              className="px-2.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold font-mono transition flex items-center gap-1 shadow-md shadow-rose-600/30 animate-pulse"
            >
              <Bell className="w-3.5 h-3.5" />
              <span className="hidden xs:inline">VIEW ALERT</span>
            </button>
          )}

          {/* Role switcher */}
          {role && (
            <button
              onClick={onSwitchRole}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-slate-300 hover:text-white bg-dark-800 hover:bg-dark-700 rounded-lg border border-dark-600 transition"
            >
              <RefreshCw className="w-3 h-3" />
              <span className="hidden sm:inline font-mono capitalize">{role}</span>
            </button>
          )}

        </div>
      </div>
    </header>
  );
}

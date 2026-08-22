import React, { useState } from 'react';
import { Radio, RefreshCw, Shield, User, Volume2, VolumeX, Bell, Smartphone, Globe } from 'lucide-react';
import { toggleAudioMute, isAudioMuted, playTestChime, unlockAudio } from '../services/audioAlarm';

export default function Navbar({ role, onSwitchRole, androidConnected, deviceIp, onOpenFullScreen, hasActiveAlert }) {
  const [muted, setMuted] = useState(isAudioMuted());

  const handleToggleMute = () => {
    unlockAudio();
    const newMuted = toggleAudioMute();
    setMuted(newMuted);
    if (!newMuted) {
      playTestChime();
    }
  };

  const displayIp = deviceIp && deviceIp !== '127.0.0.1' ? deviceIp : (androidConnected ? 'Connected' : 'Detecting IP...');

  return (
    <header className="bg-dark-900/95 backdrop-blur-md border-b border-dark-700/80 px-3 sm:px-6 py-3 sticky top-0 z-30">
      <div className="max-w-4xl mx-auto flex items-center justify-between gap-2">
        
        {/* Brand */}
        <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
          <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-600/30 shrink-0">
            <Radio className="w-4 h-4 sm:w-5 sm:h-5 animate-pulse" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5 sm:gap-2">
              <h1 className="text-sm sm:text-base font-bold text-white tracking-tight truncate">
                Mesh Alert System
              </h1>
            </div>
            <p className="text-[10px] sm:text-[11px] text-slate-400 truncate">BLE Mesh Broadcast Monitor</p>
          </div>
        </div>

        {/* Right side Actions */}
        <div className="flex items-center gap-2 shrink-0">

          {/* Connected Device IP Badge */}
          <div 
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-mono font-semibold border ${
              androidConnected 
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' 
                : 'bg-blue-500/10 text-blue-400 border-blue-500/30'
            }`}
            title={`Device IP: ${deviceIp || 'Detecting...'}`}
          >
            <Smartphone className="w-3 h-3 shrink-0" />
            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${androidConnected ? 'bg-emerald-500 animate-pulse' : 'bg-blue-400'}`} />
            <span className="truncate max-w-[110px] sm:max-w-[170px]">
              {displayIp}
            </span>
          </div>

          {/* Audio Alarm Mute/Unmute Button */}
          <button
            onClick={handleToggleMute}
            className={`p-2 rounded-lg border text-xs font-mono transition flex items-center gap-1 ${
              muted 
                ? 'bg-dark-800 text-slate-400 border-dark-700 hover:text-slate-200' 
                : 'bg-blue-500/10 text-blue-400 border-blue-500/30 hover:bg-blue-500/20'
            }`}
            title={muted ? "Sound Muted (Click to Unmute & Test)" : "Audio Alarm Enabled (Click to Mute)"}
          >
            {muted ? <VolumeX className="w-3.5 h-3.5" /> : <Volume2 className="w-3.5 h-3.5" />}
            <span className="hidden md:inline">{muted ? "Muted" : "Alarm ON"}</span>
          </button>

          {/* Full Screen Alert Button if alert active */}
          {hasActiveAlert && onOpenFullScreen && (
            <button
              onClick={onOpenFullScreen}
              className="px-2.5 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold font-mono transition flex items-center gap-1 shadow-md shadow-rose-600/30 animate-pulse"
              title="Show Full Screen Emergency Alert"
            >
              <Bell className="w-3.5 h-3.5" />
              <span className="hidden xs:inline">VIEW ALERT</span>
            </button>
          )}

          {/* Role Switcher */}
          {role && (
            <button
              onClick={onSwitchRole}
              className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-slate-300 hover:text-white bg-dark-800 hover:bg-dark-700 rounded-lg border border-dark-600 transition"
              title="Switch Dashboard View"
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

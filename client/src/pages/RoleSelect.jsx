import React from 'react';
import { Radio, Shield, User, ArrowRight, Smartphone, Globe } from 'lucide-react';
import { unlockAudio } from '../services/audioAlarm';

export default function RoleSelect({ onSelectRole, androidConnected, myPublicIp, androidDeviceIp }) {
  const handleSelect = (role) => {
    unlockAudio();
    onSelectRole(role);
  };

  return (
    <div className="min-h-[75vh] flex items-center justify-center p-3 sm:p-4">
      <div className="max-w-md w-full space-y-5 text-center">

        {/* Header */}
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-400 text-xs font-mono">
            <Radio className="w-3.5 h-3.5 animate-pulse" />
            Mesh Alert System • The Inevitables
          </div>
          <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
            Select Your View
          </h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Real-time BLE mesh emergency monitor &amp; broadcast station.
          </p>
        </div>

        {/* Status Cards */}
        <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-4 text-left space-y-3 shadow-lg">

          {/* This device's public IP */}
          <div className="flex items-center justify-between border-b border-dark-700/60 pb-3">
            <span className="text-xs font-mono font-bold uppercase text-slate-300 flex items-center gap-1.5">
              <Globe className="w-3.5 h-3.5 text-blue-400" />
              Your Device IP
            </span>
            <strong className={`text-sm font-bold font-mono px-2.5 py-1 rounded-lg border ${
              myPublicIp
                ? 'text-blue-300 bg-blue-500/10 border-blue-500/30'
                : 'text-amber-400 bg-amber-500/10 border-amber-500/30 animate-pulse'
            }`}>
              {myPublicIp || 'Detecting…'}
            </strong>
          </div>

          {/* Android App Connection */}
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono font-bold uppercase text-slate-300 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-emerald-400" />
              Android App
            </span>
            <div className="text-right">
              <span className={`text-[11px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                androidConnected
                  ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 animate-pulse'
                  : 'bg-slate-800 text-slate-500 border-slate-700'
              }`}>
                {androidConnected ? '🟢 Connected' : '⚫ Not Connected'}
              </span>
              {androidConnected && androidDeviceIp && (
                <p className="text-[10px] font-mono text-slate-500 mt-0.5">
                  {androidDeviceIp}
                </p>
              )}
              {!androidConnected && (
                <p className="text-[10px] text-slate-600 mt-0.5">
                  Open BLE Helper app → Connect Web Bridge
                </p>
              )}
            </div>
          </div>
        </div>

        {/* Role Choices */}
        <div className="grid grid-cols-1 gap-3.5 text-left">

          {/* Client */}
          <button
            onClick={() => handleSelect('client')}
            className="p-4 sm:p-5 rounded-2xl bg-dark-900 hover:bg-dark-800 border-2 border-dark-700/80 hover:border-blue-500 transition-all duration-200 flex items-center justify-between group shadow-xl active:scale-[0.98]"
          >
            <div className="flex items-center gap-3.5 sm:gap-4">
              <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-xl bg-blue-500/10 border border-blue-500/20 text-blue-400 flex items-center justify-center group-hover:scale-105 transition-transform shrink-0">
                <User className="w-5 h-5 sm:w-6 sm:h-6" />
              </div>
              <div className="min-w-0">
                <h3 className="text-sm sm:text-base font-bold text-white group-hover:text-blue-400 transition-colors">
                  1. Client / Public Monitor
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Receive emergency broadcasts &amp; evacuation guidance
                </p>
              </div>
            </div>
            <ArrowRight className="w-5 h-5 text-slate-600 group-hover:text-blue-400 group-hover:translate-x-1 transition-all shrink-0 ml-2" />
          </button>

          {/* Admin */}
          <button
            onClick={() => handleSelect('admin')}
            className="p-4 sm:p-5 rounded-2xl bg-dark-900 hover:bg-dark-800 border-2 border-dark-700/80 hover:border-amber-500 transition-all duration-200 flex items-center justify-between group shadow-xl active:scale-[0.98]"
          >
            <div className="flex items-center gap-3.5 sm:gap-4">
              <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400 flex items-center justify-center group-hover:scale-105 transition-transform shrink-0">
                <Shield className="w-5 h-5 sm:w-6 sm:h-6" />
              </div>
              <div className="min-w-0">
                <h3 className="text-sm sm:text-base font-bold text-white group-hover:text-amber-400 transition-colors">
                  2. Incident Control / Admin
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Manage incident alerts &amp; monitor Android bridge
                </p>
              </div>
            </div>
            <ArrowRight className="w-5 h-5 text-slate-600 group-hover:text-amber-400 group-hover:translate-x-1 transition-all shrink-0 ml-2" />
          </button>

        </div>

      </div>
    </div>
  );
}

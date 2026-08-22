import React from 'react';
import { Radio, Shield, User, ArrowRight } from 'lucide-react';
import { unlockAudio } from '../services/audioAlarm';

export default function RoleSelect({ onSelectRole, androidConnected }) {
  const handleSelect = (role) => {
    unlockAudio();
    onSelectRole(role);
  };

  return (
    <div className="min-h-[75vh] flex items-center justify-center p-3 sm:p-4">
      <div className="max-w-md w-full space-y-6 text-center">
        
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
            Choose your interface mode to monitor localized BLE mesh alerts.
          </p>
        </div>

        {/* 2 Choices: Client vs Admin */}
        <div className="grid grid-cols-1 gap-3.5 text-left">
          
          {/* 1. Client Choice */}
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
                  Receive emergency broadcasts & evacuation guidance
                </p>
              </div>
            </div>
            <ArrowRight className="w-5 h-5 text-slate-600 group-hover:text-blue-400 group-hover:translate-x-1 transition-all shrink-0 ml-2" />
          </button>

          {/* 2. Admin Choice */}
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
                  Manage incident alerts & monitor Android bridge
                </p>
              </div>
            </div>
            <ArrowRight className="w-5 h-5 text-slate-600 group-hover:text-amber-400 group-hover:translate-x-1 transition-all shrink-0 ml-2" />
          </button>

        </div>

        {/* Live Status indicator */}
        <div className="text-xs font-mono text-slate-400 flex items-center justify-center gap-2 pt-1">
          <span className={`w-2 h-2 rounded-full ${androidConnected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'}`} />
          <span>Android BLE Bridge: <strong className={androidConnected ? 'text-emerald-400' : 'text-rose-400'}>{androidConnected ? 'Connected 🟢' : 'Disconnected 🔴'}</strong></span>
        </div>

      </div>
    </div>
  );
}

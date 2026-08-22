import React from 'react';

export default function StatusBadge({ label, isConnected, activeText = 'Connected', inactiveText = 'Disconnected', subtitle = null }) {
  return (
    <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-3.5 sm:p-4 flex items-center justify-between shadow-sm gap-3">
      <div className="min-w-0">
        <span className="text-xs sm:text-sm font-semibold text-slate-200 block truncate">{label}</span>
        {subtitle && <span className="text-[11px] text-slate-400 block truncate">{subtitle}</span>}
      </div>
      
      <div className={`flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3 py-1 rounded-full text-xs font-mono font-bold border transition-colors shrink-0 ${
        isConnected 
          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' 
          : 'bg-rose-500/10 text-rose-400 border-rose-500/30'
      }`}>
        <span className={`w-2 h-2 rounded-full shrink-0 ${isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'}`} />
        <span>{isConnected ? activeText : inactiveText}</span>
      </div>
    </div>
  );
}

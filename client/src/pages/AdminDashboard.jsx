import React, { useState, useEffect } from 'react';
import StatusBadge from '../components/StatusBadge';
import AlertCard from '../components/AlertCard';
import { Send, Zap, Trash2, Radio, Flame, Users, HeartPulse, LogOut, Info, ShieldAlert, History, Smartphone, Globe, RefreshCw, Check } from 'lucide-react';
import { unlockAudio } from '../services/audioAlarm';

export default function AdminDashboard({ 
  androidConnected, 
  targetDeviceIp = '127.0.0.1', 
  targetDevicePort = 7000, 
  detectedClientIp = null, 
  onSetTargetIp, 
  onAutoDetectIp, 
  alert, 
  alertHistory = [], 
  onClearAlert, 
  onOpenFullScreen 
}) {
  const [customIp, setCustomIp] = useState(targetDeviceIp);
  const [customPort, setCustomPort] = useState(targetDevicePort);
  const [testArea, setTestArea] = useState('Floor 1');
  const [testPriority, setTestPriority] = useState('HIGH');
  const [testType, setTestType] = useState('FIRE');
  const [testMsg, setTestMsg] = useState('Fire detected on Floor 1. Move towards North Exit. Do not use elevators.');
  const [isSending, setIsSending] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  useEffect(() => {
    setCustomIp(targetDeviceIp);
    setCustomPort(targetDevicePort);
  }, [targetDeviceIp, targetDevicePort]);

  const handleSaveIp = (e) => {
    if (e) e.preventDefault();
    if (onSetTargetIp && customIp) {
      onSetTargetIp(customIp.trim(), customPort);
      setSavedSuccess(true);
      setTimeout(() => setSavedSuccess(false), 2000);
    }
  };

  const handleUseMyIp = () => {
    if (detectedClientIp) {
      setCustomIp(detectedClientIp);
      if (onSetTargetIp) {
        onSetTargetIp(detectedClientIp, customPort);
        setSavedSuccess(true);
        setTimeout(() => setSavedSuccess(false), 2000);
      }
    } else if (onAutoDetectIp) {
      onAutoDetectIp();
    }
  };

  const presets = [
    {
      label: '🔥 Fire Alarm',
      type: 'FIRE',
      priority: 'HIGH',
      area: 'Floor 1 - North Wing',
      msg: 'Fire detected on Floor 1. Move towards North Exit immediately. Do not use elevators.'
    },
    {
      label: '⚠️ Stampede / Surge',
      type: 'STAMPEDE',
      priority: 'CRITICAL',
      area: 'Gate 4 Concourse',
      msg: 'High crowd density detected at Gate 4. Divert to Gates 2 & 3. Follow steward directions.'
    },
    {
      label: '🏥 Medical Alert',
      type: 'MEDICAL',
      priority: 'HIGH',
      area: 'Central Plaza',
      msg: 'Medical emergency reported in Central Plaza. First aid team dispatched. Clear access corridor.'
    },
    {
      label: '🚪 Full Evacuation',
      type: 'EVACUATION',
      priority: 'CRITICAL',
      area: 'All Sectors',
      msg: 'Immediate evacuation required. Proceed calmly to nearest assembly points.'
    }
  ];

  const handleApplyPreset = (p) => {
    setTestType(p.type);
    setTestPriority(p.priority);
    setTestArea(p.area);
    setTestMsg(p.msg);
  };

  const handleTriggerAlert = async (e) => {
    if (e) e.preventDefault();
    unlockAudio();
    setIsSending(true);
    try {
      const BACKEND_URL = window.location.hostname === 'localhost' 
        ? 'http://localhost:5000' 
        : `http://${window.location.hostname}:5000`;

      await fetch(`${BACKEND_URL}/api/alert`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: 'ALERT',
          alertType: testType,
          priority: testPriority,
          message: testMsg,
          area: testArea,
          timestamp: new Date().toISOString()
        })
      });
    } catch (err) {
      console.error('Failed to trigger alert:', err);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6 pb-8">
      
      {/* Header */}
      <div>
        <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight">
          Incident Control & Device Bridge
        </h2>
        <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
          Backend running on Cloud/Railway — connects to your phone's TCP Server on Port 7000.
        </p>
      </div>

      {/* Connection Statuses */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <StatusBadge
          label="Android TCP Server Connection"
          isConnected={androidConnected}
          activeText="Connected 🟢"
          inactiveText="Reconnecting 🔴"
          subtitle={`Targeting ${targetDeviceIp}:${targetDevicePort}`}
        />

        <StatusBadge
          label="Detected Client Device IP"
          isConnected={Boolean(detectedClientIp)}
          activeText={detectedClientIp || 'Detected'}
          inactiveText="Detecting..."
          subtitle="Your current device IP seen by backend"
        />
      </div>

      {/* Target Device IP Configuration Card */}
      <div className="bg-dark-900/90 border border-dark-700/80 rounded-2xl p-4 sm:p-5 space-y-3.5 shadow-lg">
        <div className="flex items-center justify-between pb-2 border-b border-dark-700/60">
          <span className="text-xs font-mono font-bold uppercase text-slate-300 flex items-center gap-1.5">
            <Smartphone className="w-3.5 h-3.5 text-emerald-400" />
            Target Android Device IP Binding
          </span>
          <span className="text-[10px] font-mono text-slate-400">Railway $\rightarrow$ Android TCP</span>
        </div>

        <form onSubmit={handleSaveIp} className="space-y-3 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="sm:col-span-2">
              <label className="block text-slate-400 font-semibold mb-1">Android Phone IP Address</label>
              <input
                type="text"
                value={customIp}
                onChange={(e) => setCustomIp(e.target.value)}
                placeholder="e.g. 103.21.x.x or 192.168.1.50"
                className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2 text-white font-mono focus:outline-none focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-slate-400 font-semibold mb-1">TCP Port</label>
              <input
                type="number"
                value={customPort}
                onChange={(e) => setCustomPort(Number(e.target.value))}
                placeholder="7000"
                className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2 text-white font-mono focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 pt-1">
            <button
              type="submit"
              className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl text-xs transition flex items-center gap-1.5 shadow-md shadow-blue-600/30"
            >
              {savedSuccess ? <Check className="w-3.5 h-3.5" /> : <RefreshCw className="w-3.5 h-3.5" />}
              <span>{savedSuccess ? 'Connected Target Updated!' : 'Set Target IP & Connect'}</span>
            </button>

            {detectedClientIp && (
              <button
                type="button"
                onClick={handleUseMyIp}
                className="px-3.5 py-2 bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 rounded-xl text-xs font-semibold transition flex items-center gap-1.5"
              >
                <Globe className="w-3.5 h-3.5" />
                <span>🎯 Use My Detected Device IP ({detectedClientIp})</span>
              </button>
            )}
          </div>
        </form>
      </div>

      {/* Active Alert Monitor */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400">
          <span>Current Active Alert</span>
          {alert && (
            <button
              onClick={onClearAlert}
              className="text-slate-400 hover:text-rose-400 flex items-center gap-1 transition text-[11px]"
            >
              <Trash2 className="w-3 h-3" />
              <span>Clear Active Alert</span>
            </button>
          )}
        </div>
        <AlertCard 
          alert={alert} 
          onClearAlert={onClearAlert} 
          onOpenFullScreen={onOpenFullScreen}
          isAdmin={true} 
        />
      </div>

      {/* Quick Dispatch Presets */}
      <div className="bg-dark-900/90 border border-dark-700/80 rounded-2xl p-4 sm:p-5 space-y-3">
        <div className="flex items-center justify-between pb-2 border-b border-dark-700/60">
          <span className="text-xs font-mono font-bold uppercase text-slate-300 flex items-center gap-1.5">
            <Zap className="w-3.5 h-3.5 text-amber-400" />
            Quick Incident Presets
          </span>
          <span className="text-[10px] font-mono text-slate-400">Tap to load preset</span>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {presets.map((p, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => handleApplyPreset(p)}
              className="p-2.5 rounded-xl bg-dark-950 hover:bg-dark-800 border border-dark-700 hover:border-amber-500/50 text-left transition space-y-1 group"
            >
              <div className="text-xs font-bold text-slate-200 group-hover:text-amber-400 transition-colors truncate">
                {p.label}
              </div>
              <div className="text-[10px] text-slate-400 truncate">{p.area}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Dispatch Custom Alert Form */}
      <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-4 sm:p-5 space-y-4 shadow-lg">
        <div className="flex items-center justify-between pb-2 border-b border-dark-700/60">
          <span className="text-xs font-mono font-bold uppercase text-slate-300 flex items-center gap-1.5">
            <Radio className="w-3.5 h-3.5 text-blue-400" />
            Broadcast Emergency Alert
          </span>
          <span className="text-[10px] font-mono text-slate-400">Dispatches to all clients</span>
        </div>

        <form onSubmit={handleTriggerAlert} className="space-y-3.5 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-slate-400 font-semibold mb-1">Incident Type</label>
              <select
                value={testType}
                onChange={(e) => setTestType(e.target.value)}
                className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              >
                <option value="FIRE">🔥 FIRE</option>
                <option value="STAMPEDE">⚠️ STAMPEDE</option>
                <option value="MEDICAL">🏥 MEDICAL</option>
                <option value="EVACUATION">🚪 EVACUATION</option>
                <option value="GENERAL">📢 GENERAL</option>
              </select>
            </div>

            <div>
              <label className="block text-slate-400 font-semibold mb-1">Severity / Priority</label>
              <select
                value={testPriority}
                onChange={(e) => setTestPriority(e.target.value)}
                className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              >
                <option value="CRITICAL">CRITICAL (High Siren)</option>
                <option value="HIGH">HIGH Priority</option>
                <option value="MEDIUM">MEDIUM Priority</option>
                <option value="LOW">LOW / Advisory</option>
              </select>
            </div>

            <div>
              <label className="block text-slate-400 font-semibold mb-1">Target Area / Zone</label>
              <input
                type="text"
                value={testArea}
                onChange={(e) => setTestArea(e.target.value)}
                placeholder="e.g. Floor 1 / Gate 4"
                className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2 text-white focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-slate-400 font-semibold mb-1">Evacuation & Safety Instructions</label>
            <textarea
              rows={2}
              value={testMsg}
              onChange={(e) => setTestMsg(e.target.value)}
              placeholder="Enter clear instruction message..."
              className="w-full bg-dark-950 border border-dark-700 rounded-xl px-3 py-2.5 text-white focus:outline-none focus:border-blue-500 font-medium text-xs sm:text-sm"
              required
            />
          </div>

          <button
            type="submit"
            disabled={isSending}
            className="w-full py-3.5 px-4 rounded-xl bg-gradient-to-r from-rose-600 to-red-600 hover:from-rose-500 hover:to-red-500 text-white font-bold text-xs sm:text-sm transition flex items-center justify-center gap-2 shadow-lg shadow-rose-900/30 disabled:opacity-50 active:scale-[0.99]"
          >
            <Send className="w-4 h-4" />
            <span>{isSending ? 'Dispatching Broadcast...' : '🚨 Broadcast Emergency Alert & Siren'}</span>
          </button>
        </form>
      </div>

      {/* History Log */}
      {alertHistory && alertHistory.length > 0 && (
        <div className="space-y-2">
          <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400">
            <span className="flex items-center gap-1.5">
              <History className="w-3.5 h-3.5" />
              Session Alert History ({alertHistory.length})
            </span>
          </div>

          <div className="space-y-2">
            {alertHistory.map((item, idx) => (
              <div 
                key={item.id || idx}
                className="bg-dark-900/80 border border-dark-700/70 rounded-xl p-3 flex items-center justify-between gap-3 text-xs"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-white uppercase text-[11px] sm:text-xs">
                      {item.alertType || 'ALERT'}
                    </span>
                    <span className="text-[10px] font-mono text-slate-400 px-1.5 py-0.2 rounded bg-dark-950 border border-dark-700">
                      {item.priority || 'MEDIUM'}
                    </span>
                    {item.area && (
                      <span className="text-[11px] text-blue-400 font-mono truncate">
                        • {item.area}
                      </span>
                    )}
                  </div>
                  <p className="text-slate-300 text-xs truncate mt-0.5">{item.message}</p>
                </div>
                <span className="text-[10px] font-mono text-slate-500 shrink-0">{item.receivedAt}</span>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  );
}

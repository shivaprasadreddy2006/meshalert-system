import React, { useState } from 'react';
import StatusBadge from '../components/StatusBadge';
import AlertCard from '../components/AlertCard';
import { Send, Zap, Trash2, Radio, Flame, Users, HeartPulse, LogOut, Info, ShieldAlert, History, Smartphone, Globe } from 'lucide-react';
import { unlockAudio } from '../services/audioAlarm';

export default function AdminDashboard({ 
  androidConnected, 
  androidDeviceIp,
  myPublicIp, 
  alert, 
  alertHistory = [], 
  onClearAlert, 
  onOpenFullScreen 
}) {
  const [testArea, setTestArea] = useState('Floor 1');
  const [testPriority, setTestPriority] = useState('HIGH');
  const [testType, setTestType] = useState('FIRE');
  const [testMsg, setTestMsg] = useState('Fire detected on Floor 1. Move towards North Exit. Do not use elevators.');
  const [isSending, setIsSending] = useState(false);

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

  const handleSendTestAlert = async (e) => {
    e.preventDefault();
    if (!testMsg.trim()) return;

    unlockAudio();
    setIsSending(true);

    try {
      const BACKEND_URL = window.location.port === '5173' ? 'http://localhost:5000' : window.location.origin;
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
          Incident Control Center
        </h2>
        <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
          Broadcast emergency instructions and monitor BLE mesh relay network.
        </p>
      </div>

      {/* Connection Statuses */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <StatusBadge
          label="Android BLE Mesh Relay"
          isConnected={androidConnected}
          activeText="Online & Relaying 🟢"
          inactiveText="Standby / Ready ⚪"
          subtitle={androidConnected ? `Active device: ${androidDeviceIp || 'Mesh Node'}` : 'Waiting for Android app'}
        />

        <StatusBadge
          label="Your Device IP"
          isConnected={Boolean(myPublicIp)}
          activeText={myPublicIp ? `${myPublicIp} 🟢` : 'Detecting...'}
          inactiveText="Detecting..."
          subtitle="Public network address"
        />
      </div>

      {/* Active Alert Monitor */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-mono font-bold uppercase text-slate-400">
          <span>Current Active Alert</span>
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
          <div className="space-y-3">
            <AlertCard alert={alert} onOpenFullScreen={onOpenFullScreen} />
            <button
              onClick={onClearAlert}
              className="w-full py-2.5 px-4 rounded-xl bg-dark-800 hover:bg-dark-700 border border-dark-600 text-slate-300 hover:text-white text-xs font-bold font-mono transition flex items-center justify-center gap-2 shadow-sm"
            >
              <Trash2 className="w-4 h-4 text-slate-400" />
              CLEAR ACTIVE ALERT & SILENCE SIREN
            </button>
          </div>
        ) : (
          <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-5 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
            <Info className="w-4 h-4 text-blue-400 shrink-0" />
            <span>No emergency broadcast currently active.</span>
          </div>
        )}
      </div>

      {/* Manual Broadcast Trigger Box */}
      <div className="bg-dark-900 border border-dark-700/80 rounded-2xl p-4 sm:p-6 space-y-4 shadow-lg">
        <div className="flex items-center justify-between border-b border-dark-700/60 pb-3">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-amber-400" />
            <h3 className="text-sm sm:text-base font-bold text-white">
              Dispatch Emergency Broadcast
            </h3>
          </div>
          <span className="text-[10px] font-mono text-slate-400 uppercase tracking-wider">
            All Dashboards & Mobile
          </span>
        </div>

        {/* Quick Presets */}
        <div className="space-y-1.5">
          <label className="text-[11px] font-mono text-slate-400 uppercase tracking-wider">
            Quick Emergency Presets:
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {presets.map((p, i) => (
              <button
                key={i}
                type="button"
                onClick={() => handleApplyPreset(p)}
                className="py-1.5 px-2 rounded-lg bg-dark-800 hover:bg-dark-700 border border-dark-600/80 text-[11px] font-medium text-slate-300 hover:text-white transition text-left truncate"
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        {/* Dispatch Form */}
        <form onSubmit={handleSendTestAlert} className="space-y-3 pt-1">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-[11px] font-mono text-slate-400 mb-1">INCIDENT TYPE</label>
              <select
                value={testType}
                onChange={(e) => setTestType(e.target.value)}
                className="w-full bg-dark-950 border border-dark-600 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-blue-500 font-mono"
              >
                <option value="FIRE">🔥 FIRE</option>
                <option value="STAMPEDE">⚠️ STAMPEDE</option>
                <option value="MEDICAL">🏥 MEDICAL</option>
                <option value="EVACUATION">🚪 EVACUATION</option>
                <option value="GENERAL">📢 GENERAL</option>
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-mono text-slate-400 mb-1">PRIORITY</label>
              <select
                value={testPriority}
                onChange={(e) => setTestPriority(e.target.value)}
                className="w-full bg-dark-950 border border-dark-600 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-blue-500 font-mono"
              >
                <option value="CRITICAL">🔴 CRITICAL</option>
                <option value="HIGH">🟠 HIGH</option>
                <option value="MEDIUM">🟡 MEDIUM</option>
                <option value="LOW">🔵 LOW</option>
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-mono text-slate-400 mb-1">AFFECTED AREA</label>
              <input
                type="text"
                value={testArea}
                onChange={(e) => setTestArea(e.target.value)}
                placeholder="e.g. Floor 1"
                className="w-full bg-dark-950 border border-dark-600 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-blue-500 font-mono"
              />
            </div>
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">ALERT MESSAGE</label>
            <textarea
              value={testMsg}
              onChange={(e) => setTestMsg(e.target.value)}
              rows={3}
              placeholder="Enter clear evacuation or safety instructions..."
              className="w-full bg-dark-950 border border-dark-600 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-blue-500 resize-none font-sans"
            />
          </div>

          <button
            type="submit"
            disabled={isSending}
            className="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-bold font-mono transition flex items-center justify-center gap-2 shadow-md shadow-blue-600/30 active:scale-[0.99]"
          >
            <Send className="w-4 h-4" />
            {isSending ? 'DISPATCHING...' : 'DISPATCH EMERGENCY BROADCAST'}
          </button>
        </form>
      </div>

      {/* Broadcast History */}
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
                className="bg-dark-900 border border-dark-700/60 rounded-xl p-3 flex items-center justify-between gap-3 text-xs"
              >
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-white uppercase text-[11px] font-mono">
                      {item.alertType || 'ALERT'}
                    </span>
                    <span className="text-slate-500">•</span>
                    <span className="text-slate-300 font-medium text-[11px]">
                      {item.area || 'Floor 1'}
                    </span>
                  </div>
                  <p className="text-slate-400 text-xs mt-0.5 truncate max-w-md">
                    {item.message}
                  </p>
                </div>
                <div className="text-right shrink-0 font-mono text-[10px] text-slate-500">
                  {item.receivedAt || 'Recent'}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

    </div>
  );
}

// Web Audio API Emergency Alarm Synthesizer & Mobile Haptics

let audioCtx = null;
let sirenOsc = null;
let sirenGain = null;
let sirenTimer = null;
let isPlaying = false;
let isMuted = false;

function getAudioContext() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume().catch(() => {});
  }
  return audioCtx;
}

// User interaction helper to unlock audio
export function unlockAudio() {
  const ctx = getAudioContext();
  if (ctx && ctx.state === 'suspended') {
    ctx.resume().catch(() => {});
  }
}

export function isAudioMuted() {
  return isMuted;
}

export function setAudioMuted(muted) {
  isMuted = muted;
  if (isMuted) {
    stopEmergencyAlarm();
  }
}

export function toggleAudioMute() {
  setAudioMuted(!isMuted);
  return isMuted;
}

/**
 * Starts continuous alternating emergency siren audio and mobile vibration.
 */
export function playEmergencyAlarm() {
  if (isMuted || isPlaying) return;

  try {
    const ctx = getAudioContext();
    if (!ctx) return;

    isPlaying = true;

    // Create oscillator and gain node
    sirenOsc = ctx.createOscillator();
    sirenGain = ctx.createGain();

    sirenOsc.type = 'sawtooth';
    sirenGain.gain.setValueAtTime(0.15, ctx.currentTime);

    // Two-tone siren frequency oscillation (880Hz to 660Hz)
    let high = true;
    sirenOsc.frequency.setValueAtTime(880, ctx.currentTime);

    sirenOsc.connect(sirenGain);
    sirenGain.connect(ctx.destination);
    sirenOsc.start();

    sirenTimer = setInterval(() => {
      if (!sirenOsc || !isPlaying) return;
      high = !high;
      try {
        const targetFreq = high ? 880 : 660;
        sirenOsc.frequency.setTargetAtTime(targetFreq, ctx.currentTime, 0.08);
      } catch (e) {
        clearInterval(sirenTimer);
      }
    }, 450);

    // Mobile vibration pattern: [vibrate, pause, vibrate, pause, ...]
    if ('vibrate' in navigator) {
      navigator.vibrate([500, 200, 500, 200, 800]);
    }
  } catch (err) {
    console.warn('Web Audio Alarm error:', err);
    isPlaying = false;
  }
}

/**
 * Stops continuous alarm sound.
 */
export function stopEmergencyAlarm() {
  isPlaying = false;
  if (sirenTimer) {
    clearInterval(sirenTimer);
    sirenTimer = null;
  }

  if (sirenOsc) {
    try {
      sirenOsc.stop();
      sirenOsc.disconnect();
    } catch (ignored) {}
    sirenOsc = null;
  }

  if (sirenGain) {
    try {
      sirenGain.disconnect();
    } catch (ignored) {}
    sirenGain = null;
  }

  if ('vibrate' in navigator) {
    navigator.vibrate(0);
  }
}

/**
 * Plays a quick test chime.
 */
export function playTestChime() {
  try {
    const ctx = getAudioContext();
    if (!ctx) return;

    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
    osc.frequency.setValueAtTime(880, ctx.currentTime + 0.12); // A5

    gain.gain.setValueAtTime(0.2, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.5);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + 0.5);

    if ('vibrate' in navigator) {
      navigator.vibrate(100);
    }
  } catch (err) {
    console.warn('Test chime error:', err);
  }
}

export function isAlarmActive() {
  return isPlaying;
}

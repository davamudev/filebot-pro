"""Generates the soundtrack of the promo video (public/music.wav) from scratch, so there are no licensing issues.

100 BPM, A minor (Am - F - C - G), one bar = 72 frames at 30 fps. The section changes follow src/timeline.json:
the groove starts with the logo and the last chord lands on the closing scene. Needs only numpy.
"""
import json
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
timeline = json.loads((ROOT / 'src' / 'timeline.json').read_text())

SR = 44100
FPS = timeline['fps']
BEAT = 60 / 100
BAR = BEAT * 4

scenes = list(timeline['scenes'].items())
starts = []
t = 0
for i, (name, length) in enumerate(scenes):
    starts.append((name, t / FPS))
    t += length - timeline['transition']
total = (t + timeline['transition']) / FPS
start_of = dict(starts)
GROOVE = start_of['logo']           # impact + groove
END = start_of['cta']               # final chord
LENGTH = total + 0.5

N = int(LENGTH * SR)
left = np.zeros(N)
right = np.zeros(N)
rng = np.random.default_rng(7)


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def add(sig, at, gain=1.0, pan=0.0, bus=None):
    i = int(at * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    l_gain = gain * np.cos((pan + 1) * np.pi / 4)
    r_gain = gain * np.sin((pan + 1) * np.pi / 4)
    (bus or (left, right))[0][i:i + len(sig)] += sig * l_gain
    (bus or (left, right))[1][i:i + len(sig)] += sig * r_gain


def lowpass(x, n):
    """Moving average, cheap low-pass filter."""
    c = np.cumsum(np.concatenate([[0], x]))
    y = (c[n:] - c[:-n]) / n
    return np.concatenate([y, np.zeros(n - 1)])


def env(n, attack, release, total_len):
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(attack, 1e-4))
    rel_start = total_len - release
    e *= np.clip(1 - (t - rel_start) / release, 0, 1)
    return e


def pad(f, dur, bright=1.0):
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    out_l = np.zeros(n)
    out_r = np.zeros(n)
    for detune, side in ((0.9985, 0), (1.0015, 1)):
        for h in range(1, 8):
            a = (1 / h ** 1.8) * (bright if h > 2 else 1)
            s = a * np.sin(2 * np.pi * f * detune * h * t + rng.uniform(0, 6.28))
            (out_l if side == 0 else out_r)[:] += s
    e = env(n, 0.45, 1.2, dur + 1.2) * (1 + 0.06 * np.sin(2 * np.pi * 0.3 * t))
    return out_l * e, out_r * e


def pluck(f, decay=6.0):
    n = int(0.6 * SR)
    t = np.arange(n) / SR
    s = sum((1 / h) * np.sin(2 * np.pi * f * h * t) * np.exp(-t * (decay + h * 4)) for h in range(1, 7))
    return s * np.minimum(1, t / 0.002)


def bass(f, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) + 0.12 * np.sin(6 * np.pi * f * t)
    return s * np.minimum(1, t / 0.004) * np.exp(-t * 3.5) * env(n, 0.001, 0.03, dur)


def kick(length=0.45, low=45, punch=90):
    n = int(length * SR)
    t = np.arange(n) / SR
    freq = low + punch * np.exp(-t * 32)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.exp(-t * (7 if length < 1 else 2.2)) + 0.3 * rng.standard_normal(n) * np.exp(-t * 300)


def noise_hit(length, decay, smooth_hi=1, smooth_lo=0):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = rng.standard_normal(n)
    if smooth_lo:
        x = x - lowpass(x, smooth_lo)
    if smooth_hi > 1:
        x = lowpass(x, smooth_hi)
    return x * np.exp(-t * decay)


def whoosh(length=0.55):
    n = int(length * SR)
    t = np.arange(n) / SR
    x = lowpass(rng.standard_normal(n), 6) - lowpass(rng.standard_normal(n), 60) * 0.5
    shape = np.sin(np.pi * np.clip(t / length, 0, 1)) ** 2
    return x * shape


CHORDS = [
    (45, [57, 60, 64, 67]),   # Am7
    (41, [53, 57, 60, 64]),   # Fmaj7
    (48, [55, 60, 64, 67]),   # C
    (43, [55, 59, 62, 67]),   # G
]
ARP = [0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 3, 2, 3, 1, 2]

pad_bus = (np.zeros(N), np.zeros(N))
fx_bus = (np.zeros(N), np.zeros(N))
duck = np.ones(N)

bars = int(np.ceil(END / BAR))
for b in range(bars):
    at = b * BAR
    root, notes = CHORDS[b % 4]
    groove = at >= GROOVE - 0.01
    section = (at - GROOVE) / BAR  # bars since the groove started
    breakdown = groove and 14 <= section < 16

    for m in notes:
        l, r = pad(midi(m), BAR, bright=0.6 if not groove else 1.0)
        add(l, at, 0.05, -0.6, pad_bus)
        add(r, at, 0.05, 0.6, pad_bus)

    if not groove:
        # Intro: ticking clock on eighths, a little chaotic
        for i in range(8):
            blip = np.sin(2 * np.pi * (1900 + 300 * (i % 3)) * np.arange(int(0.04 * SR)) / SR) * np.exp(-np.arange(int(0.04 * SR)) / SR * 90)
            add(blip, at + i * BEAT / 2, 0.06, 0.5 if i % 2 else -0.5)
        continue

    for beat in range(4):
        bt = at + beat * BEAT
        if not breakdown:
            add(kick(), bt, 0.9)
            k = int(bt * SR)
            span = np.arange(min(int(BEAT * SR), N - k)) / SR
            duck[k:k + len(span)] = np.minimum(duck[k:k + len(span)], 1 - 0.55 * np.exp(-span * 9))
        if section >= 2 and not breakdown:
            add(noise_hit(0.07, 70, smooth_lo=4), bt + BEAT / 2, 0.12, 0.3)
            add(noise_hit(0.04, 120, smooth_lo=3), bt + BEAT * 0.75, 0.05, -0.3)
        if section >= 10 and beat % 2 == 1 and not breakdown:
            for d in (0, 0.012, 0.025):
                add(noise_hit(0.18, 22, smooth_hi=3, smooth_lo=40), bt + d, 0.22, 0.1)

    # Bass on eighths, ducked by the kick
    for i in range(8):
        add(bass(midi(root), BEAT / 2 * 0.9), at + i * BEAT / 2, 0.32 if not breakdown else 0.15)

    # Arpeggio from the third bar of the groove
    if section >= 2:
        for i, idx in enumerate(ARP):
            f = midi(notes[idx] + 12)
            add(pluck(f, decay=9 if breakdown else 6), at + i * BEAT / 4, 0.06 if i % 4 == 0 else 0.04, -0.45 if i % 2 else 0.45, fx_bus)

    # Riser into the return of the drums after the breakdown
    if section == 15:
        n = int(BAR * SR)
        tt = np.arange(n) / SR
        add(lowpass(rng.standard_normal(n), 4) * (tt / BAR) ** 2, at, 0.12)

# Riser + impact on the logo
n = int(2.0 * SR)
tt = np.arange(n) / SR
add(lowpass(rng.standard_normal(n), 5) * (tt / 2.0) ** 3, GROOVE - 2.0, 0.25)
add(kick(2.5, low=38, punch=70), GROOVE, 1.0)
add(noise_hit(2.5, 1.8, smooth_lo=2), GROOVE, 0.12)

# Final chord on the closing scene: C major with a big hit, then it rings out
add(kick(3.0, low=36, punch=70), END, 1.0)
add(noise_hit(3.0, 1.3, smooth_lo=2), END, 0.12)
for m in [48, 55, 60, 64, 67, 72, 74]:
    l, r = pad(midi(m), LENGTH - END - 1.6)
    add(l, END, 0.05, -0.6, pad_bus)
    add(r, END, 0.05, 0.6, pad_bus)
add(np.sin(2 * np.pi * midi(36) * np.arange(int(4 * SR)) / SR) * np.exp(-np.arange(int(4 * SR)) / SR * 0.8), END, 0.3)
for i, m in enumerate([72, 76, 79, 84]):
    add(pluck(midi(m), decay=3), END + i * BEAT / 2, 0.06, -0.4 + i * 0.27, fx_bus)

# Whooshes on the scene changes (the logo and the closing scene have their own impact)
for name, at in starts[1:]:
    if name not in ('logo', 'cta'):
        add(whoosh(), at - 0.3, 0.05, 0.0)

# Reverb on pads and arpeggio: convolution with a decaying noise tail
ir_len = int(2.2 * SR)
ir_t = np.arange(ir_len) / SR
size = 1 << int(np.ceil(np.log2(N + ir_len)))
for ch in range(2):
    ir = rng.standard_normal(ir_len) * np.exp(-ir_t * 2.8)
    ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    dry = pad_bus[ch] + fx_bus[ch]
    wet = np.fft.irfft(np.fft.rfft(dry, size) * np.fft.rfft(ir, size), size)[:N]
    out = (pad_bus[ch] * duck + fx_bus[ch]) + 0.35 * wet
    (left if ch == 0 else right)[:] += out

mix = np.stack([left, right])
mix /= np.max(np.abs(mix)) + 1e-9
mix = np.tanh(mix * 1.4) / np.tanh(1.4)
fade = np.minimum(1, np.arange(N) / (0.3 * SR))
mix *= fade
mix *= 0.89 / np.max(np.abs(mix))

pcm = (mix.T * 32767).astype('<i2')
out = ROOT / 'public' / 'music.wav'
with wave.open(str(out), 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print(f'Wrote {out} ({LENGTH:.1f}s, groove at {GROOVE:.2f}s, final chord at {END:.2f}s)')

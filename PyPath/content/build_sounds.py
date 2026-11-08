"""Generates PyPath's sound effects as small WAV files. Standard library only.

    python content/build_sounds.py

Writes mono 16-bit 22050 Hz WAVs to app/src/main/res/raw/. Every sound is synthesised here
from sine/square/triangle tones, noise and envelopes, so there is no third-party or copyrighted
audio. The output is deterministic: running it twice gives byte-identical files.
"""
import math, pathlib, random, struct, wave

RATE = 22050
MAX_BYTES = 150 * 1024
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

# ───────────── building blocks ─────────────

def note(name):
    """'A4' -> 440.0 Hz (equal temperament). Supports sharps like 'C#5'."""
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    semis = names[name[0]] + (1 if name[1] == "#" else 0)
    octave = int(name[-1])
    return 440.0 * 2 ** ((semis + 12 * (octave - 4)) / 12)

def silence(seconds): return [0.0] * int(RATE * seconds)

def env_adsr(n, attack=0.01, decay=0.08, sustain=0.6, release=0.1):
    a, d, r = int(RATE * attack), int(RATE * decay), int(RATE * release)
    out = []
    for i in range(n):
        if i < a: v = i / max(1, a)
        elif i < a + d: v = 1 - (1 - sustain) * (i - a) / max(1, d)
        else: v = sustain
        if i > n - r: v *= max(0.0, (n - i) / max(1, r))
        out.append(v)
    return out

def env_exp(n, rate=6.0, attack=0.004):
    a = int(RATE * attack)
    return [(i / a if i < a else 1.0) * math.exp(-rate * i / RATE) for i in range(n)]

def osc(freq, seconds, shape="sine", freq_end=None, vibrato=0.0):
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        f = freq if freq_end is None else freq + (freq_end - freq) * i / n
        if vibrato: f *= 1 + vibrato * math.sin(2 * math.pi * 5.5 * i / RATE)
        phase += 2 * math.pi * f / RATE
        if shape == "sine": v = math.sin(phase)
        elif shape == "square": v = 1.0 if math.sin(phase) >= 0 else -1.0
        elif shape == "triangle": v = 2 / math.pi * math.asin(math.sin(phase))
        else: raise ValueError(shape)
        out.append(v)
    return out

def bell(freq, seconds, rate=5.0, gain=1.0):
    """Sine with a couple of soft overtones and an exponential decay: a chime."""
    a, b, c = osc(freq, seconds), osc(freq * 2, seconds), osc(freq * 3.01, seconds)
    e = env_exp(len(a), rate)
    return [gain * e[i] * (0.7 * a[i] + 0.2 * b[i] + 0.1 * c[i]) for i in range(len(a))]

def tone(freq, seconds, shape="sine", gain=1.0, **env):
    s = osc(freq, seconds, shape)
    e = env_adsr(len(s), **env)
    return [gain * s[i] * e[i] for i in range(len(s))]

def noise(seconds, seed, gain=1.0, rate=18.0, smooth=0.0):
    rnd = random.Random(seed)
    n, out, prev = int(RATE * seconds), [], 0.0
    e = env_exp(n, rate, attack=0.002)
    for i in range(n):
        v = rnd.uniform(-1, 1)
        prev = smooth * prev + (1 - smooth) * v  # simple low-pass
        out.append(gain * prev * e[i])
    return out

def mix(total_seconds, *parts):
    """parts: (start_seconds, samples). Returns one buffer."""
    buf = [0.0] * int(RATE * total_seconds)
    for start, samples in parts:
        s = int(RATE * start)
        for i, v in enumerate(samples):
            if s + i < len(buf): buf[s + i] += v
    return buf

def lowpass(samples, k=0.25):
    out, prev = [], 0.0
    for v in samples:
        prev += k * (v - prev)
        out.append(prev)
    return out

def finish(samples, peak=0.85, fade=0.02):
    m = max(1e-9, max(abs(v) for v in samples))
    f = int(RATE * fade)
    out = []
    for i, v in enumerate(samples):
        g = min(1.0, (len(samples) - i) / max(1, f))
        out.append(v / m * peak * g)
    return out

def write(name, samples):
    samples = finish(samples)
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in samples))
    size = path.stat().st_size
    seconds = len(samples) / RATE
    assert size < MAX_BYTES, f"{name} is {size} bytes"
    print(f"wrote {path.name:<16} {seconds:4.2f}s {size / 1024:6.1f} KB")
    return seconds

def arpeggio(notes, step, length, start=0.0, rate=5.0, gain=1.0):
    return [(start + i * step, bell(note(n), length, rate, gain)) for i, n in enumerate(notes)]

# ───────────── UI sounds ─────────────

def sfx_correct():   # bright two-note rising chime
    return mix(0.5, (0.0, bell(note("E5"), 0.35, 9)), (0.09, bell(note("B5"), 0.4, 8)))

def sfx_wrong():     # soft low buzz that sags in pitch
    buzz = osc(150, 0.4, "square", freq_end=105)
    e = env_adsr(len(buzz), 0.01, 0.12, 0.5, 0.15)
    body = osc(75, 0.4, "sine", freq_end=60)
    return lowpass([0.35 * buzz[i] * e[i] + 0.6 * body[i] * e[i] for i in range(len(buzz))], 0.18)

def sfx_fill():      # quick soft "tick-pluck" for a code line appearing
    return mix(0.2, (0.0, bell(note("A5"), 0.18, 22, 0.8)), (0.0, noise(0.03, 7, 0.25, 90, 0.6)))

def sfx_pass():      # major arpeggio C-E-G-C
    return mix(1.1, *arpeggio(["C5", "E5", "G5", "C6"], 0.11, 0.7, rate=4.5))

def sfx_fail():      # gentle falling minor third
    return mix(0.85, (0.0, tone(note("E4"), 0.35, "triangle", decay=0.1, sustain=0.5, release=0.12)),
               (0.3, tone(note("C#4"), 0.5, "triangle", decay=0.2, sustain=0.4, release=0.3)))

# ───────────── Badge sounds (one per level, matching its animation) ─────────────

def badge_l1():      # spin + shine: rising glissando then a shimmering bell
    sweep = osc(note("C5"), 0.9, "sine", freq_end=note("C6"))
    e = env_adsr(len(sweep), 0.05, 0.3, 0.5, 0.3)
    shine = [(1.0 + i * 0.07, bell(note(n), 0.6, 7, 0.5)) for i, n in enumerate(["G6", "C7", "E7"])]
    return mix(1.9, (0.0, [0.5 * sweep[i] * e[i] for i in range(len(sweep))]),
               (0.9, bell(note("C6"), 1.0, 3.5)), *shine)

def badge_l2():      # confetti burst: pops + a bright major chord
    pops = [(0.05 + 0.06 * i, noise(0.05, 20 + i, 0.6, 60, 0.3)) for i in range(8)]
    chord = [(0.35, bell(note(n), 1.6, 2.5, 0.7)) for n in ("C5", "E5", "G5", "C6")]
    return mix(2.1, *pops, *chord)

def badge_l3():      # expanding rings with stars: three pulses that echo, then twinkles
    parts = []
    for k, n in enumerate(["G4", "D5", "G5"]):
        for echo in range(3):
            parts.append((0.0 + 0.45 * k + 0.12 * echo, bell(note(n), 0.5, 6, 0.8 * (0.55 ** echo))))
    parts += [(1.5 + 0.12 * i, bell(note(n), 0.35, 10, 0.4)) for i, n in enumerate(["B6", "D7", "G7", "D7"])]
    return mix(2.4, *parts)

def badge_l4():      # bounce drop + sparkles: boings that get shorter, then pings
    parts, t, length = [], 0.0, 0.32
    for k in range(5):
        boing = osc(note("C5") - 60 * k, length, "triangle", freq_end=note("C4"))
        e = env_exp(len(boing), 8)
        parts.append((t, [0.7 * boing[i] * e[i] for i in range(len(boing))]))
        t += length; length *= 0.68
    parts += [(t + 0.05 + 0.09 * i, bell(note(n), 0.4, 9, 0.5)) for i, n in enumerate(["E6", "G6", "C7", "E7"])]
    parts.append((t + 0.4, bell(note("C6"), 0.9, 4, 0.6)))
    return mix(2.1, *parts)

def badge_l5():      # fireworks: whistles going up, then crackling bursts
    parts = []
    for k, (start, top) in enumerate([(0.0, 1400), (0.55, 1800), (1.1, 1200)]):
        whistle = osc(500, 0.45, "sine", freq_end=top)
        e = env_adsr(len(whistle), 0.02, 0.2, 0.6, 0.1)
        parts.append((start, [0.25 * whistle[i] * e[i] for i in range(len(whistle))]))
        parts.append((start + 0.45, noise(0.6, 40 + k, 0.9, 7, 0.55)))
        parts += [(start + 0.5 + 0.05 * j, noise(0.04, 60 + 10 * k + j, 0.35, 80, 0.1)) for j in range(6)]
    parts.append((1.7, bell(note("G5"), 1.0, 3, 0.6)))
    parts.append((1.7, bell(note("D6"), 1.0, 3, 0.4)))
    return mix(2.8, *parts)

def badge_l6():      # ribbon unroll + orbit: smooth ascending scale with a gentle tremolo
    parts = []
    scale = ["D5", "E5", "F#5", "G5", "A5", "B5", "C#6", "D6"]
    for i, n in enumerate(scale):
        parts.append((0.14 * i, tone(note(n), 0.3, "sine", 0.6, attack=0.02, decay=0.1, sustain=0.5, release=0.12)))
    orbit = osc(note("D6"), 1.4, "sine")
    e = env_adsr(len(orbit), 0.1, 0.3, 0.6, 0.5)
    trem = [0.4 * orbit[i] * e[i] * (0.6 + 0.4 * math.sin(2 * math.pi * 7 * i / RATE)) for i in range(len(orbit))]
    parts.append((1.15, trem))
    parts.append((1.15, bell(note("A5"), 1.4, 2.5, 0.5)))
    return mix(2.6, *parts)

def badge_l7():      # graduation fanfare: brass-like chords, confetti pops, fireworks and a shining crown
    def brass(n, start, length, gain=0.5):
        a, b = osc(note(n), length, "square"), osc(note(n) * 1.005, length, "sine", vibrato=0.004)
        e = env_adsr(len(a), 0.03, 0.1, 0.7, 0.15)
        return (start, lowpass([gain * e[i] * (0.4 * a[i] + 0.6 * b[i]) for i in range(len(a))], 0.3))
    parts = [brass("G4", 0.0, 0.18), brass("G4", 0.2, 0.18), brass("C5", 0.4, 0.5),
             brass("E5", 0.95, 0.18), brass("D5", 1.15, 0.18), brass("E5", 1.35, 0.25)]
    parts += [brass(n, 1.65, 1.0, 0.35) for n in ("C5", "E5", "G5")]
    parts += [(0.4 + 0.07 * i, noise(0.05, 80 + i, 0.4, 60, 0.3)) for i in range(6)]
    parts += [(1.65, noise(0.7, 99, 0.6, 6, 0.6))]
    parts += [(2.1 + 0.08 * i, bell(note(n), 0.8, 5, 0.45)) for i, n in enumerate(["C7", "E7", "G7", "C7"])]
    return mix(3.2, *parts)

UI = {"sfx_correct": (sfx_correct, 0.3, 0.6), "sfx_wrong": (sfx_wrong, 0.3, 0.6), "sfx_fill": (sfx_fill, 0.1, 0.6),
      "sfx_pass": (sfx_pass, 0.5, 1.5), "sfx_fail": (sfx_fail, 0.5, 1.5)}
BADGES = {f"badge_l{i}": (fn, 1.5, 4.0) for i, fn in
          enumerate([badge_l1, badge_l2, badge_l3, badge_l4, badge_l5, badge_l6, badge_l7], start=1)}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (fn, lo, hi) in {**UI, **BADGES}.items():
        seconds = write(name, fn())
        assert lo <= seconds <= hi, f"{name} is {seconds:.2f}s, expected {lo}-{hi}s"
    print("sounds OK")

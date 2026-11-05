"""
Pulsar One-Shot Microphone Recorder & Tester
Immediately records 4 seconds from the system microphone, transcribes via Zipformer ASR,
runs INT8 Intent recognition and contact/file pinpointing.

Usage:
    python record_once.py [duration_seconds]
"""

import os
import sys
import time
import sounddevice as sd
import soundfile as sf
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from agent_core.voice_pipeline import PulsarVoicePipeline

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def main():
    duration = 4
    if len(sys.argv) > 1:
        try:
            duration = int(sys.argv[1])
        except ValueError:
            pass

    asr_model_dir = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
    
    print("=" * 70)
    print(f"PULSAR ONE-SHOT RECORDING ({duration} SECONDS)")
    print("=" * 70)
    
    pipeline = PulsarVoicePipeline(asr_model_dir=asr_model_dir)

    print("\nGet ready! Recording starts in:")
    for i in range(2, 0, -1):
        print(f"  {i}...")
        time.sleep(1)
        
    print(f"\n>>> [RECORDING NOW - SPEAK IN BANGLA FOR {duration} SECONDS] <<<")
    sample_rate = 16000
    audio = sd.rec(int(duration * sample_rate), samplerate=sample_rate, channels=1, dtype='float32')
    sd.wait()
    print(">>> [RECORDING COMPLETED - PROCESSING PIPELINE] <<<\n")

    audio_flat = audio.flatten()
    out_wav = os.path.join(r"D:\Coding\Pulsar\local\recordings", "my_voice_test.wav")
    sf.write(out_wav, audio_flat, sample_rate)

    t0 = time.perf_counter()
    res = pipeline.process_audio(audio_flat, sample_rate)
    total_time_ms = (time.perf_counter() - t0) * 1000.0

    print("=" * 70)
    print("LIVE VOICE RESULT:")
    print("=" * 70)
    print(f"🎙️  Transcribed Speech: '{res.get('transcription', '')}'")
    print(f"⚡  ASR Latency:        {res.get('asr_latency_ms', 0):.1f} ms")
    print(f"🎯  Predicted Intent:   {res.get('intent', 'N/A')} (Confidence: {res.get('intent_confidence', 0):.2f})")
    print(f"🧠  Intent Latency:     {res.get('intent_latency_ms', 0):.1f} ms")
    print(f"🎬  Triggered Action:   {res.get('action_type', 'N/A')}")
    
    if "action_params" in res and res["action_params"]:
        import json
        print(f"📦  Action Parameters:  {json.dumps(res['action_params'], ensure_ascii=False, indent=2)}")
        
    print(f"🔊  Spoken Feedback:   \"{res.get('spoken_response', '')}\"")
    print(f"⏱️  Total Pipeline Latency: {total_time_ms:.1f} ms")
    print(f"💾  Audio saved to:     {out_wav}")
    print("=" * 70)

if __name__ == "__main__":
    main()

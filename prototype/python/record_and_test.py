"""
Pulsar Live Voice Tester
Records voice directly from your microphone, transcribes it via Streaming Zipformer ASR,
classifies intent with INT8 BanglaBERT, resolves kinship/contacts/files, and executes the action.

Usage:
    python record_and_test.py
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

def record_audio(duration_sec: int = 4, sample_rate: int = 16000) -> np.ndarray:
    print("\n" + "=" * 60)
    print(f">> PREPARING TO RECORD ({duration_sec} seconds)...")
    print(">> Speak clearly in Bangla (e.g., 'আমার ছোট ছেলেকে ফোন দাও' or 'বসের পাঠানো ইনভয়েসটা বের কর')")
    print("=" * 60)
    for i in range(3, 0, -1):
        print(f"Starting in {i}...", end="\r")
        time.sleep(1)
    
    print("\n>>> RECORDING NOW! SPEAK...                     ")
    audio = sd.rec(int(duration_sec * sample_rate), samplerate=sample_rate, channels=1, dtype='float32')
    sd.wait()
    print(">>> RECORDING FINISHED! Processing...\n")
    return audio.flatten()

def main():
    duration = 4
    if len(sys.argv) > 1:
        try:
            duration = int(sys.argv[1])
        except ValueError:
            pass

    asr_model_dir = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
    
    print("=" * 70)
    print("       PULSAR OFFLINE VOICE AGENT - LIVE MICROPHONE TEST")
    print("=" * 70)
    
    pipeline = PulsarVoicePipeline(asr_model_dir=asr_model_dir)
    
    while True:
        try:
            input("\n[Press ENTER to record your voice (or Ctrl+C to exit)] ")
        except (KeyboardInterrupt, EOFError):
            print("\nExiting. Thank you!")
            break

        audio_data = record_audio(duration_sec=duration, sample_rate=16000)
        
        # Save last recording for inspection
        out_wav = os.path.join(r"D:\Coding\Pulsar\local\recordings", "last_voice_query.wav")
        sf.write(out_wav, audio_data, 16000)
        
        t0 = time.perf_counter()
        res = pipeline.process_audio(audio_data, 16000)
        total_time_ms = (time.perf_counter() - t0) * 1000.0

        print("=" * 70)
        print("RESULT:")
        print("=" * 70)
        print(f"🎙️  Transcribed Speech: '{res.get('transcription', '')}'")
        print(f"⚡  ASR Latency:        {res.get('asr_latency_ms', 0):.1f} ms")
        print(f"🎯  Predicted Intent:   {res.get('intent', 'N/A')} (Confidence: {res.get('intent_confidence', 0):.2f})")
        print(f"🧠  Intent Latency:     {res.get('intent_latency_ms', 0):.1f} ms")
        print(f"🎬  Triggered Action:   {res.get('action_type', 'N/A')}")
        
        if "action_params" in res and res["action_params"]:
            print(f"📦  Action Parameters:  {json.dumps(res['action_params'], ensure_ascii=False, indent=2)}")
            
        print(f"🔊  Spoken Feedback:   \"{res.get('spoken_response', '')}\"")
        print(f"⏱️  Total Pipeline Latency: {total_time_ms:.1f} ms")
        print("=" * 70)

if __name__ == "__main__":
    main()

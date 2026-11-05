"""
Pulsar Audio File Tester
Processes any pre-recorded audio file (.wav, .flac, .ogg) through the end-to-end voice pipeline:
ASR -> Intent -> Kinship/Contact/File Resolution -> Action Execution Payload.

Usage:
    python test_audio.py path/to/audio_file.wav
    python test_audio.py (defaults to last_voice_query.wav if present)
"""

import json
import os
import sys
import time
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from agent_core.voice_pipeline import PulsarVoicePipeline

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def main():
    if len(sys.argv) > 1:
        wav_path = sys.argv[1]
    else:
        wav_path = os.path.join(r"D:\Coding\Pulsar\local\recordings", "last_voice_query.wav")

    if not os.path.exists(wav_path):
        print(f"Error: Audio file not found at: {wav_path}")
        print("Please provide a valid audio path: python test_audio.py <path_to_audio.wav>")
        return

    asr_model_dir = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
    pipeline = PulsarVoicePipeline(asr_model_dir=asr_model_dir)

    print(f"\nProcessing Audio File: {wav_path}")
    res = pipeline.process_audio_file(wav_path)

    print("\n" + "=" * 70)
    print("VOICE AGENT EVALUATION RESULT:")
    print("=" * 70)
    print(f"🎙️  Transcribed Speech: '{res.get('transcription', '')}'")
    print(f"⚡  ASR Latency:        {res.get('asr_latency_ms', 0):.1f} ms")
    print(f"🎯  Predicted Intent:   {res.get('intent', 'N/A')} (Confidence: {res.get('intent_confidence', 0):.2f})")
    print(f"🧠  Intent Latency:     {res.get('intent_latency_ms', 0):.1f} ms")
    print(f"🎬  Triggered Action:   {res.get('action_type', 'N/A')}")
    
    if "action_params" in res and res["action_params"]:
        print(f"📦  Action Parameters:  {json.dumps(res['action_params'], ensure_ascii=False, indent=2)}")
        
    print(f"🔊  Spoken Feedback:   \"{res.get('spoken_response', '')}\"")
    print(f"⏱️  Total Pipeline Latency: {res.get('total_latency_ms', 0):.1f} ms")
    print("=" * 70)

if __name__ == "__main__":
    main()

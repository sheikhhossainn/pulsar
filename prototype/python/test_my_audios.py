"""
Pulsar Benchmark on User's Audio Clips
Tests the user's recorded audio files from 'D:\\Coding\\Pulsar\\local\\recordings'
"""

import os
import sys
import time
import subprocess
import soundfile as sf
import imageio_ffmpeg

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from agent_core.voice_pipeline import PulsarVoicePipeline

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def convert_to_wav(input_path: str, output_path: str) -> bool:
    """Converts any audio file to 16kHz mono 16-bit WAV using imageio_ffmpeg."""
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [
        ffmpeg_exe, "-y",
        "-i", input_path,
        "-ar", "16000",
        "-ac", "1",
        "-c:a", "pcm_s16le",
        output_path
    ]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return res.returncode == 0 and os.path.exists(output_path)

def main():
    audios_dir = r"D:\Coding\Pulsar\local\recordings"
    asr_model_dir = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
    
    print("=" * 80)
    print("        PULSAR VOICE AGENT - USER AUDIO EVALUATION")
    print(f"        Audio Directory: {audios_dir}")
    print("=" * 80)

    # Initialize Pipeline
    pipeline = PulsarVoicePipeline(asr_model_dir=asr_model_dir)

    # List original aac user audio files
    files = [f for f in os.listdir(audios_dir) if f.lower().endswith(".aac")]

    if not files:
        print(f"No audio files found in: {audios_dir}")
        return

    print(f"\nFound {len(files)} audio clips to evaluate:\n")
    for f in files:
        print(f"  - {f}")

    results = []

    for idx, fname in enumerate(files, 1):
        fpath = os.path.join(audios_dir, fname)
        wav_path = os.path.splitext(fpath)[0] + "_16k.wav"
        
        # Convert to 16k wav if needed
        if fname.lower().endswith(".wav"):
            target_wav = fpath
        else:
            success = convert_to_wav(fpath, wav_path)
            if not success:
                print(f"\n[FAIL] Could not decode: {fname}")
                continue
            target_wav = wav_path

        # Read audio duration
        info = sf.info(target_wav)
        duration_sec = info.duration

        print("\n" + "-" * 80)
        print(f"[{idx}/{len(files)}] Processing: '{fname}' ({duration_sec:.2f}s)")
        print("-" * 80)

        t0 = time.perf_counter()
        res = pipeline.process_audio_file(target_wav)
        total_time_ms = (time.perf_counter() - t0) * 1000.0

        transcription = res.get("transcription", "")
        intent = res.get("intent", "N/A")
        intent_conf = res.get("intent_confidence", 0.0)
        action_type = res.get("action_type", "N/A")
        spoken_response = res.get("spoken_response", "")

        print(f"🎙️  Transcribed Speech:  \"{transcription}\"")
        print(f"⚡  ASR Latency:         {res.get('asr_latency_ms', 0):.1f} ms")
        print(f"🎯  Predicted Intent:    {intent} (Confidence: {intent_conf:.2f})")
        print(f"🧠  Intent Latency:      {res.get('intent_latency_ms', 0):.1f} ms")
        print(f"🎬  Triggered Action:    {action_type}")
        
        if "action_params" in res and res["action_params"]:
            import json
            print(f"📦  Action Parameters:   {json.dumps(res['action_params'], ensure_ascii=False, indent=2)}")
            
        print(f"🔊  Spoken Feedback:    \"{spoken_response}\"")
        print(f"⏱️  Total Latency:       {total_time_ms:.1f} ms (ASR + NLU + Action)")

        results.append({
            "filename": fname,
            "duration_sec": duration_sec,
            "transcription": transcription,
            "asr_latency_ms": res.get("asr_latency_ms", 0),
            "intent": intent,
            "intent_confidence": intent_conf,
            "action_type": action_type,
            "spoken_response": spoken_response,
            "total_latency_ms": total_time_ms
        })

    print("\n" + "=" * 80)
    print("EVALUATION SUMMARY")
    print("=" * 80)
    for r in results:
        print(f"• File: '{r['filename']}' ({r['duration_sec']:.1f}s)")
        print(f"  Transcribed: \"{r['transcription']}\"")
        print(f"  Intent: {r['intent']} -> Action: {r['action_type']}")
        print(f"  Latency: {r['total_latency_ms']:.1f} ms")
        print()

if __name__ == "__main__":
    main()

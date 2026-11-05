import sys
import os
import sherpa_onnx
import soundfile as sf

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

MODEL_DIR = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
HW_FILE = r"D:\Coding\Pulsar\prototype\python\hotwords\bangladesh_daily_hotwords.txt"

print(f"Loading OnlineRecognizer with {HW_FILE}...")
rec = sherpa_onnx.OnlineRecognizer.from_transducer(
    encoder=os.path.join(MODEL_DIR, "encoder.onnx"),
    decoder=os.path.join(MODEL_DIR, "decoder.onnx"),
    joiner=os.path.join(MODEL_DIR, "joiner.onnx"),
    tokens=os.path.join(MODEL_DIR, "tokens.txt"),
    num_threads=2,
    sample_rate=16000,
    feature_dim=80,
    decoding_method="modified_beam_search",
    max_active_paths=4,
    hotwords_file=HW_FILE,
    hotwords_score=2.5
)
print("Loaded successfully!")

# Test all 4 user audio files
audios_dir = r"D:\Coding\Pulsar\local\recordings"
wav_files = [
    "call my mother_16k.wav",
    "check boss's email_16k.wav",
    "find the last photo_16k.wav",
    "open today's clicked photo_16k.wav"
]

print("\n" + "=" * 60)
print("DECODING USER AUDIOS WITH DAILY HOTWORDS BIASING")
print("=" * 60)

for wf in wav_files:
    fpath = os.path.join(audios_dir, wf)
    if not os.path.exists(fpath):
        continue
    data, sr = sf.read(fpath)
    s = rec.create_stream()
    s.accept_waveform(sr, data)
    while rec.is_ready(s):
        rec.decode_stream(s)
    text = rec.get_result(s)
    print(f"File: {wf}\n  --> Transcribed: '{text}'\n")

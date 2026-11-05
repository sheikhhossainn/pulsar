import sys
import os
import sherpa_onnx
import soundfile as sf
import sentencepiece as spm

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

model_dir = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
sp = spm.SentencePieceProcessor()
sp.load(os.path.join(model_dir, "bpe.model"))

# List of daily technical loanwords and common commands used in Bangladesh
hotwords_list = [
    "ইমেইল", "মেইল", "বস", "বসের", "চেক", "হোয়াটসঅ্যাপ",
    "পিডিএফ", "ইনভয়েস", "প্রেসক্রিপশন", "ডাউনলোড", "ক্যামেরা",
    "ছবি", "ফটো", "পিকচার", "রেকর্ডিং", "মেসেজ", "কল", "ফোন",
    "সার্চ", "গুগল", "টিকেট", "এনআইডি", "বিল", "ভিডিও", "অডিও"
]

hw_lines = []
for hw in hotwords_list:
    pieces = sp.encode_as_pieces(hw)
    hw_lines.append(" ".join(pieces) + " :2.5")

hw_file = r"D:\Coding\Pulsar\prototype\python\hotwords\daily_hotwords.txt"
with open(hw_file, "w", encoding="utf-8") as f:
    f.write("\n".join(hw_lines))

print(f"Generated {len(hw_lines)} hotwords in {hw_file}")

# Initialize Recognizer with Modified Beam Search and Hotwords Biasing
rec = sherpa_onnx.OnlineRecognizer.from_transducer(
    encoder=os.path.join(model_dir, "encoder.onnx"),
    decoder=os.path.join(model_dir, "decoder.onnx"),
    joiner=os.path.join(model_dir, "joiner.onnx"),
    tokens=os.path.join(model_dir, "tokens.txt"),
    num_threads=2,
    sample_rate=16000,
    feature_dim=80,
    decoding_method="modified_beam_search",
    max_active_paths=4,
    hotwords_file=hw_file,
    hotwords_score=2.5
)

# Test on check boss's email_16k.wav
wav_path = os.path.join(r"D:\Coding\Pulsar\local\recordings", "check boss's email_16k.wav")
data, sr = sf.read(wav_path)
s = rec.create_stream()
s.accept_waveform(sr, data)
while rec.is_ready(s):
    rec.decode_stream(s)

result_text = rec.get_result(s)
print(f"Transcription with Hotwords Biasing: '{result_text}'")

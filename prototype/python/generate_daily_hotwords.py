"""
Pulsar Hotwords Generator for Daily Bangladeshi Mobile Speech
Analyzes and compiles the most frequent technical loanwords, daily mobile terminology,
and spoken Dhaka action keywords encoded precisely into sherpa-onnx BPE tokens.
"""

import os
import sys

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

MODEL_DIR = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
TOKENS_FILE = os.path.join(MODEL_DIR, "tokens.txt")

# Read valid tokens from tokens.txt
with open(TOKENS_FILE, "r", encoding="utf-8") as f:
    token_list = [line.strip().split()[0] for line in f if line.strip()]
token_set = set(token_list)

BPE_SPACE = "\u2581"

def encode_hotword(word: str) -> str:
    """Encodes a word using maximal forward matching against tokens.txt."""
    target = BPE_SPACE + word
    res = []
    i = 0
    while i < len(target):
        matched = None
        for l in range(min(15, len(target) - i), 0, -1):
            sub = target[i:i + l]
            if sub in token_set:
                matched = sub
                break
        if matched:
            res.append(matched)
            i += len(matched)
        else:
            ch = target[i]
            if ch in token_set:
                res.append(ch)
            else:
                # If char not in vocab, skip or return None
                return None
            i += 1
    return " ".join(res)

# Daily Bangladeshi Mobile & Voice Agent Vocabulary
DAILY_VOCABULARY = [
    # 1. Email & Office Communication
    ("ইমেইল", 3.0),
    ("ইমেইলটা", 3.0),
    ("মেইল", 3.0),
    ("মেইলটা", 3.0),
    ("বস", 2.5),
    ("বসের", 2.5),
    ("অফিস", 2.0),
    ("অফিসের", 2.0),
    ("রিপোর্ট", 2.5),
    ("রিপোর্টটা", 2.5),
    ("চেক", 2.5),
    ("ফাইল", 2.5),
    ("ফাইলটা", 2.5),
    
    # 2. Messaging & Calling
    ("হোয়াটসঅ্যাপ", 3.0),
    ("হোয়াটসঅ্যাপে", 3.0),
    ("মেসেজ", 3.0),
    ("মেসেজটা", 3.0),
    ("কল", 2.5),
    ("কলটা", 2.5),
    ("ফোন", 2.5),
    ("ফোনটা", 2.5),
    ("রিং", 2.0),
    ("নাম্বার", 2.0),
    ("ঘণ্টি", 2.0),
    
    # 3. Documents & Financials
    ("পিডিএফ", 3.0),
    ("পিডিএফটা", 3.0),
    ("ইনভয়েস", 3.0),
    ("ইনভয়েসটা", 3.0),
    ("বিল", 2.5),
    ("বিলটা", 2.5),
    ("প্রেসক্রিপশন", 3.0),
    ("প্রেসক্রিপশনটা", 3.0),
    ("এনআইডি", 3.0),
    ("টিকেট", 2.5),
    ("টিকিট", 2.5),
    ("কার্ড", 2.5),
    
    # 4. Photos, Media & Storage
    ("ছবি", 2.0),
    ("ছবিটা", 2.5),
    ("ছবিগুলা", 2.0),
    ("ফটো", 2.5),
    ("ফটোটা", 2.5),
    ("পিকচার", 2.5),
    ("ক্যামেরা", 2.5),
    ("ক্যামেরায়", 2.5),
    ("গ্যালারি", 2.5),
    ("ডাউনলোড", 2.5),
    ("ভিডিও", 2.0),
    ("অডিও", 2.0),
    ("ভয়েস", 2.5),
    ("ভয়েসটা", 2.5),
    ("রেকর্ডিং", 2.5),
    ("রেকর্ডিংটা", 2.5),
    ("স্ক্রিনশট", 2.5),
    
    # 5. Core Actions & Searches
    ("ওপেন", 2.5),
    ("বাইর", 2.0),
    ("সার্চ", 2.5),
    ("গুগল", 2.5),
    ("গুগলে", 2.5),
    ("সেন্ড", 2.5),
    ("শেয়ার", 2.0),
    ("অনলাইন", 2.0),
    
    # 6. Trades, Health & Kinship
    ("ড্রাইভার", 2.5),
    ("ডাক্তার", 2.5),
    ("মিস্ত্রি", 2.0),
    ("আম্মু", 2.0),
    ("আম্মুকে", 2.0),
    ("আব্বু", 2.0),
    ("আব্বুকে", 2.0),
    ("ছোট", 2.0),
    ("ছোটকে", 2.0),
    ("বড়", 2.0),
    ("বড়কে", 2.0),
    ("ছেলে", 2.0),
    ("মেয়ে", 2.0),
    ("বউ", 2.0),
    ("ভাবী", 2.0),
    ("জামাই", 2.0)
]

output_lines = []
success_count = 0
skipped = []

for word, score in DAILY_VOCABULARY:
    encoded = encode_hotword(word)
    if encoded:
        output_lines.append(f"{encoded} :{score}")
        success_count += 1
    else:
        skipped.append(word)

out_file = os.path.join(r"D:\Coding\Pulsar\prototype\python\hotwords", "bangladesh_daily_hotwords.txt")
with open(out_file, "w", encoding="utf-8") as f:
    f.write("\n".join(output_lines) + "\n")

print(f"Generated {success_count} hotwords successfully in {out_file}!")
if skipped:
    print(f"Skipped {len(skipped)} words due to missing tokens: {skipped}")

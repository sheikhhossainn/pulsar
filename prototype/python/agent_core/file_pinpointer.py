"""
Pulsar Smart File Pinpointing Engine
Translates natural spoken Bangla requests into exact file paths on local device storage
using multi-facet indexing: Channel, Media Type, Temporal Recency, and Semantic/Entity Context.
"""

import os
import re
import sys
import time
from datetime import datetime
from typing import Dict, List, Optional, Tuple, Any

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Semantic Lexicon Mapping (Bangla/Banglish to semantic tag stems)
SEMANTIC_STEMS = {
    "invoice": ["invoice", "bill", "memo", "বিল", "ইনভয়েস", "ইনভয়েস", "চালান", "রিসিপ্ট"],
    "bill": ["bill", "electricity", "desco", "বিল", "বিদ্যুৎ", "কারেন্ট"],
    "prescription": ["prescription", "doctor", "hospital", "rx", "প্রেসক্রিপশন", "প্রেস্ক্রিপশন", "ডাক্তার", "ওষুধ", "হাসপাতাল"],
    "nid": ["nid", "smart_nid", "identity", "ভোট", "এনআইডি", "জাতীয়_পরিচয়পত্র", "ভোটার"],
    "ticket": ["ticket", "biman", "flight", "টিকেট", "টিকিট", "বিমান"],
    "meter": ["meter", "reading", "মিটার"],
    "meeting": ["meeting", "conference", "মিটিং", "বৈঠক"],
    "boss": ["boss", "tareq", "বস", "অফিস", "তারেক"],
    "son": ["son", "rifat", "tanvir", "ছেলে", "পোলা", "রিফাত", "তানভীর"],
    "daughter": ["daughter", "nabila", "tasnim", "মেয়ে", "মাইয়া", "নাবিলা", "তাসনিম"]
}

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}
DOC_EXTS = {".pdf", ".docx", ".doc", ".txt"}
AUDIO_EXTS = {".m4a", ".mp3", ".opus", ".wav", ".aac"}

class FilePinpointer:
    def __init__(self, storage_root: str):
        self.storage_root = os.path.abspath(storage_root)
        self.files_index = []
        self.refresh_index()

    def refresh_index(self):
        """Scans the storage root and builds metadata cache for all files."""
        self.files_index = []
        now = time.time()
        
        for root, dirs, files in os.walk(self.storage_root):
            for fname in files:
                fpath = os.path.join(root, fname)
                try:
                    stat = os.stat(fpath)
                    mtime = stat.st_mtime
                    age_seconds = max(0, now - mtime)
                    age_minutes = age_seconds / 60.0
                    
                    # Read metadata header if present in test files
                    sender = ""
                    tags = []
                    try:
                        with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                            header = [f.readline() for _ in range(5)]
                            for line in header:
                                if line.startswith("Sender:"):
                                    sender = line.split("Sender:", 1)[1].strip()
                                elif line.startswith("Tags:"):
                                    tags = [t.strip().lower() for t in line.split("Tags:", 1)[1].split(",")]
                    except Exception:
                        pass
                    
                    rel_path = os.path.relpath(fpath, self.storage_root)
                    ext = os.path.splitext(fname)[1].lower()
                    
                    self.files_index.append({
                        "path": fpath,
                        "rel_path": rel_path,
                        "filename": fname,
                        "ext": ext,
                        "mtime": mtime,
                        "age_minutes": age_minutes,
                        "size": stat.st_size,
                        "sender": sender,
                        "tags": tags,
                        "search_blob": f"{fname} {rel_path} {sender} {' '.join(tags)}".lower()
                    })
                except Exception:
                    pass

    def _tokenize(self, text: str) -> List[str]:
        return re.findall(r"[\u0980-\u09FFa-zA-Z0-9]+", text.lower())

    def _strip_suffixes(self, word: str) -> str:
        """Strip Bangla inflections, case markers, and definers (longest suffix first)."""
        suffixes = [
            "টারেরে", "গুলো", "গুলা", "টারে", "টাকে", "দের", "গো", 
            "টা", "টি", "কে", "রে", "তে", "ের", "র", "ে"
        ]
        for s in suffixes:
            if word.endswith(s) and len(word) > len(s) + 1:
                return word[:-len(s)]
        return word

    def _extract_facets(self, query: str) -> Dict[str, Any]:
        """Extracts channel, media type, temporal constraints, and semantic stems using clean tokenization."""
        raw_tokens = self._tokenize(query.lower())
        tokens = [self._strip_suffixes(t) for t in raw_tokens]
        all_tokens = set(raw_tokens + tokens)

        # 1. Channel Filter (using token-level checks to prevent substring false positives)
        channel = None
        if any(t in all_tokens for t in ["হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটসএপ"]):
            channel = "whatsapp"
        elif any(t in all_tokens for t in ["ক্যামেরা", "camera"]):
            channel = "camera"
        elif any(t in all_tokens for t in ["ডাউনলোড", "download", "নামাইছি"]):
            channel = "download"
        elif any(t in all_tokens for t in ["রেকর্ডিং", "রেকর্ড", "recording", "ভয়েস"]):
            channel = "recordings"

        # 2. Media Type Filter
        media_type = None
        if any(t in all_tokens for t in ["ছবি", "ফটো", "পিকচার", "image", "photo", "pic"]):
            media_type = "image"
        elif any(t in all_tokens for t in ["পিডিএফ", "কাগজ", "ডকুমেন্ট", "pdf", "document", "doc"]):
            media_type = "document"
        elif any(t in all_tokens for t in ["ভয়েস", "রেকর্ডিং", "গান", "voice", "audio"]):
            media_type = "audio"

        # 3. Temporal Filter
        temporal = None
        if any(t in all_tokens for t in ["লাস্টের", "শেষের", "সর্বশেষ", "লাস্ট", "নতুন", "latest", "recent"]):
            temporal = "latest"
        elif any(t in all_tokens for t in ["আজকের", "আজকে", "today"]):
            temporal = "today"
        elif any(t in all_tokens for t in ["গতকালকের", "গতকালের", "গতকাল", "yesterday"]):
            temporal = "yesterday"
        elif any(t in all_tokens for t in ["সপ্তাহের", "week"]):
            temporal = "this_week"

        # 4. Semantic Concept Stems
        matched_stems = set()
        for stem_name, keywords in SEMANTIC_STEMS.items():
            for kw in keywords:
                if kw in all_tokens:
                    matched_stems.add(stem_name)
                    break

        return {
            "channel": channel,
            "media_type": media_type,
            "temporal": temporal,
            "semantic_stems": list(matched_stems),
            "tokens": list(all_tokens)
        }

    def pinpoint_file(self, query: str) -> Dict[str, Any]:
        """
        Pinpoints the exact file matching the natural speech request.
        Calculates normalized confidence score across all facets.
        """
        facets = self._extract_facets(query)
        channel = facets["channel"]
        media_type = facets["media_type"]
        temporal = facets["temporal"]
        stems = facets["semantic_stems"]

        candidates = []

        for f in self.files_index:
            score = 0.0
            reasons = []

            # 1. Channel Scoring
            rel_lower = f["rel_path"].lower()
            if channel:
                if channel == "whatsapp" and "whatsapp" in rel_lower:
                    score += 0.35
                    reasons.append("channel:whatsapp")
                elif channel == "camera" and "camera" in rel_lower:
                    score += 0.35
                    reasons.append("channel:camera")
                elif channel == "download" and "download" in rel_lower:
                    score += 0.35
                    reasons.append("channel:download")
                elif channel == "recordings" and "recordings" in rel_lower:
                    score += 0.35
                    reasons.append("channel:recordings")
                else:
                    # Negative penalty if wrong directory
                    score -= 0.25

            # 2. Media Type Scoring
            if media_type == "image":
                if f["ext"] in IMAGE_EXTS:
                    score += 0.25
                    reasons.append("type:image")
                else:
                    continue # Strict discard if explicit type mismatch
            elif media_type == "document":
                if f["ext"] in DOC_EXTS:
                    score += 0.25
                    reasons.append("type:document")
                else:
                    continue
            elif media_type == "audio":
                if f["ext"] in AUDIO_EXTS:
                    score += 0.25
                    reasons.append("type:audio")
                else:
                    continue

            # 3. Temporal Scoring
            age_min = f["age_minutes"]
            if temporal == "latest":
                # Recency decay: score higher the fresher it is
                recency_boost = max(0.0, 0.40 - (age_min / 1440.0) * 0.20)
                score += recency_boost
                reasons.append(f"temporal:latest({int(age_min)}m_ago)")
            elif temporal == "today":
                if age_min <= 1440: # within 24h
                    score += 0.30
                    reasons.append("temporal:today")
                else:
                    score -= 0.20
            elif temporal == "yesterday":
                if 1200 <= age_min <= 2880: # roughly 20h to 48h
                    score += 0.35
                    reasons.append("temporal:yesterday")
                else:
                    score -= 0.15
            else:
                # Default gentle recency preference
                score += max(0.0, 0.08 - (age_min / 5000.0) * 0.04)

            # 4. Semantic Stems Scoring
            blob = f["search_blob"]
            matched_stem_count = 0
            for stem in stems:
                keywords = SEMANTIC_STEMS.get(stem, [])
                if stem in blob or any(kw in blob for kw in keywords):
                    matched_stem_count += 1
                    reasons.append(f"stem:{stem}")

            if matched_stem_count > 0:
                score += min(0.45, matched_stem_count * 0.25)

            # 5. Direct token match against filename
            for tok in facets["tokens"]:
                if len(tok) >= 3 and tok in f["filename"].lower():
                    score += 0.15
                    reasons.append(f"token:{tok}")

            if score > 0.10:
                candidates.append({
                    "path": f["path"],
                    "filename": f["filename"],
                    "rel_path": f["rel_path"],
                    "score": round(score, 3),
                    "reasons": reasons,
                    "age_minutes": int(age_min),
                    "sender": f["sender"]
                })

        candidates.sort(key=lambda x: x["score"], reverse=True)

        if not candidates:
            return {
                "status": "NOT_FOUND",
                "message": "অনুরোধ অনুযায়ী কোনো ফাইল খুঁজে পাওয়া যায়নি।"
            }

        top = candidates[0]
        return {
            "status": "PINPOINTED",
            "file_path": top["path"],
            "filename": top["filename"],
            "relative_path": top["rel_path"],
            "confidence": min(1.0, top["score"]),
            "sender": top["sender"],
            "reasons": top["reasons"],
            "top_candidates": candidates[:3]
        }


# Quick verification
if __name__ == "__main__":
    pinpointer = FilePinpointer(r"D:\Coding\Pulsar\prototype\python\agent_core\data\mock_storage")
    print(f"Indexed {len(pinpointer.files_index)} files in mock storage.")

    test_queries = [
        "আমার ছেলে একটা ছবি দিছে হোয়াটসঅ্যাপে, বের কর তো",
        "লাস্টের ছবিটা পিডিএফ বানাও",
        "বসের পাঠানো ইনভয়েসটা বের কর",
        "গতকালকের প্রেসক্রিপশনটা দেখাও",
        "ডাউনলোড করা বিদ্যুৎ বিলের কাগজটা ওপেন কর",
        "এনআইডি কার্ডের কপিটা বের কর",
        "মিটিংয়ের ভয়েস রেকর্ডিংটা শোনাও",
        "বড় ছেলের পাঠানো বিমানের টিকিটটা বের কর"
    ]

    print("=" * 65)
    print("PULSAR FILE PINPOINTING ENGINE TEST")
    print("=" * 65)
    for q in test_queries:
        res = pinpointer.pinpoint_file(q)
        print(f"\nVoice Query: '{q}'")
        if res["status"] == "PINPOINTED":
            print(f" -> Pinpointed: {res['relative_path']}")
            print(f"    Confidence: {res['confidence']} | Reasons: {res['reasons']}")
        else:
            print(f" -> NOT FOUND: {res['message']}")

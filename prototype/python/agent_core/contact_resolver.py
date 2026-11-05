"""
Pulsar Contact & Kinship Resolver
Handles elder-generation Bangladeshi speech, kinship relations, ambiguity resolution,
transliteration (Bangla <-> English), and direct contact matching.
"""

import json
import os
import re
import sys
from typing import Dict, List, Optional, Tuple, Any

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Bengali to Roman/Banglish common phonetic mappings
BN_TO_EN_NAME_MAP = {
    "তানভীর": "tanvir",
    "তানভির": "tanvir",
    "আহমেদ": "ahmed",
    "রিফাত": "rifat",
    "নাবিলা": "nabila",
    "তাসনিম": "tasnim",
    "ফাহিম": "fahim",
    "রোকেয়া": "rokeya",
    "কামরুল": "kamrul",
    "সাদিয়া": "sadia",
    "আম্মু": "ammu",
    "আম্মা": "amma",
    "মা": "ammu",
    "আব্বু": "abbu",
    "আব্বা": "abba",
    "বাবা": "abbu",
    "তারেক": "tareq",
    "মামুন": "mamun",
    "আসিফ": "asif",
    "সোহেল": "sohel",
    "রহিম": "rahim",
    "মিজান": "mizan",
    "রফিক": "rafiq",
    "আল-আমিন": "al-amin",
    "বাদল": "badal",
    "কালাম": "kalam",
    "রুবেল": "rubel",
    "বাবুল": "babul",
    "মানিক": "manik",
    "মাহফুজ": "mahfuz",
    "শামসুল": "shamsul",
    "ফাতেমা": "fatema",
    "আনোয়ার": "anwar",
    "জুবায়ের": "zubair",
    "শাকিল": "shakil",
    "মনির": "monir",
    "হাসান": "hasan",
    "ফারুক": "faruk",
    "সুলতana": "sultana",
    "ইমরান": "imran",
    "আয়ান": "aayan",
    "আনিকা": "anika"
}

# Pronouns, politeness particles, and action verbs to ignore when searching names
STOPWORDS = {
    "কল", "ফোন", "দাও", "মার", "পাঠা", "মেসেজ", "দে", "তো", "একটা", "কর", 
    "আমার", "ডাক", "খাড়া", "বলো", "বল", "বলিস", "মারো", "মারেন", "একটু",
    "আপনি", "তুমি", "তুই", "আপনার", "তোমার", "তোর", "লাগাও", "লাগা", "আর্জেন্ট",
    "হোয়াটসঅ্যাপে", "হোয়াটসঅ্যাপে", "whatsapp"
}

class ContactResolver:
    def __init__(self, contacts_path: str, kinship_graph_path: str):
        with open(contacts_path, "r", encoding="utf-8") as f:
            self.contacts = json.load(f)
            
        with open(kinship_graph_path, "r", encoding="utf-8") as f:
            self.kinship = json.load(f)
            
        self._build_index()

    def _build_index(self):
        """Index contacts by ID, normalized name, and aliases."""
        self.contact_by_id = {c["id"]: c for c in self.contacts}
        self.indexed_contacts = []
        
        for c in self.contacts:
            name_raw = c["name"]
            tokens = self._tokenize(name_raw)
            clean_tokens = [self._clean_token(t) for t in tokens]
            
            # Romanized versions of tokens if any
            roman_tokens = []
            for t in clean_tokens:
                if t in BN_TO_EN_NAME_MAP:
                    roman_tokens.append(BN_TO_EN_NAME_MAP[t])
            
            nick_tokens = []
            for nick in c.get("nicknames", []):
                for nt in self._tokenize(nick):
                    nick_tokens.append(self._clean_token(nt))

            self.indexed_contacts.append({
                "id": c["id"],
                "raw": c,
                "name": name_raw,
                "clean_name": " ".join(clean_tokens).lower(),
                "clean_tokens": set(clean_tokens),
                "roman_tokens": set(roman_tokens),
                "nick_tokens": set(nick_tokens),
                "relation": c.get("relation", "")
            })

    def _tokenize(self, text: str) -> List[str]:
        """Extract Bengali and alphanumeric tokens accurately preserving vowel signs."""
        return re.findall(r"[\u0980-\u09FFa-zA-Z0-9]+", text)

    def _clean_token(self, token: str) -> str:
        t = token.strip().lower()
        # Strip leading conversational fillers if attaching to a known root (e.g. 'আবস' -> 'বস')
        if len(t) >= 3 and t.startswith(("আ", "এ")):
            sub = t[1:]
            if sub in ["বস", "বাবা", "ভাই", "পোলা", "মেয়ে", "ডাক্তার"]:
                return sub
        return t

    def _strip_case_markers(self, word: str) -> str:
        """Strip ONLY Bangla accusative/objective/emphasis suffixes like কে, রে, টা, টারে, ের."""
        suffixes = ["টারেরে", "গুলো", "গুলা", "টারে", "টাকে", "দের", "গো", "ের", "কে", "রে"]
        for s in suffixes:
            if word.endswith(s) and len(word) > len(s) + 1:
                return word[:-len(s)]
        return word

    def extract_kinship_from_query(self, query: str) -> Tuple[Optional[str], Optional[str]]:
        """
        Parses query for kinship modifier (boro, choto, mejo, sejo) and role (chele, meye, jamai, etc.)
        """
        raw_tokens = self._tokenize(query.lower())
        tokens = [self._clean_token(self._strip_case_markers(t)) for t in raw_tokens]
        joined = " ".join(tokens)

        # Modifier detection (strict token-level to prevent substrings like 'সেজ' matching inside 'মেসেজ')
        modifier = None
        for t in tokens:
            if t in ["ছোট", "ছুডু", "choto", "chuto"]:
                modifier = "choto"
                break
            elif t in ["বড়", "বড্ডা", "boro", "bodo"]:
                modifier = "boro"
                break
            elif t in ["মেজো", "মেজ", "mejo"]:
                modifier = "mejo"
                break
            elif t in ["সেজো", "সেজ", "sejo"]:
                modifier = "sejo"
                break

        # Role detection
        role = None
        if any(r in tokens for r in ["ছেলে", "পোলা", "বেটা", "chele", "pola", "beta", "son"]):
            role = "chele"
        elif any(r in tokens for r in ["মেয়ে", "মাইয়া", "কুইড়া", "meye", "maiya", "daughter"]):
            role = "meye"
        elif any(r in tokens for r in ["জামাই", "দামাদ", "jamai"]):
            role = "jamai"
        elif any(r in tokens for r in ["বউ", "স্ত্রী", "গিন্নি", "wife", "bou"]):
            role = "spouse"
        elif any(r in tokens for r in ["ভাবী", "ভাবি", "বৌদি", "bhabi", "bhabhi"]):
            role = "sister_in_law"
        elif any(r in tokens for r in ["দুলাভাই", "dulabhai"]):
            role = "brother_in_law_dulabhai"
        elif any(r in tokens for r in ["শালা", "shala", "saley"]):
            role = "brother_in_law_sala"
        elif any(r in tokens for r in ["ভাই", "ভায়রা", "bhai", "brother"]):
            role = "bhai"
        elif any(r in tokens for r in ["বোন", "বুবু", "আপা", "bon", "apa", "sister"]):
            role = "bon"
        elif any(r in tokens for r in ["আম্মু", "আম্মা", "মা", "ammu", "amma", "maa", "mother"]):
            role = "mother"
        elif any(r in tokens for r in ["আব্বু", "আব্বা", "বাবা", "abbu", "abba", "baba", "father"]):
            role = "father"
        elif any(r in tokens for r in ["চাচা", "কাকা", "মামা", "খালা", "ফুফু", "chacha", "kaka", "mama", "khala", "fupu"]):
            role = "relative"
        elif any(r in tokens for r in ["ড্রাইভার", "driver"]):
            role = "driver"
        elif any(r in tokens for r in ["ইলেকট্রিশিয়ান", "মিস্ত্রি", "electrician", "mistri"]):
            role = "electrician"
        elif any(r in tokens for r in ["ডাক্তার", "doctor", "daktar", "dr"]):
            role = "doctor"
        elif any(r in tokens for r in ["বস", "boss", "স্যার", "sir"]):
            role = "supervisor"

        if modifier and not role:
            if any(t in raw_tokens for t in ["ছোটকে", "ছোটরে", "বড়কে", "বড়রে"]):
                role = "chele"

        return modifier, role

    def resolve_contact(self, query: str) -> Dict[str, Any]:
        """
        Resolves spoken query to contact with kinship reasoning and ambiguity detection.
        """
        modifier, role = self.extract_kinship_from_query(query)

        # 1. Kinship Graph & Relational Handling
        if role:
            # Case A: Generic role without modifier (e.g. "আমার ছেলেকে কল দাও")
            if role in self.kinship.get("category_groups", {}) and not modifier:
                candidate_rel_keys = self.kinship["category_groups"][role]
                candidates = [self.kinship["relations"][k] for k in candidate_rel_keys if k in self.kinship["relations"]]
                
                if len(candidates) > 1:
                    desc_list = [f"{c['bangla_labels'][0]} ({c['name']})" for c in candidates]
                    clarification = f"আপনার {len(candidates)} জন {role} তালিকায় রয়েছে: {', '.join(desc_list)}। আপনি কাকে কল করতে চান?"
                    return {
                        "status": "AMBIGUOUS",
                        "role": role,
                        "candidates": candidates,
                        "clarification_message": clarification
                    }
                elif len(candidates) == 1:
                    match_info = candidates[0]
                    return {
                        "status": "RESOLVED",
                        "method": "kinship_single_child",
                        "contact": self.contact_by_id[match_info["contact_id"]],
                        "phone": match_info["phone"],
                        "confidence": 0.95
                    }

            # Case B: Specific Kinship Mapping (e.g. "ছোট ছেলে", "বড় মেয়ে", "বউ")
            rel_key = None
            if modifier and role:
                if modifier == "boro" and role == "chele": rel_key = "elder_son"
                elif modifier == "choto" and role == "chele": rel_key = "younger_son"
                elif modifier in ["mejo", "sejo"] and role == "chele": rel_key = "middle_son"
                elif modifier == "boro" and role == "meye": rel_key = "elder_daughter"
                elif modifier == "choto" and role == "meye": rel_key = "younger_daughter"
                elif modifier == "boro" and role == "jamai": rel_key = "son_in_law"
            elif role in [
                "spouse", "sister_in_law", "brother_in_law_dulabhai", "brother_in_law_sala",
                "father", "mother", "driver", "electrician", "doctor", "supervisor"
            ]:
                rel_key = role

            if rel_key and rel_key in self.kinship["relations"]:
                rel_info = self.kinship["relations"][rel_key]
                contact_entry = self.contact_by_id.get(rel_info["contact_id"])
                if contact_entry:
                    return {
                        "status": "RESOLVED",
                        "method": "kinship_graph",
                        "relation_role": rel_key,
                        "contact": contact_entry,
                        "phone": rel_info["phone"],
                        "confidence": 0.98
                    }

        # 2. Direct Name or Nickname Matching
        raw_tokens = self._tokenize(query)
        stripped_tokens = [self._strip_case_markers(t.lower()) for t in raw_tokens]
        query_name_tokens = [t for t in stripped_tokens if t not in STOPWORDS]

        if not query_name_tokens:
            return {"status": "NOT_FOUND", "message": "কোনো নাম খুঁজে পাওয়া যায়নি।"}

        best_match = None
        best_score = 0.0

        for c in self.indexed_contacts:
            score = 0.0
            matched = 0
            
            for qt in query_name_tokens:
                # Direct token match (Bangla or Latin)
                if qt in c["clean_tokens"] or qt in c["nick_tokens"]:
                    matched += 1
                # Transliterated token match
                elif qt in BN_TO_EN_NAME_MAP:
                    mapped_en = BN_TO_EN_NAME_MAP[qt]
                    if mapped_en in c["clean_tokens"] or mapped_en in c["nick_tokens"]:
                        matched += 1

            if matched > 0:
                total_tokens = len(c["clean_tokens"])
                score = matched / max(total_tokens, len(query_name_tokens))
                if role and role == c["relation"]:
                    score += 0.3

            if score > best_score:
                best_score = score
                best_match = c

        if best_match and best_score >= 0.35:
            return {
                "status": "RESOLVED",
                "method": "direct_name_match",
                "contact": best_match["raw"],
                "phone": best_match["raw"]["phone"],
                "confidence": round(min(1.0, best_score + 0.3), 2)
            }

        return {
            "status": "NOT_FOUND",
            "message": "কোনো কন্টাক্ট খুঁজে পাওয়া যায়নি। দয়া করে নামটি আবার বলুন।"
        }

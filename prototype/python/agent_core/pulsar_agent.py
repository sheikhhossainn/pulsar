"""
Pulsar Unified Voice Agent Orchestrator
Connects INT8 BanglaBERT Intent Model + Contact & Kinship Resolver + Smart File Pinpointer
Produces production-ready offline Android Action Execution Payloads.
"""

import json
import os
import sys
import time
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import onnxruntime as ort
from tokenizers import Tokenizer
from typing import Dict, List, Optional, Tuple, Any

from agent_core.contact_resolver import ContactResolver
from agent_core.file_pinpointer import FilePinpointer

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

class PulsarAgent:
    def __init__(
        self,
        onnx_model_path: str,
        tokenizer_path: str,
        metadata_path: str,
        contacts_path: str,
        kinship_graph_path: str,
        storage_root: str
    ):
        print("[PulsarAgent] Initializing offline engine components...")
        t0 = time.time()
        
        # 1. Load ONNX Session (CPU INT8)
        sess_options = ort.SessionOptions()
        sess_options.intra_op_num_threads = 2
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(onnx_model_path, sess_options, providers=["CPUExecutionProvider"])
        
        # 2. Load Fast Tokenizer
        self.tokenizer = Tokenizer.from_file(tokenizer_path)
        
        # 3. Load Intent Metadata
        with open(metadata_path, "r", encoding="utf-8") as f:
            metadata = json.load(f)
            self.id2label = {int(k): v for k, v in metadata["id2label"].items()}
            self.label2id = metadata["label2id"]

        # 4. Load Contact Resolver
        self.contact_resolver = ContactResolver(contacts_path, kinship_graph_path)

        # 5. Load File Pinpointer
        self.file_pinpointer = FilePinpointer(storage_root)

        init_duration_ms = (time.time() - t0) * 1000.0
        print(f"[PulsarAgent] System initialized successfully in {init_duration_ms:.1f} ms.")

    def predict_intent(self, query: str) -> Tuple[str, float, float]:
        """
        Runs BanglaBERT INT8 inference on query text with rule-guided neural disambiguation.
        Returns: (intent_name, confidence, latency_ms)
        """
        t0 = time.perf_counter()
        
        # Tokenize
        enc = self.tokenizer.encode(query)
        input_ids = np.array([enc.ids], dtype=np.int64)
        attention_mask = np.array([enc.attention_mask], dtype=np.int64)

        # Run ONNX inference
        outputs = self.session.run(None, {
            "input_ids": input_ids,
            "attention_mask": attention_mask
        })
        logits = outputs[0][0]
        
        # Softmax
        exp_logits = np.exp(logits - np.max(logits))
        probs = exp_logits / np.sum(exp_logits)
        
        pred_idx = int(np.argmax(probs))
        confidence = float(probs[pred_idx])
        intent_name = self.id2label[pred_idx]

        # Industrial Rule-Guided Neural Guardrails
        q_lower = query.lower()

        # 1. Email checking action
        if any(e in q_lower for e in ["ইমেইল", "ইমেইলটা", "মেইল", "মেইলটা", "email"]):
            intent_name = "CHECK_EMAIL"
            confidence = 0.95

        # 2. IMG_TO_PDF requires explicit PDF conversion keywords
        has_pdf_keyword = any(k in q_lower for k in ["পিডিএফ", "pdf", "খাড়া কর", "কনভার্ট"])
        if intent_name == "IMG_TO_PDF" and not has_pdf_keyword:
            # Re-route to VIEW_MEDIA if user asked to open/show/view file
            if any(v in q_lower for v in ["ওপেন", "দেখাও", "বের কর", "খোল", "শোনাও", "বাইর কর"]):
                intent_name = "VIEW_MEDIA"
                confidence = 0.90

        # 2. Messaging keywords prioritize SEND_WHATSAPP over MAKE_CALL
        if any(m in q_lower for m in ["মেসেজ", "বার্তা", "বলিস", "টেক্সট", "পাঠাও"]):
            if intent_name in ["MAKE_CALL", "REPLY_MESSAGE"]:
                intent_name = "SEND_WHATSAPP"
                confidence = 0.92

        # 3. File retrieval keywords prioritize VIEW_MEDIA over WEB_SEARCH
        if any(v in q_lower for v in ["বের কর", "ওপেন কর", "দেখাও", "শোনাও", "টিকিটটা", "ছবিটা"]):
            if intent_name in ["WEB_SEARCH", "SEND_WHATSAPP"]:
                intent_name = "VIEW_MEDIA"
                confidence = 0.88

        # 4. Search keywords prioritize WEB_SEARCH
        if any(s in q_lower for s in ["সার্চ করো", "সার্চ কর", "গুগলে", "খোঁজ"]):
            intent_name = "WEB_SEARCH"
            confidence = 0.95
        
        latency_ms = (time.perf_counter() - t0) * 1000.0
        return intent_name, confidence, latency_ms

    def process_utterance(self, utterance: str) -> Dict[str, Any]:
        """
        Full end-to-end processing: Intent Prediction -> Entity/Kinship/File Resolution -> Action Payload.
        """
        t_start = time.perf_counter()
        
        # 1. Intent Recognition
        intent, intent_conf, intent_lat_ms = self.predict_intent(utterance)
        
        payload = {
            "query": utterance,
            "intent": intent,
            "intent_confidence": round(intent_conf, 4),
            "intent_latency_ms": round(intent_lat_ms, 2),
            "status": "SUCCESS",
            "action_type": "",
            "action_params": {},
            "spoken_response": ""
        }

        # 2. Route by Intent
        t_entity_start = time.perf_counter()

        if intent in ["MAKE_CALL", "SEND_WHATSAPP", "REPLY_MESSAGE"]:
            res = self.contact_resolver.resolve_contact(utterance)
            payload["resolution_details"] = res
            
            if res["status"] == "RESOLVED":
                contact = res["contact"]
                phone = res["phone"]
                method = res.get("method", "direct")
                
                if intent == "MAKE_CALL":
                    payload["action_type"] = "ANDROID_CALL"
                    payload["action_params"] = {
                        "intent_action": "android.intent.action.CALL",
                        "uri": f"tel:{phone}",
                        "recipient_name": contact["name"],
                        "phone_number": phone
                    }
                    payload["spoken_response"] = f"{contact['name']}কে ফোন দেওয়া হচ্ছে..."
                elif intent in ["SEND_WHATSAPP", "REPLY_MESSAGE"]:
                    payload["action_type"] = "WHATSAPP_SEND"
                    payload["action_params"] = {
                        "package": "com.whatsapp",
                        "phone_number": phone,
                        "recipient_name": contact["name"]
                    }
                    payload["spoken_response"] = f"হোয়াটসঅ্যাপে {contact['name']}কে বার্তা পাঠানো হচ্ছে..."
            elif res["status"] == "AMBIGUOUS":
                payload["status"] = "AMBIGUOUS_CONTACT"
                payload["action_type"] = "PROMPT_USER"
                payload["action_params"] = {
                    "candidates": [c["name"] for c in res["candidates"]]
                }
                payload["spoken_response"] = res["clarification_message"]
            else:
                payload["status"] = "CONTACT_NOT_FOUND"
                payload["action_type"] = "NOTIFY_USER"
                payload["spoken_response"] = "আপনার কন্টাক্ট লিস্টে কোনো মিল খুঁজে পাওয়া যায়নি।"

        elif intent in ["VIEW_MEDIA", "IMG_TO_PDF"]:
            file_res = self.file_pinpointer.pinpoint_file(utterance)
            payload["resolution_details"] = file_res
            
            if file_res["status"] == "PINPOINTED":
                fpath = file_res["file_path"]
                fname = file_res["filename"]
                rel_path = file_res["relative_path"]
                
                if intent == "VIEW_MEDIA":
                    payload["action_type"] = "OPEN_FILE"
                    payload["action_params"] = {
                        "intent_action": "android.intent.action.VIEW",
                        "file_path": fpath,
                        "relative_path": rel_path,
                        "filename": fname,
                        "confidence": file_res["confidence"]
                    }
                    payload["spoken_response"] = f"{fname} ফাইলটি ওপেন করা হচ্ছে।"
                elif intent == "IMG_TO_PDF":
                    pdf_out = os.path.splitext(fpath)[0] + "_converted.pdf"
                    payload["action_type"] = "CONVERT_IMG_TO_PDF"
                    payload["action_params"] = {
                        "source_file": fpath,
                        "relative_path": rel_path,
                        "output_pdf": pdf_out,
                        "confidence": file_res["confidence"]
                    }
                    payload["spoken_response"] = f"{fname} ছবিটিকে পিডিএফ রূপান্তর করা হচ্ছে।"
            else:
                payload["status"] = "FILE_NOT_FOUND"
                payload["action_type"] = "NOTIFY_USER"
                payload["spoken_response"] = "অনুরোধ করা ফাইলটি মেমোরিতে খুঁজে পাওয়া যায়নি।"

        elif intent == "WEB_SEARCH":
            # Extract keywords
            clean_q = utterance.replace("সার্চ করো", "").replace("সার্চ কর", "").replace("গুগলে", "").strip()
            payload["action_type"] = "WEB_SEARCH"
            payload["action_params"] = {
                "intent_action": "android.intent.action.WEB_SEARCH",
                "search_query": clean_q
            }
            payload["spoken_response"] = f"গুগলে {clean_q} লিখে সার্চ করা হচ্ছে।"

        elif intent == "CHECK_EMAIL":
            res = self.contact_resolver.resolve_contact(utterance)
            payload["resolution_details"] = res
            if res["status"] == "RESOLVED":
                contact = res["contact"]
                payload["action_type"] = "OPEN_EMAIL"
                payload["action_params"] = {
                    "intent_action": "android.intent.action.VIEW",
                    "filter_sender": contact["name"],
                    "query": f"from:{contact['name']}"
                }
                payload["spoken_response"] = f"{contact['name']}র ইমেইল চেক করা হচ্ছে..."
            else:
                payload["action_type"] = "OPEN_EMAIL_INBOX"
                payload["action_params"] = {
                    "intent_action": "android.intent.action.MAIN",
                    "category": "android.intent.category.APP_EMAIL"
                }
                payload["spoken_response"] = "ইমেইল ইনবক্স ওপেন করা হচ্ছে..."

        entity_lat_ms = (time.perf_counter() - t_entity_start) * 1000.0
        total_lat_ms = (time.perf_counter() - t_start) * 1000.0
        
        payload["entity_latency_ms"] = round(entity_lat_ms, 2)
        payload["total_latency_ms"] = round(total_lat_ms, 2)
        
        return payload

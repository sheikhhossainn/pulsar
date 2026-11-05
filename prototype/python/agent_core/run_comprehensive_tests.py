"""
Pulsar Comprehensive Evaluation Suite
Tests 100-Contact Database, Elder Kinship Graph, and Smart File Pinpointer
across diverse Dhaka speech registers, slang variants, and real-world edge cases.
"""

import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import pandas as pd
from typing import List, Dict, Any

from agent_core.pulsar_agent import PulsarAgent

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Comprehensive Test Dataset across Edge Cases
TEST_CASES = [
    # Category 1: Elder Kinship Disconnect (Saved by Name, Spoken by Kinship Role)
    {
        "id": "TC01",
        "category": "Kinship_Elder_Disconnect",
        "query": "আমার ছোট ছেলেকে ফোন দাও",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Rifat Hasan",
        "notes": "Elderly calls younger son; contact saved as 'Rifat Hasan'"
    },
    {
        "id": "TC02",
        "category": "Kinship_Elder_Disconnect",
        "query": "ছোটকে একটা রিং দে তো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Rifat Hasan",
        "notes": "Elderly uses colloquial modifier 'ছোটকে' with street slang 'রিং দে'"
    },
    {
        "id": "TC03",
        "category": "Kinship_Elder_Disconnect",
        "query": "বড় ছেলেকে একটু ফোন মারো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Tanvir Ahmed",
        "notes": "Elderly calls elder son; contact saved as 'Tanvir Ahmed'"
    },
    {
        "id": "TC04",
        "category": "Kinship_Elder_Disconnect",
        "query": "বড় মেয়েকে একটা রিং মার",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Nabila Rahman",
        "notes": "Elderly calls daughter; contact saved as 'Nabila Rahman'"
    },
    {
        "id": "TC05",
        "category": "Kinship_Elder_Disconnect",
        "query": "ছোট মেয়েরে একটা কল দাও",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Tasnim Jahan",
        "notes": "Accusative inflection 'মেয়েরে' mapped to younger daughter"
    },
    {
        "id": "TC06",
        "category": "Kinship_Elder_Disconnect",
        "query": "বড় জামাইরে একটা মেসেজ পাঠাও",
        "expected_intent": "SEND_WHATSAPP",
        "expected_action": "WHATSAPP_SEND",
        "expected_entity": "Fahim Kabir",
        "notes": "Son-in-law relationship mapped to 'Fahim Kabir'"
    },
    {
        "id": "TC07",
        "category": "Kinship_Elder_Disconnect",
        "query": "বউরে একটা কল দে",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Rokeya Begum",
        "notes": "Spouse relation 'বউরে' mapped to 'Rokeya Begum'"
    },

    # Category 2: Kinship Ambiguity Detection
    {
        "id": "TC08",
        "category": "Kinship_Ambiguity",
        "query": "আমার ছেলেকে ফোন দাও তো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "PROMPT_USER",
        "expected_entity": "AMBIGUOUS",
        "notes": "Generic 'ছেলে' with 3 sons; agent must detect ambiguity and prompt"
    },
    {
        "id": "TC09",
        "category": "Kinship_Ambiguity",
        "query": "আমার মেয়েকে একটা কল করো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "PROMPT_USER",
        "expected_entity": "AMBIGUOUS",
        "notes": "Generic 'মেয়ে' with 2 daughters; agent must prompt user"
    },

    # Category 3: Direct Name, Nicknames & Local Service Trades
    {
        "id": "TC10",
        "category": "Direct_Contact_Match",
        "query": "ড্রাইভার সোহেলকে ডাক দে",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Driver Sohel",
        "notes": "Trade prefix 'Driver Sohel'"
    },
    {
        "id": "TC11",
        "category": "Direct_Contact_Match",
        "query": "বিদ্যুৎ মিস্ত্রি রহিমকে ডাকো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Electrician Rahim",
        "notes": "Service trade 'Electrician Rahim'"
    },
    {
        "id": "TC12",
        "category": "Direct_Contact_Match",
        "query": "ডাক্তার মাহফুজকে রিং মারো",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Dr. Mahfuz Dental",
        "notes": "Medical contact 'Dr. Mahfuz Dental'"
    },
    {
        "id": "TC13",
        "category": "Direct_Contact_Match",
        "query": "তানভীর আহমেদকে ফোন কর",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Tanvir Ahmed",
        "notes": "Full name Bangla transliteration"
    },
    {
        "id": "TC14",
        "category": "Direct_Contact_Match",
        "query": "হোয়াটসঅ্যাপে বাবাকে মেসেজ দাও",
        "expected_intent": "SEND_WHATSAPP",
        "expected_action": "WHATSAPP_SEND",
        "expected_entity": "Abbu",
        "notes": "WhatsApp messaging to parent"
    },

    # Category 4: Smart File Pinpointing
    {
        "id": "TC15",
        "category": "File_Pinpointing",
        "query": "আমার ছেলে একটা ছবি দিছে হোয়াটসঅ্যাপে, বের কর তো",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "IMG-20261022-WA0001.jpg",
        "notes": "User's exact quote from prompt: WhatsApp + son + recent image"
    },
    {
        "id": "TC16",
        "category": "File_Pinpointing",
        "query": "লাস্টের ছবিটা পিডিএফ বানাও",
        "expected_intent": "IMG_TO_PDF",
        "expected_action": "CONVERT_IMG_TO_PDF",
        "expected_entity": "IMG-20261022-WA0001.jpg",
        "notes": "Recent image to PDF conversion"
    },
    {
        "id": "TC17",
        "category": "File_Pinpointing",
        "query": "বসের পাঠানো ইনভয়েসটা বের কর",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "Invoice_Project_Alpha_Oct2026.pdf",
        "notes": "Boss entity + Invoice keyword"
    },
    {
        "id": "TC18",
        "category": "File_Pinpointing",
        "query": "গতকালকের প্রেসক্রিপশনটা দেখাও",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "IMG_20261021_093000.jpg",
        "notes": "Temporal filter 'গতকালকের' + prescription"
    },
    {
        "id": "TC19",
        "category": "File_Pinpointing",
        "query": "ডাউনলোড করা বিদ্যুৎ বিলের কাগজটা ওপেন কর",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "DESCO_Electricity_Bill_October_2026.pdf",
        "notes": "Channel 'Download' + electricity bill"
    },
    {
        "id": "TC20",
        "category": "File_Pinpointing",
        "query": "এনআইডি কার্ডের কপিটা বের কর",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "Smart_NID_Card_Rafiqul_Islam.pdf",
        "notes": "Identity document pinpointing"
    },
    {
        "id": "TC21",
        "category": "File_Pinpointing",
        "query": "মিটিংয়ের ভয়েস রেকর্ডিংটা শোনাও",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "VoiceNote_Building_Meeting_Oct22.m4a",
        "notes": "Audio voice recording retrieval"
    },
    {
        "id": "TC22",
        "category": "File_Pinpointing",
        "query": "বড় ছেলের পাঠানো বিমানের টিকিটটা বের কর",
        "expected_intent": "VIEW_MEDIA",
        "expected_action": "OPEN_FILE",
        "expected_entity": "Biman_Bangladesh_E-Ticket_Dhaka_CoxsBazar.pdf",
        "notes": "Elder son + travel ticket PDF"
    },

    # Category 5: Multi-Register Slang & Pronoun Variants (tui, tumi, apni, apne)
    {
        "id": "TC23",
        "category": "Slang_and_Registers",
        "query": "তুই ছোট পোলারে একটা ঘণ্টি দে",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Rifat Hasan",
        "notes": "Slang 'তোর/তুই', 'পোলারে', 'ঘণ্টি দে'"
    },
    {
        "id": "TC24",
        "category": "Slang_and_Registers",
        "query": "আপনি ড্রাইভার সোহেলকে একটা রিং মারেন",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Driver Sohel",
        "notes": "Formal register 'আপনি', 'রিং মারেন'"
    },
    {
        "id": "TC25",
        "category": "Slang_and_Registers",
        "query": "লাস্টের ফটোটারে পিডিএফ খাড়া কর তো",
        "expected_intent": "IMG_TO_PDF",
        "expected_action": "CONVERT_IMG_TO_PDF",
        "expected_entity": "IMG-20261022-WA0001.jpg",
        "notes": "Street slang 'ফটোটারে পিডিএফ খাড়া কর'"
    },
    {
        "id": "TC26",
        "category": "Slang_and_Registers",
        "query": "বসরে বলিস আজকের রিপোর্ট রেডি",
        "expected_intent": "SEND_WHATSAPP",
        "expected_action": "WHATSAPP_SEND",
        "expected_entity": "Tareq Boss",
        "notes": "Office colleague slang register"
    },
    {
        "id": "TC27",
        "category": "Slang_and_Registers",
        "query": "ডাক্তাররে কল লাগা আর্জেন্ট",
        "expected_intent": "MAKE_CALL",
        "expected_action": "ANDROID_CALL",
        "expected_entity": "Dr. Mahfuz Dental",
        "notes": "Emergency slang 'কল লাগা আর্জেন্ট'"
    },
    {
        "id": "TC28",
        "category": "Slang_and_Registers",
        "query": "গুগলে আজকের ঢাকার আবহাওয়া সার্চ করো",
        "expected_intent": "WEB_SEARCH",
        "expected_action": "WEB_SEARCH",
        "expected_entity": "SEARCH",
        "notes": "Weather web search"
    }
]

def run_evaluation():
    print("=" * 80)
    print("PULSAR OFFLINE VOICE AGENT - COMPREHENSIVE BENCHMARK")
    print("=" * 80)

    agent = PulsarAgent(
        onnx_model_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\banglabert_small_intent_int8.onnx",
        tokenizer_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\intent_tokenizer\tokenizer.json",
        metadata_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\intent_model_metadata.json",
        contacts_path=r"D:\Coding\Pulsar\prototype\python\agent_core\data\contacts_100.json",
        kinship_graph_path=r"D:\Coding\Pulsar\prototype\python\agent_core\data\kinship_graph.json",
        storage_root=r"D:\Coding\Pulsar\prototype\python\agent_core\data\mock_storage"
    )

    results = []
    intent_correct = 0
    action_correct = 0
    entity_correct = 0
    total = len(TEST_CASES)

    latencies_intent = []
    latencies_entity = []
    latencies_total = []

    print("\nExecuting Test Cases:\n")

    for tc in TEST_CASES:
        res = agent.process_utterance(tc["query"])
        
        # Verify Intent
        pred_intent = res["intent"]
        is_intent_ok = (pred_intent == tc["expected_intent"])
        if is_intent_ok:
            intent_correct += 1

        # Verify Action
        pred_action = res["action_type"]
        is_action_ok = (pred_action == tc["expected_action"])
        if is_action_ok:
            action_correct += 1

        # Verify Entity / Target
        is_entity_ok = False
        pred_entity_str = ""
        
        if tc["expected_entity"] == "AMBIGUOUS":
            is_entity_ok = (res["status"] == "AMBIGUOUS_CONTACT")
            pred_entity_str = "AMBIGUOUS_DETECTED" if is_entity_ok else res["status"]
        elif tc["expected_action"] == "ANDROID_CALL" or tc["expected_action"] == "WHATSAPP_SEND":
            resolved_contact = res.get("resolution_details", {}).get("contact", {})
            name = resolved_contact.get("name", "")
            is_entity_ok = (tc["expected_entity"] in name)
            pred_entity_str = name
        elif tc["expected_action"] in ["OPEN_FILE", "CONVERT_IMG_TO_PDF"]:
            filename = res.get("action_params", {}).get("filename", "") or os.path.basename(res.get("action_params", {}).get("source_file", ""))
            is_entity_ok = (tc["expected_entity"] == filename)
            pred_entity_str = filename
        elif tc["expected_action"] == "WEB_SEARCH":
            is_entity_ok = True
            pred_entity_str = res.get("action_params", {}).get("search_query", "")

        if is_entity_ok:
            entity_correct += 1

        pass_all = is_intent_ok and is_action_ok and is_entity_ok

        latencies_intent.append(res["intent_latency_ms"])
        latencies_entity.append(res["entity_latency_ms"])
        latencies_total.append(res["total_latency_ms"])

        status_flag = "PASS" if pass_all else "FAIL"
        print(f"[{status_flag}] {tc['id']} ({tc['category']}): '{tc['query']}'")
        print(f"       Intent: {pred_intent} ({res['intent_confidence']:.2f}) | Action: {pred_action}")
        print(f"       Entity: {pred_entity_str} (Expected: {tc['expected_entity']})")
        print(f"       Latency: Total={res['total_latency_ms']:.1f}ms (Intent={res['intent_latency_ms']:.1f}ms, Entity={res['entity_latency_ms']:.1f}ms)")
        if not pass_all:
            print(f"       >>> MISMATCH: Expected Intent={tc['expected_intent']}, Action={tc['expected_action']}, Entity={tc['expected_entity']}")
        print()

        results.append({
            "id": tc["id"],
            "category": tc["category"],
            "query": tc["query"],
            "expected_intent": tc["expected_intent"],
            "predicted_intent": pred_intent,
            "intent_confidence": res["intent_confidence"],
            "is_intent_ok": is_intent_ok,
            "expected_action": tc["expected_action"],
            "predicted_action": pred_action,
            "is_action_ok": is_action_ok,
            "expected_entity": tc["expected_entity"],
            "predicted_entity": pred_entity_str,
            "is_entity_ok": is_entity_ok,
            "pass_all": pass_all,
            "intent_latency_ms": res["intent_latency_ms"],
            "entity_latency_ms": res["entity_latency_ms"],
            "total_latency_ms": res["total_latency_ms"],
            "spoken_response": res["spoken_response"]
        })

    # Summary Statistics
    intent_acc = (intent_correct / total) * 100.0
    action_acc = (action_correct / total) * 100.0
    entity_acc = (entity_correct / total) * 100.0
    overall_acc = (sum(r["pass_all"] for r in results) / total) * 100.0

    avg_intent_lat = sum(latencies_intent) / len(latencies_intent)
    avg_entity_lat = sum(latencies_entity) / len(latencies_entity)
    avg_total_lat = sum(latencies_total) / len(latencies_total)

    print("=" * 80)
    print("FINAL BENCHMARK RESULTS")
    print("=" * 80)
    print(f"Total Test Cases:               {total}")
    print(f"Intent Recognition Accuracy:    {intent_acc:.1f}% ({intent_correct}/{total})")
    print(f"Action Routing Accuracy:        {action_acc:.1f}% ({action_correct}/{total})")
    print(f"Entity/File Resolution Acc:     {entity_acc:.1f}% ({entity_correct}/{total})")
    print(f"End-to-End System Accuracy:     {overall_acc:.1f}%")
    print("-" * 80)
    print(f"Avg Intent Model Latency:       {avg_intent_lat:.2f} ms")
    print(f"Avg Entity/File Search Latency: {avg_entity_lat:.2f} ms")
    print(f"Avg End-to-End Latency:         {avg_total_lat:.2f} ms (Budget: < 35.0 ms)")
    print("=" * 80)

    # Save detailed CSV
    df = pd.DataFrame(results)
    out_csv = r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\comprehensive_agent_evaluation.csv"
    df.to_csv(out_csv, index=False, encoding="utf-8-sig")
    print(f"\nSaved detailed evaluation metrics to: {out_csv}")

if __name__ == "__main__":
    run_evaluation()

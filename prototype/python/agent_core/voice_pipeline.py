"""
Pulsar End-to-End Voice Pipeline
Audio Input (.wav / live microphone) -> Streaming Zipformer ASR -> INT8 BanglaBERT Intent
-> Kinship/Contact Resolver / File Pinpointer -> Action Execution Payload
"""

import json
import os
import sys
import time
import numpy as np
import soundfile as sf
import sherpa_onnx

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from agent_core.pulsar_agent import PulsarAgent

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

class PulsarVoicePipeline:
    def __init__(
        self,
        asr_model_dir: str,
        hotwords_path: str = r"D:\Coding\Pulsar\prototype\python\hotwords\bangladesh_daily_hotwords.txt",
        hotwords_score: float = 2.5
    ):
        print("\n[PulsarVoicePipeline] Loading Streaming Zipformer ASR with Hotwords Biasing...")
        t0 = time.time()
        
        # Configure ASR Recognizer with Modified Beam Search & Daily Hotwords
        if hotwords_path and os.path.exists(hotwords_path):
            self.asr_recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
                encoder=os.path.join(asr_model_dir, "encoder.onnx"),
                decoder=os.path.join(asr_model_dir, "decoder.onnx"),
                joiner=os.path.join(asr_model_dir, "joiner.onnx"),
                tokens=os.path.join(asr_model_dir, "tokens.txt"),
                num_threads=2,
                sample_rate=16000,
                feature_dim=80,
                decoding_method="modified_beam_search",
                max_active_paths=8,
                hotwords_file=hotwords_path,
                hotwords_score=hotwords_score
            )
            print(f"[PulsarVoicePipeline] Hotwords active: {hotwords_path} (score: {hotwords_score}, paths: 8)")
        else:
            self.asr_recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
                encoder=os.path.join(asr_model_dir, "encoder.onnx"),
                decoder=os.path.join(asr_model_dir, "decoder.onnx"),
                joiner=os.path.join(asr_model_dir, "joiner.onnx"),
                tokens=os.path.join(asr_model_dir, "tokens.txt"),
                num_threads=2,
                sample_rate=16000,
                feature_dim=80,
                decoding_method="greedy_search"
            )
        print(f"[PulsarVoicePipeline] ASR loaded in {(time.time() - t0)*1000:.1f} ms.")

        # Load Intent & Entity Agent
        self.agent = PulsarAgent(
            onnx_model_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\banglabert_small_intent_int8.onnx",
            tokenizer_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\intent_tokenizer\tokenizer.json",
            metadata_path=r"D:\Coding\Pulsar\prototype\python\kaggle_kernel\output\intent_model_metadata.json",
            contacts_path=r"D:\Coding\Pulsar\prototype\python\agent_core\data\contacts_100.json",
            kinship_graph_path=r"D:\Coding\Pulsar\prototype\python\agent_core\data\kinship_graph.json",
            storage_root=r"D:\Coding\Pulsar\prototype\python\agent_core\data\mock_storage"
        )

    def transcribe_audio(self, audio_data: np.ndarray, sample_rate: int = 16000) -> Tuple[str, float]:
        """
        Streams audio waveform into Zipformer ASR and returns transcribed Bangla text + ASR latency.
        """
        t0 = time.perf_counter()
        
        # Ensure audio is float32 and mono
        if audio_data.dtype != np.float32:
            audio_data = audio_data.astype(np.float32)
        if len(audio_data.shape) > 1:
            audio_data = np.mean(audio_data, axis=1)

        # Resample if not 16000Hz (simple linear interpolation for speed)
        if sample_rate != 16000:
            target_len = int(len(audio_data) * 16000 / sample_rate)
            indices = np.linspace(0, len(audio_data) - 1, target_len)
            audio_data = np.interp(indices, np.arange(len(audio_data)), audio_data)
            sample_rate = 16000

        stream = self.asr_recognizer.create_stream()
        
        # Feed in chunks (simulate real-time 100ms streaming chunks)
        chunk_size = int(0.1 * sample_rate) # 100ms
        for i in range(0, len(audio_data), chunk_size):
            chunk = audio_data[i:i + chunk_size]
            stream.accept_waveform(sample_rate, chunk)
            while self.asr_recognizer.is_ready(stream):
                self.asr_recognizer.decode_stream(stream)

        # Final decode
        while self.asr_recognizer.is_ready(stream):
            self.asr_recognizer.decode_stream(stream)
            
        transcription = self.asr_recognizer.get_result(stream).strip()
        asr_lat_ms = (time.perf_counter() - t0) * 1000.0
        
        return transcription, asr_lat_ms

    def process_audio_file(self, wav_path: str) -> dict:
        """Process an audio file end-to-end."""
        print(f"\n[Processing Audio File]: {wav_path}")
        audio_data, sr = sf.read(wav_path)
        return self.process_audio(audio_data, sr)

    def process_audio(self, audio_data: np.ndarray, sample_rate: int) -> dict:
        """Full end-to-end processing: Voice -> Transcription -> Intent -> Action Payload."""
        t_total_start = time.perf_counter()
        
        # 1. ASR Transcription
        transcription, asr_lat_ms = self.transcribe_audio(audio_data, sample_rate)
        
        if not transcription:
            return {
                "status": "SILENCE_OR_EMPTY",
                "transcription": "",
                "asr_latency_ms": round(asr_lat_ms, 2),
                "total_latency_ms": round((time.perf_counter() - t_total_start) * 1000.0, 2),
                "spoken_response": "কোনো কথা শোনা যায়নি। দয়া করে আবার বলুন।"
            }

        # 2. Agent Processing (Intent + Kinship/Contact/File Resolution)
        agent_res = self.agent.process_utterance(transcription)
        
        total_lat_ms = (time.perf_counter() - t_total_start) * 1000.0
        
        result = {
            **agent_res,
            "transcription": transcription,
            "asr_latency_ms": round(asr_lat_ms, 2),
            "total_latency_ms": round(total_lat_ms, 2)
        }
        return result

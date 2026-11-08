"""Restore the pinned offline Bengali model and Android runtime for a clean checkout."""
from hashlib import sha256
from pathlib import Path
import shutil
import tarfile
import tempfile
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download"
MODEL = "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09"
RUNTIME = "sherpa-onnx-2.0.1.aar"

def digest(path):
    with path.open("rb") as source:
        hasher = sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
        return hasher.hexdigest()

def fetch(url, output, checksum):
    if output.is_file() and digest(output) == checksum:
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    partial = output.with_suffix(".partial")
    with urlopen(Request(url, headers={"User-Agent": "Pulsar-build"}), timeout=120) as source, partial.open("wb") as dest:
        shutil.copyfileobj(source, dest)
    if digest(partial) != checksum:
        partial.unlink()
        raise ValueError(f"Checksum mismatch for {url}")
    partial.replace(output)

def main():
    fetch(f"{RELEASES}/v2.0.1/{RUNTIME}", ROOT / "app/libs" / RUNTIME,
          "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7")
    with tempfile.TemporaryDirectory() as temporary:
        archive = Path(temporary) / "model.tar.bz2"
        fetch(f"{RELEASES}/asr-models/{MODEL}.tar.bz2", archive,
              "1bc89d81ee33ba7ddef8454736aeeb55e3cc1593274272485c6da342202b4b18")
        folder = ROOT / "app/src/main/assets/asr"
        folder.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as tar:
            for name in ("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt"):
                member = tar.getmember(f"{MODEL}/{name}")
                if not member.isfile():
                    raise ValueError(name)
                with tar.extractfile(member) as source, (folder / name).open("wb") as output:
                    shutil.copyfileobj(source, output)
    print("Offline speech assets ready.")
if __name__ == "__main__":
    main()


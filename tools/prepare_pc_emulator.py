"""Install Google's pinned Windows emulator and API 35 image under .buildenv."""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha1
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / ".buildenv/sdk"
CACHE = ROOT / ".buildenv/emulator-downloads"
PACKAGES = (
    ("https://dl.google.com/android/repository/emulator-windows_x64-16740729.zip",
     "4d7268aa7787c640ad892844e6c9a5f159e91ad3", SDK / "emulator", "emulator.exe"),
    ("https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip",
     "2d857d170c0d1b827149565da34b3383e5306f7f", SDK / "system-images/android-35/default/x86_64", "system.img"),
)


def install(package):
    url, expected, destination, required = package
    marker = destination / ".pulsar-package"
    if marker.exists() and marker.read_text() == expected and (destination / required).is_file():
        return
    CACHE.mkdir(parents=True, exist_ok=True)
    archive = CACHE / url.rsplit("/", 1)[-1]
    if not archive.exists():
        partial = archive.with_suffix(".partial")
        subprocess.run(["curl.exe", "--fail", "--location", "--silent", "--show-error",
                        "--retry", "2", url, "--output", str(partial)], check=True)
        partial.replace(archive)
    hasher = sha1()
    with archive.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    if hasher.hexdigest() != expected:
        raise ValueError(f"Checksum failed. Remove {archive} and retry.")
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as package_zip:
        for item in package_zip.infolist():
            parts = Path(item.filename).parts[1:]
            if not parts or item.is_dir():
                continue
            output = destination.joinpath(*parts).resolve()
            if destination.resolve() not in output.parents:
                raise ValueError(f"Invalid archive member {item.filename}")
            output.parent.mkdir(parents=True, exist_ok=True)
            with package_zip.open(item) as source, output.open("wb") as target:
                shutil.copyfileobj(source, target)
    if not (destination / required).is_file():
        raise ValueError(f"Missing {required} in {destination}")
    marker.write_text(expected)
    print(f"Ready: {destination}", flush=True)


if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(install, PACKAGES))

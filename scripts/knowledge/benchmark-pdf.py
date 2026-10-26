"""Measure a credential-free 300-page parser child on a synthetic scanned PDF."""
import json
import os
import pathlib
import random
import shutil
import subprocess
import tempfile
import time

import psutil
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAR = pathlib.Path(os.environ.get("KNOWLEDGE_BENCH_JAR", ROOT / "blog-backend/target/blog-backend-0.0.1-SNAPSHOT.jar"))
MAX_BYTES = 20 * 1024 * 1024


def generate(path):
    rng = random.Random(741)
    pixels = 315
    pages = [Image.frombytes("RGB", (pixels, pixels), rng.randbytes(pixels * pixels * 3)) for _ in range(300)]
    try:
        pages[0].save(path, "PDF", save_all=True, append_images=pages[1:], resolution=72, quality=80)
    finally:
        for page in pages:
            page.close()


def measure(source, max_pages, expected_exit):
    java = pathlib.Path(shutil.which("java") or "java")
    command = [str(java), "-Xmx256m", "-XX:MaxDirectMemorySize=64m",
               "-Dloader.main=xyz.luyu.knowledge.KnowledgeFileParser",
               "-cp", str(JAR), "org.springframework.boot.loader.launch.PropertiesLauncher",
               str(source), "application/pdf", str(max_pages)]
    start = time.perf_counter()
    child = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    handle = psutil.Process(child.pid)
    peak_rss = 0
    cpu_seconds = 0.0
    while child.poll() is None:
        try:
            peak_rss = max(peak_rss, handle.memory_info().rss)
            times = handle.cpu_times()
            cpu_seconds = max(cpu_seconds, times.user + times.system)
        except psutil.NoSuchProcess:
            pass
        if time.perf_counter() - start > 65:
            child.kill()
            raise RuntimeError("parser exceeded the 60-second wall-clock budget")
        time.sleep(.05)
    elapsed = time.perf_counter() - start
    if child.returncode != expected_exit:
        raise RuntimeError(f"expected parser exit {expected_exit}, got {child.returncode}")
    return {"parserExit": child.returncode, "wallSeconds": round(elapsed, 2),
            "cpuSeconds": round(cpu_seconds, 2), "peakRssMiB": round(peak_rss / 1024**2, 2)}


def run():
    if not JAR.is_file():
        raise RuntimeError("build the local backend JAR first")
    with tempfile.TemporaryDirectory(prefix="knowledge-resource-") as temp:
        source = pathlib.Path(temp) / "source.pdf"
        generate(source)
        size = source.stat().st_size
        if size > MAX_BYTES:
            raise RuntimeError(f"synthetic PDF exceeds the 20 MiB upload limit: {size}")
        normal = measure(source, 300, 4)
        over_pages = measure(source, 299, 5)
        print(json.dumps({"pages": 300, "pdfBytes": size, "pdfMiB": round(size / 1024**2, 2),
                          "temporaryPdfMiB": round(size / 1024**2, 2),
                          "normal": {**normal, "classification": "NEEDS_OCR"},
                          "overPageLimit": {**over_pages, "classification": "PAGE_LIMIT"}}, ensure_ascii=False))


if __name__ == "__main__":
    run()

"""
Scene 배경 이미지 5장 생성 — Gemini Imagen 4.0 fast.

사용:
  export GEMINI_API_KEY=...
  python3 scripts/generate_scenes.py

결과: frontend/public/images/scenes/{1..5}.jpg
"""
import base64
import os
import sys
import urllib.request
import json
from pathlib import Path

API_KEY = os.environ.get("GEMINI_API_KEY", "")
if not API_KEY:
    sys.exit("ERROR: GEMINI_API_KEY env required")

MODEL = "imagen-4.0-fast-generate-001"
OUT_DIR = Path(__file__).parent.parent / "frontend" / "public" / "images" / "scenes"
OUT_DIR.mkdir(parents=True, exist_ok=True)

SCENES = {
    "1": (
        "Cinematic dimly lit ancient Egyptian palace interior at dawn, golden hieroglyphs "
        "shimmering on stone walls, seven gaunt cows and seven fat cows floating mystically in "
        "the air as dream symbols, ethereal mist, biblical narrative atmosphere, painterly style, "
        "warm golden tones, no faces visible, reverent and contemplative mood."
    ),
    "2": (
        "Vast golden barley fields under bright midday sun in ancient Egypt, abundant harvest, "
        "seven tall stone granaries being constructed in the distance, three large sacks of grain "
        "in the foreground, no people visible, hopeful and prosperous atmosphere, painterly "
        "biblical illustration style."
    ),
    "3": (
        "Cracked dry desert landscape during famine in ancient Egypt, large stone granary in "
        "the background under harsh sun, three queues of dust-covered grain sacks arranged in "
        "the foreground representing distribution lines, dramatic shadows, no people visible, "
        "biblical narrative atmosphere, weight of decision."
    ),
    "4": (
        "Vast Egyptian palace throne hall with massive stone pillars, soft candlelight, golden "
        "ornaments, an empty throne in the background, contemplative and tense atmosphere, "
        "biblical reunion scene aesthetic, painterly style, no people visible, focus on space "
        "and architecture."
    ),
    "5": (
        "Soft warm cosmic meditation space, gentle golden light radiating from center, "
        "abstract ethereal mist, ancient Hebrew scripture letters faintly floating, peaceful "
        "and restorative atmosphere, painterly biblical aesthetic, no faces or figures, "
        "focus on light and serenity."
    ),
}


def gen(scene_id: str, prompt: str):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:predict?key={API_KEY}"
    body = {
        "instances": [{"prompt": prompt}],
        "parameters": {"sampleCount": 1, "aspectRatio": "16:9"},
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    print(f"[scene {scene_id}] generating...")
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.loads(r.read())
    preds = data.get("predictions", [])
    if not preds:
        print(f"[scene {scene_id}] FAIL: no predictions. response: {json.dumps(data)[:300]}")
        return
    b64 = preds[0].get("bytesBase64Encoded")
    if not b64:
        print(f"[scene {scene_id}] FAIL: no bytesBase64Encoded. keys: {list(preds[0].keys())}")
        return
    img = base64.b64decode(b64)
    out = OUT_DIR / f"{scene_id}.jpg"
    out.write_bytes(img)
    print(f"[scene {scene_id}] saved {out} ({len(img)} bytes)")


if __name__ == "__main__":
    for sid, prompt in SCENES.items():
        try:
            gen(sid, prompt)
        except Exception as e:
            print(f"[scene {sid}] EXCEPTION: {e}")

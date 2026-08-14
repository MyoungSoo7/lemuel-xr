"""
인물별 Scene 배경 이미지 생성 — Gemini Imagen 4.0 fast.

`generate_scenes.py` 의 확장판. 그 스크립트는 요셉 5장만, 그것도 평평한
`images/scenes/{1..5}.jpg` 로 떨어뜨렸다. 그런데 scene id 는 인물 구분이 없는
맨 정수라(모세도 1~6, 다윗도 1~6) 같은 폴더에 넣으면 서로 덮어쓴다.
그래서 여기서는 **인물별 하위 폴더**로 나눈다.

사용:
  export GEMINI_API_KEY=...
  python3 scripts/generate_all_scenes.py            # 아직 없는 것만 생성
  python3 scripts/generate_all_scenes.py moses      # 특정 인물만
  python3 scripts/generate_all_scenes.py --force    # 이미 있어도 다시 생성

결과: frontend/public/images/scenes/{character}/{scene_id}.jpg

프롬프트 규칙 (기존 5장에서 이어받은 것):
  - **인물을 그리지 않는다.** 프롬프트에 인물명을 넣으면 Imagen 이 얼굴 있는
    사람을 그려버린다(실측). 그래서 전부 *배경·공간·사물* 로만 묘사한다.
  - 공통 스타일 문구(STYLE)를 모든 프롬프트에 동일하게 붙여 톤을 고정한다.
    요셉 5장이 서로 다른 화풍으로 나온 건 이 공통 문구가 없었기 때문이다.
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

API_KEY = os.environ.get("GEMINI_API_KEY", "")
if not API_KEY:
    sys.exit("ERROR: GEMINI_API_KEY env required")

MODEL = "imagen-4.0-fast-generate-001"
OUT_ROOT = Path(__file__).parent.parent / "frontend" / "public" / "images" / "scenes"

# 모든 프롬프트에 공통으로 붙는 화풍 고정 문구.
#
# 주의 — 초판에는 "so text can be overlaid" 라고 썼다가 Imagen 이 그 'text' 를
# 그리라는 지시로 받아들여, 이미지 하단에 자막처럼 생긴 **의미 없는 글자열**을
# 렌더해 넣었다 (david/5 에서 "Dore ther thehnt ther therid"). 그래서 문구에서
# 'text' 라는 단어 자체를 빼고, 글자 금지를 명시적으로 넣는다.
STYLE = (
    " Painterly biblical illustration style, warm golden tones, cinematic wide "
    "establishing shot, atmospheric depth, reverent and contemplative mood. "
    "Absolutely no people, no human figures, no faces, no crowds — the scene is "
    "empty of persons. No lettering, no words, no writing, no captions, no subtitles, "
    "no watermark, no signature anywhere in the image. Calm uncluttered lower foreground."
)

# 예외 — 이 씬들은 글자가 *의도된* 소재다(요셉 5.jpg 의 히브리 문자 광원과 같은 계열).
# STYLE 의 글자 금지가 이들에게는 적용되면 안 된다.
LETTERS_ALLOWED = {("solomon", "2")}

SCENES: dict[str, dict[str, str]] = {
    "moses": {
        "1": "Vast empty Midian wilderness at dusk, scattered weathered boulders, a shepherd's "
             "wooden staff leaning alone against a rock, tiny distant sheep silhouettes, very long shadows, silence.",
        "2": "A solitary thornbush burning with brilliant golden flame on a barren rocky mountainside, "
             "the bush unconsumed, a pair of worn sandals left on the stony ground before it, dawn sky.",
        "3": "Five weathered clay tablets laid in a row on rippled desert sand, faint carved marks worn "
             "almost smooth, low raking sunlight casting five long parallel shadows.",
        "4": # 이집트 신전 기둥은 "장식하지 말라" 고 부정문을 써도 계속 인물 벽화가 붙어 나왔다
             # (세 번 실패). 이집트 벽화는 정의상 사람 그림이라 모델의 사전확률을 못 이긴다.
             # 그래서 기둥을 *역광 실루엣*으로 돌린다 — 표면이 안 보이면 표면에 그릴 것도 없다.
             "Interior of an immense dark granite hall, colossal columns reduced to pure black "
             "silhouettes with no visible surface detail or carving, seen against a blinding "
             "doorway of daylight at the far end. Polished floor mirroring the glare, an empty "
             "throne dais lost in the light, deep shadow, extreme backlit contrast.",
        "5": "Two enormous walls of dark seawater standing parted, a dry seabed corridor of wet sand running "
             "between them toward the horizon, turbulent dramatic sky, spray suspended in the air.",
        "6": "Wilderness at first dawn, gentle warm light spilling over dunes, a single line of footprints "
             "in the sand leading toward distant blue mountains, air clear and still.",
    },
    "david": {
        "1": "Green pasture beside still water at golden hour, a small flock of sheep grazing in the distance, "
             "a simple wooden lyre resting on the grass in the foreground, deep peace.",
        "2": "An Israelite military camp of coarse tents at dusk, cooking fires guttering, armor stacked, "
             "an empty trampled clearing in the foreground, cold blue shadows against warm firelight.",
        "3": "Oversized bronze royal armor and a great plumed helmet propped on a wooden stand inside a dim "
             "campaign tent, obviously far too large, warm oil-lamp light, straps hanging loose.",
        "4": "A clear shallow brook running over smooth pebbles, five rounded stones resting together on the "
             "wet bank in the foreground, sunlight refracting through the moving water.",
        "5": "A wide empty valley floor between two dry ridges, an enormous bronze-tipped spear standing "
             "upright driven into the ground, its shadow stretching impossibly long, dust hanging in the air.",
        "6": "Sunrise flooding the valley with warm gold, a simple leather shepherd's sling lying on a flat "
             "rock in the foreground, mist burning off the hills.",
    },
    "jesus": {
        "1": "Humble stone stable interior at night, a rough wooden manger heaped with straw, a soft radiant "
             "golden glow rising from within the manger illuminating the beams, quiet and holy.",
        "2": "A broad grassy hillside sloping down toward the Sea of Galilee in clear morning light, wildflowers "
             "in the grass, the water calm and bright below, the slope entirely empty.",
        "3": "A narrow ancient village street of pale stone at warm dusk, a discarded wooden walking crutch "
             "leaning abandoned against a sunlit wall, an open doorway beyond, gentle light.",
        "4": "An ancient stone road dividing into three separate paths across a wide open landscape, low warm "
             "sun, each path leading toward a different distant horizon, no signpost.",
        "5": "An ancient olive grove at night, gnarled and twisted olive trunks, cool moonlight filtering "
             "through silver leaves, a worn stone olive press standing silent, still and sorrowful.",
        "6": "An empty rock-hewn tomb at dawn, the great round sealing stone rolled fully aside, brilliant "
             "golden light pouring through the open entrance, folded linen cloths resting on the bare stone shelf.",
        "7": "Radiant sky with layered clouds parting as golden light descends in wide beams, and below a "
             "clear crystal river winding through green living land toward the viewer.",
    },
    "job": {
        "1": "Ash-strewn ground before a ruined homestead under a grey overcast dawn, broken roof beams, four "
             "rough stones set in a small circle as if for sitting, muted and desaturated, utterly quiet.",
        "2": "A vast night sky over barren cracked land, one great dark cloud swallowing the stars, a thin "
             "band of dying ember light along the far horizon, oppressive stillness.",
        "3": "Three worn stone seats arranged facing a single lower empty seat on cracked dry ground, long "
             "hard shadows, cold light on the three and a faint warm horizon behind the one.",
        "4": "An immense towering whirlwind rising over a vast desert plain, enormous spiraling cloud structure "
             "filling the sky, brilliant shafts of golden light breaking through its wall, overwhelming scale.",
        "5": "A wide open plain at sunrise after the storm has passed, wet ground mirroring the gold sky, "
             "small green shoots pushing up through cracked earth, calm without triumph.",
    },
    "elijah": {
        "1": "The summit of Mount Carmel at dusk, a blackened stone altar still smoldering with embers and "
             "thin smoke, and beyond it an empty road descending into an immense darkening wilderness.",
        "2": "A single solitary broom tree casting a small patch of shade on empty desert sand under harsh "
             "midday sun, bleached muted warm tones, heat shimmer, profound stillness and exhaustion.",
        "3": "A simple round loaf of bread resting on a hot flat stone beside a clay jar of water, tender "
             "soft dawn light, a folded cloak on the sand nearby, gentle and restorative.",
        "4": "The mouth of a mountain cave on Horeb looking out over a vast valley at dawn, air perfectly "
             "still, delicate soft light, the enormous landscape hushed and calm.",
        "5": "A vast dark landscape at first light with countless small warm lamp-lights scattered far across "
             "the distant hills, each one tiny, together filling the whole valley, quiet hope.",
    },
    "solomon": {
        "1": "An ancient stone altar on a high place at night surrounded by a thousand small offering fires "
             "glowing across the entire hilltop, columns of smoke rising into a vast field of stars.",
        "2": "An ethereal golden dreamscape, soft radiant light diffusing from the centre, abstract drifting "
             "mist, faint ancient Hebrew letters glowing softly and floating, serene and weightless.",
        "3": "A grand judgment hall of polished stone with an empty raised throne, a ceremonial sword resting "
             "flat on a low table before it, hard shafts of light across the floor, tense and silent.",
        "4": "An opulent palace hall crowded with gold vessels, ivory, and heaped treasure, richly decorated "
             "yet completely empty of life, cold dust motes drifting through warm shafts of light, hollow.",
        "5": "A plain open courtyard at dawn stripped of all ornament, a single rolled scroll resting on a "
             "bare stone table, gentle warm light, spare and clear.",
    },
}


def gen(character: str, scene_id: str, prompt: str, force: bool) -> str:
    out_dir = OUT_ROOT / character
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{scene_id}.jpg"
    if out.exists() and not force:
        return f"[{character}/{scene_id}] skip (exists)"

    style = STYLE
    if (character, scene_id) in LETTERS_ALLOWED:
        style = style.replace(
            "No lettering, no words, no writing, no captions, no subtitles, "
            "no watermark, no signature anywhere in the image. ",
            "The only glyphs present are the intended glowing Hebrew letters; "
            "no captions, no subtitles, no watermark, no signature. ",
        )

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:predict?key={API_KEY}"
    body = {
        "instances": [{"prompt": prompt + style}],
        "parameters": {"sampleCount": 1, "aspectRatio": "16:9"},
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = json.loads(r.read())
    except urllib.error.HTTPError as e:
        return f"[{character}/{scene_id}] HTTP {e.code}: {e.read()[:200].decode(errors='replace')}"

    preds = data.get("predictions", [])
    if not preds:
        # 안전필터에 걸리면 predictions 가 통째로 비어서 온다 — 원문을 남겨야 원인을 안다.
        return f"[{character}/{scene_id}] FAIL no predictions: {json.dumps(data)[:200]}"
    b64 = preds[0].get("bytesBase64Encoded")
    if not b64:
        return f"[{character}/{scene_id}] FAIL no bytes: keys={list(preds[0].keys())}"

    img = base64.b64decode(b64)
    out.write_bytes(img)
    return f"[{character}/{scene_id}] saved {len(img)} bytes"


if __name__ == "__main__":
    argv = [a for a in sys.argv[1:] if a != "--force"]
    force = "--force" in sys.argv
    targets = argv or list(SCENES.keys())

    # 인자는 "moses" (인물 전체) 또는 "moses/1" (한 장) 둘 다 받는다.
    # 한 장만 다시 뽑아야 하는 경우가 실제로 생겼다 — Imagen 이 가짜 자막을
    # 그려 넣은 8장만 재생성해야 했고, 인물 단위로 돌리면 멀쩡한 것까지 날아간다.
    work: list[tuple[str, str]] = []
    failures = 0
    for t in targets:
        ch, _, sid = t.partition("/")
        if ch not in SCENES:
            print(f"unknown character: {ch} (have: {', '.join(SCENES)})")
            failures += 1
            continue
        if sid:
            if sid not in SCENES[ch]:
                print(f"unknown scene: {ch}/{sid} (have: {', '.join(SCENES[ch])})")
                failures += 1
                continue
            work.append((ch, sid))
        else:
            work.extend((ch, s) for s in SCENES[ch])

    for ch, sid in work:
        try:
            msg = gen(ch, sid, SCENES[ch][sid], force)
        except Exception as e:  # noqa: BLE001 - 한 장 실패가 전체를 멈추면 안 된다
            msg = f"[{ch}/{sid}] EXCEPTION {type(e).__name__}: {e}"
        if "saved" not in msg and "skip" not in msg:
            failures += 1
        print(msg, flush=True)

    print(f"\ndone. failures={failures}")
    sys.exit(1 if failures else 0)

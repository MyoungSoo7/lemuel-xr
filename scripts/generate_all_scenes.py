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

결과: frontend/public/images/scenes/{character}/{scene_id}.webp

Imagen 은 JPEG 를 돌려준다. 그걸 그대로 두지 않고 **webp 로 바꿔서만 저장한다.**
프론트가 읽는 확장자가 `.webp` 라서(각 인물 page.tsx 의 backgroundImage) jpg 로
떨어뜨리면 그 씬만 배경이 통째로 안 나온다 — 404 인데 CSS 배경이라 콘솔에도
거의 안 남고, 화면은 그냥 검게 보인다. 그래서 변환은 선택이 아니라 필수 단계다.
크기 차이도 장식이 아니다: 실측 1.26MB → 48KB (39장 합계 49MB → 2MB).
jpg 원본은 남기지 않는다. 다시 뽑으려면 이 스크립트를 --force 로 돌린다.

프롬프트 규칙 (기존 5장에서 이어받은 것):
  - **인물을 그리지 않는다.** 프롬프트에 인물명을 넣으면 Imagen 이 얼굴 있는
    사람을 그려버린다(실측). 그래서 전부 *배경·공간·사물* 로만 묘사한다.
  - 공통 스타일 문구(STYLE)를 모든 프롬프트에 동일하게 붙여 톤을 고정한다.
    요셉 5장이 서로 다른 화풍으로 나온 건 이 공통 문구가 없었기 때문이다.
"""
import base64
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

API_KEY = os.environ.get("GEMINI_API_KEY", "")
if not API_KEY:
    sys.exit("ERROR: GEMINI_API_KEY env required")

# 기존 39장은 `imagen-4.0-fast-generate-001` 의 `:predict` 로 뽑았다. 그런데
# 2026-08-22 실측에서 이 모델이 그 키의 v1beta ListModels 에 **더 이상 없다**
# (`404 ... is not found for API version v1beta, or is not supported for predict`).
# 즉 이 스크립트는 --force 로도 기존 39장을 다시 뽑지 못하는 상태였다.
# 남아 있는 이미지 생성 경로는 `gemini-*-image` 계열의 `:generateContent` 뿐이라
# 그쪽을 기본으로 바꾼다. imagen 계열 이름을 넣으면 예전 predict 경로로 돌아간다
# (키에 그 모델이 다시 생기면 쓰라고 남겨둔다).
#
# 산출 해상도가 조금 다르다: predict 는 1408x768, generateContent 는 1376x768.
# 둘 다 16:9 근처이고 화면에서는 `background-size: cover` 로 꽉 채우므로 섞여도
# 보이는 차이가 없다.
MODEL = os.environ.get("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image")
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
    # 룻 5장 — 이 인물만 오래 배경이 **한 장도 없었다.** `/ruth` 는 단색 그라디언트로
    # 돌았고, `scene-backgrounds.test.ts` 는 그 사실을 "의도된 제외" 로 적어 두고 있었다.
    # 그 제외를 걷으려면 먼저 자산이 있어야 하므로 프롬프트를 여기에 올린다.
    #
    # 소재는 지어내지 않았다 — 다섯 칸 전부 `backend/src/main/resources/scenarios/ruth.yml`
    # 의 `extras.anchor` 와 `extras.environment`(time_of_day · camera_rig)에서 옮겼다.
    # 광원은 그 yml 의 시간대를 그대로 따른다: overcast_noon · high_sun · afternoon ·
    # night_lighting_only · late_afternoon.
    #
    # 🚨 Scene 4(타작 마당의 밤)는 이 미션에서 AR 이 닫혀 있는 이유가 된 씬이다
    # (docs/RUTH-RUNTIME-SIGNOFF.md · RuntimeExposureSignoffTest). 배경은 VR 용이고,
    # 프롬프트는 **빈 마당과 곡식 단**까지만 그린다 — 사람도, 잠든 형상도, 두 사람의
    # 자리도 그리지 않는다. 본문이 서술하지 않는 것을 이미지가 서술하게 두지 않는다.
    "ruth": {
        "1": "A dirt track crossing an empty upland plain under flat grey overcast noon light, a single "
             "weathered boundary stone standing where the road forks, dry grass bending in the wind, "
             "the two paths receding toward opposite horizons.",
        "2": "The outskirts of a small stone hill town under high midday sun, an open gateway in a low "
             "rough wall, barley fields just coming into harvest on the slope beyond, two empty woven "
             "baskets set down in the dust of the road.",
        "3": "A wide barley field in mid-harvest in the afternoon, standing grain laid over in waves by the "
             "wind, cut sheaves bound and leaning together in rows, loose ears of grain scattered along "
             "the field edge in the foreground.",
        "4": "A threshing floor at night lit only by moonlight, stacked sheaves of grain heaped around a "
             "swept circle of packed earth, a winnowing fork leaning against the pile, cool blue shadows, "
             "completely still and quiet.",
        "5": "A town gate plaza in the late afternoon, ten worn stone seats arranged in a half circle in "
             "the shade of the gateway arch, a single worn sandal set on the paving before them, long "
             "warm light raking across the stones.",
    },
    # 베드로 — 2026-08-22. 다섯 장의 색온도가 크게 갈린다(등불 실내 → 밤 불빛 →
    # 밤 → 여명 → 아침). 그래서 1·5 의 불은 같은 소재(숯불)로 두되 광원 방향을
    # 반대로 잡는다: 2 는 사람이 둘러선 안쪽을 비추고, 5 는 빈 바닷가를 향해 열려 있다.
    # 그 대비가 이 미션의 축(같은 불 앞에서 부인했고, 같은 불 앞에서 이름이 불렸다)이다.
    "peter": {
        "1": "A stone upper room at night after a meal has ended, a long low table with emptied clay "
             "cups and torn bread left on it, one small oil lamp burning at the table's end, deep warm "
             "shadows gathering in the corners of the room.",
        "2": "A high-walled courtyard of a large stone house at night, a charcoal fire burning low in an "
             "open brazier at the centre of the packed earth, empty worn benches drawn up around it, "
             "cold darkness pressing in beyond the ring of firelight.",
        "3": "The same stone courtyard seen toward its open gateway in the last hour of night, the "
             "charcoal fire reduced to embers behind, the archway standing open onto an empty dark "
             "street, first faint grey showing low in the sky.",
        "4": "The wooden deck of a small fishing boat on a wide still lake in the last dark before dawn, "
             "a heavy wet net heaped and empty along the gunwale, oars shipped, flat grey water "
             "stretching to a low shoreline barely separating from the sky.",
        "5": "A quiet lake shore at sunrise, a charcoal fire already burning on the sand with fish laid "
             "across it and flat loaves of bread set beside, a fishing boat drawn up empty at the "
             "water's edge, low golden light coming in level across the water.",
    },
    # 다니엘 — 2026-08-22. 다섯 칸 전부 `scenarios/daniel.yml` 의 `extras.anchor` 와
    # `environment.time_of_day` 에서 옮겼다: torch_lit_interior · interior_daylight_shafts ·
    # day_cycle_timelapse · formal_court_session · 새벽.
    #
    # 두 가지를 일부러 안 그린다.
    #
    # ① **사자 굴을 그리지 않는다.** Scene 5 의 trigger_warning 이 경고하는 것이
    #    바로 그 좁고 어두운 공간이고, 동의하지 않은 사람은 `daniel_scene5_alt_quiet_window`
    #    로 빠진다. 그런데 배경은 동의 게이트보다 *먼저* 깔린다 — 굴을 그려 두면 건너뛰기를
    #    고른 사람이 건너뛴 그 그림을 이미 본 상태가 된다. 그래서 yml 의 anchor 그대로
    #    예루살렘을 향한 창문과 새벽만 그린다. 축약 경로와 정본 경로가 같은 배경을 쓴다.
    #
    # ② **조서의 글자를 그리지 않는다.** Scene 4 의 소재는 도장이 찍히는 자리인데,
    #    STYLE 이 글자를 금지한다(david/5 의 의미 없는 자막 사고). 봉인은 그리되
    #    양피지 면은 비워 둔다 — 읽히는 순간 그 문구를 저작자가 아니라 Imagen 이 쓴 게 된다.
    #
    # 2·3 은 yml 상 같은 방이다. 그래서 소재로 가른다: 2 는 차려진 왕의 상, 3 은 치우고
    # 남은 채소와 물, 그리고 열흘이 지나간 흔적(빛줄기가 여러 겹으로 겹친다).
    "daniel": {
        "1": "A large open courtyard of a Neo-Babylonian palace in the evening, walls of deep blue "
             "glazed brick banded with plain geometric ochre borders and nothing else, stepped "
             "crenellations along the roofline, low torches burning in bronze brackets, a wide empty "
             "paved floor, long torchlight shadows reaching across the stones. The walls carry no "
             "murals, no figure carvings, no reliefs of any creature, and no inscribed marks.",
        "2": "Interior of a Neo-Babylonian palace dining hall by day, plain blue glazed brick walls "
             "with simple geometric banding and no decoration of any other kind, square cedar roof "
             "beams, a long low table laid with heaped platters of rich royal food and bronze wine "
             "vessels, reed mats set around it, one tall narrow slot window casting a single long "
             "shaft of daylight across the table and floor, every seat empty. No wall paintings, no "
             "hangings, no reliefs, no statues.",
        "3": "The same Neo-Babylonian palace dining hall with the same plain blue glazed brick walls, "
             "same cedar beams and same long low table, but the rich platters cleared away — only a "
             "plain wooden bowl of raw vegetables and a simple clay jar of water left on the bare "
             "table, many overlapping shafts of light and shadow layered across the floor as if ten "
             "days had passed at once. No wall paintings, no hangings, no reliefs, no statues.",
        "4": "A formal Achaemenid Persian throne hall of polished dark stone, tall slender fluted "
             "columns, walls entirely plain and undecorated, an empty raised throne dais at the far "
             "end, a broad unrolled parchment lying on a low table in the foreground with a clay seal "
             "pressed at its edge, the parchment surface entirely blank, cold ceremonial light. "
             "No carved figures, no reliefs of people or animals, no statues, no wall panels of any "
             "figurative kind.",
        "5": "A quiet stone upper room at first dawn, one open window facing out toward distant hills, "
             "a worn woven mat laid on the floor before it, soft grey-gold light just beginning to "
             "spill over the sill, the room still and completely empty.",
    },
}


# webp 인코딩 설정.
#
# q=82 는 39장 실측에서 SSIM 0.967~0.986 (최악이 david/1) 이었다. 전면 배경 위에
# 텍스트를 얹는 용도라 이 정도 손실은 육안으로 구분되지 않는다 — 풀 텍스처가 아주
# 약간 뭉개지는 것이 전부고, 구도·광원·피사체 디테일은 그대로다.
# m=6 은 가장 느린(=가장 잘 줄이는) 탐색이다. 39장에 수십 초라 아낄 이유가 없다.
WEBP_QUALITY = "82"
WEBP_METHOD = "6"


def to_webp(jpeg_bytes: bytes, out: Path, tag: str) -> str:
    """Imagen 이 준 JPEG 를 webp 로 인코딩해 out 에 쓴다. jpg 는 남기지 않는다."""
    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
        tmp.write(jpeg_bytes)
        tmp_path = Path(tmp.name)
    try:
        proc = subprocess.run(
            ["cwebp", "-quiet", "-q", WEBP_QUALITY, "-m", WEBP_METHOD,
             str(tmp_path), "-o", str(out)],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            # 여기서 조용히 넘어가면 씬 배경이 없는 채로 배포된다. 실패는 실패로 남긴다.
            return f"[{tag}] FAIL cwebp rc={proc.returncode}: {proc.stderr.strip()[:200]}"
    finally:
        tmp_path.unlink(missing_ok=True)
    return f"[{tag}] saved {out.stat().st_size} bytes (jpeg {len(jpeg_bytes)})"


def gen(character: str, scene_id: str, prompt: str, force: bool) -> str:
    out_dir = OUT_ROOT / character
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{scene_id}.webp"
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

    predict = MODEL.startswith("imagen")
    verb = "predict" if predict else "generateContent"
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:{verb}?key={API_KEY}"
    if predict:
        body = {
            "instances": [{"prompt": prompt + style}],
            "parameters": {"sampleCount": 1, "aspectRatio": "16:9"},
        }
    else:
        body = {
            "contents": [{"parts": [{"text": prompt + style}]}],
            "generationConfig": {
                "responseModalities": ["IMAGE"],
                "imageConfig": {"aspectRatio": "16:9"},
            },
        }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        # generateContent 쪽이 predict 보다 눈에 띄게 느리다 (실측 한 장 40~90초).
        with urllib.request.urlopen(req, timeout=300) as r:
            data = json.loads(r.read())
    except urllib.error.HTTPError as e:
        return f"[{character}/{scene_id}] HTTP {e.code}: {e.read()[:200].decode(errors='replace')}"

    if predict:
        preds = data.get("predictions", [])
        if not preds:
            # 안전필터에 걸리면 predictions 가 통째로 비어서 온다 — 원문을 남겨야 원인을 안다.
            return f"[{character}/{scene_id}] FAIL no predictions: {json.dumps(data)[:200]}"
        b64 = preds[0].get("bytesBase64Encoded")
        if not b64:
            return f"[{character}/{scene_id}] FAIL no bytes: keys={list(preds[0].keys())}"
    else:
        # generateContent 는 파트 배열로 온다. 안전필터에 걸리면 이미지 파트 없이
        # 텍스트 파트만(또는 finishReason 만) 온다 — predict 와 마찬가지로 원문을 남긴다.
        b64 = ""
        for cand in data.get("candidates", []):
            for part in cand.get("content", {}).get("parts", []):
                if "inlineData" in part:
                    b64 = part["inlineData"]["data"]
                    break
            if b64:
                break
        if not b64:
            return f"[{character}/{scene_id}] FAIL no image part: {json.dumps(data)[:300]}"

    img = base64.b64decode(b64)
    return to_webp(img, out, f"{character}/{scene_id}")


if __name__ == "__main__":
    # 저장 단계가 cwebp 에 의존한다. 39장을 다 뽑고 나서야 인코더가 없다는 걸 알면
    # Imagen 호출을 통째로 날린다 — 그래서 첫 호출 전에 막는다.
    if shutil.which("cwebp") is None:
        sys.exit("ERROR: cwebp 필요 (macOS: brew install webp / Debian: apt install webp)")

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

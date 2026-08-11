#!/usr/bin/env python3
"""룻(Theme 19) 전용 검사기 — §8-1 의 AC 7·10·11·14·23·24·26·27·28·29·30·31·32 집행기.

설계 규칙은 `scripts/newchar_gates.py:20` 을 그대로 따른다: **grep 금지, 전부 python.**
macOS BSD grep 에 `-P` 가 없고 이 개발 셸의 `grep` 은 ugrep 래퍼라 대화형과 스크립트 결과가 갈린다.

**정본은 `docs/RUTH-LOCKED-STRINGS.md` 다** (2026-08-06 사용자 결정 "상류 문서를 실제로 만들어").
이 파일에는 자막·면책·카드 문자열의 사본이 **없다** — 전부 그 문서의 데이터 블록에서 읽는다.
정본을 못 읽으면 판정을 내지 않고 rc 126 으로 죽는다(`_load_canon`). 조용한 초록으로 새지 않는다.

배경: 원래 이 파일은 값을 하드코딩해 두고 상류가 `SEED-RUTH.md` 라고 적었는데, 그 파일은
저장소에 존재한 적이 없었다(작업 트리·git 전체 이력 모두). 이 파일이 스스로 경고하던
**자기참조 검사**가 실제 상태였던 것이다.

여전히 못 잡는 것: 정본 문서와 `content/ruth/*.yml` 을 *같이* 고치면 이 검사기는 통과한다.
제3자가 없으면 원리적으로 그렇다. 달라진 점은 그 수정이 정본 파일의 diff 로 남아
검토 가능해졌다는 것이다. 성구 자구(AC 30·14)만은 예외로 진짜 제3자 대조다 —
`docs/VERSES-RUTH-GAE.md` / `docs/verses-ruth.txt` 를 읽는다.

rc 규약(`scripts/gates/tests/run.sh` 와 동일): 0=PASS · 1=FAIL · ≥126 은 판정이 아니라 실행 실패.

사용:
    python3 scripts/check_ruth_captions.py                  # AC 14 (자막 자구 대조)
    python3 scripts/check_ruth_captions.py --closing        # AC 7 + AC 10
    python3 scripts/check_ruth_captions.py --all            # 전건
"""

from __future__ import annotations

import argparse
import os
import re
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from newchar_gates import collect_key, yaml_values  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(REPO, "content", "ruth")
SCENES = [os.path.join(CONTENT, f"scene{i}.yml") for i in range(1, 6)]
GAE = os.path.join(REPO, "docs", "VERSES-RUTH-GAE.md")
VERSES = os.path.join(REPO, "docs", "verses-ruth.txt")
GATE_CFG = os.path.join(REPO, "scripts", "gates", "ruth.yml")

# ── 정본 적재 (docs/RUTH-LOCKED-STRINGS.md) ───────────────────────────────

CANON_DOC = os.path.join(REPO, "docs", "RUTH-LOCKED-STRINGS.md")

# 정본에 반드시 있어야 하는 최상위 키. 문서가 조용히 줄어들면 KeyError 로 죽는 대신
# 여기서 먼저 잡는다 — 없는 키를 검사에서 빼먹으면 그 AC 는 재지 않으면서 초록이 된다.
CANON_KEYS = (
    "closing_caption", "closing_lines", "disclaimer", "ruth_2_11",
    "crisis_latch", "consent_cards", "skip_destinations",
    "f66_entry_gate", "exclusions",
)


def _die(msg: str) -> "None":
    """판정이 아니라 실행 실패다 — rc 126 (rc 규약은 docstring 참조)."""
    print(f"EXEC FAIL  {msg}", file=sys.stderr)
    raise SystemExit(126)


def _load_canon() -> dict:
    """정본 md 의 첫 ```yaml 블록을 읽는다. 실패는 전부 rc 126 이다."""
    if not os.path.exists(CANON_DOC):
        _die(f"정본 문서가 없다: {CANON_DOC}")
    with open(CANON_DOC, encoding="utf-8") as fh:
        text = fh.read()
    m = re.search(r"^```yaml\n(.*?)^```", text, re.DOTALL | re.MULTILINE)
    if not m:
        _die(f"정본 문서에 ```yaml 데이터 블록이 없다: {CANON_DOC}")
    try:
        data = yaml.safe_load(m.group(1))
    except yaml.YAMLError as e:
        _die(f"정본 데이터 블록 파싱 실패: {e}")
    if not isinstance(data, dict):
        _die("정본 데이터 블록이 매핑이 아니다")
    missing = [k for k in CANON_KEYS if k not in data]
    if missing:
        _die(f"정본에 필수 키가 없다: {missing}")
    return data


CANON = _load_canon()

# 아래는 *사본이 아니라 별칭* 이다 — 값은 전부 CANON 에서 온다.

CLOSING_CAPTION = CANON["closing_caption"]["text_ko"]
CLOSING_VERSE_REF = CANON["closing_caption"]["verse_ref"]

# `belonging_label` → 문자열. 정본의 `default` 키가 라벨 없음(None)에 대응한다.
# faith_tone 은 적용되지 않는다(SR-9).
CLOSING_LINES = {
    "stay_beside": CANON["closing_lines"]["stay_beside"],
    "step_back": CANON["closing_lines"]["step_back"],
    None: CANON["closing_lines"]["default"],
}

# 고난 맥락 면책 (F-1). 셋째 줄이 F-6.5(피해 상황 부인)를 집행한다.
DISCLAIMER = CANON["disclaimer"]["text_ko"]
DISCLAIMER_POSITION = CANON["disclaimer"]["position"]
DISCLAIMER_STYLE = CANON["disclaimer"]["style"]

# 룻 2:11 축약 정본 (LOCKED · X1) — 전문은 어느 경로에서도 렌더하지 않는다
RUTH_2_11_SHORT = CANON["ruth_2_11"]["short_text_ko"]
RUTH_2_11_FORBIDDEN_FRAGMENT = CANON["ruth_2_11"]["forbidden_fragment"]

# AC 23 — 위기 카드 latch 3키의 값. 키 존재가 아니라 값을 잰다.
LATCH = dict(CANON["crisis_latch"])

# AC 24 동의 카드 3장 (LOCKED — designer 가 재작성하지 않는다)
CONSENT_CARDS = {
    cid: {"covers": v["covers"], "declined_route": v["declined_route"], "text": v["text_ko"]}
    for cid, v in CANON["consent_cards"].items()
}

# 상시 이탈(SR-5)이 쓰는 Scene 자체의 스킵 키.
#
# ⚠️ 3항목 판(`{1: 3, 3: 4, 5: ...}`)은 **G7 과 동시에 만족할 수 없다.**
#    g7(`newchar_gates.py:1290-1304`)은 `trigger_scenes` 의 **모든** Scene 파일에
#    `skip_alternative_scene_id` 리터럴 키를 요구하고 룻의 `trigger_scenes` 는 `[1,2,3,4,5]` 다 —
#    Scene 2·4 가 키를 가지면 3항목 판의 `==` 가 깨지고, 안 가지면 G7 이 FAIL 한다.
#    그래서 5행 표가 정본이다. — rev.6.5 §16-m
#    정본 문서의 키는 YAML 문자열이라 여기서 int 로 되돌린다.
SKIP_DESTINATIONS = {int(k): v for k, v in CANON["skip_destinations"].items()}

# AC 29 — F66 진입 상태 게이트. 항목 수 2 가 요점(재채점 U1)
F66_ID = CANON["f66_entry_gate"]["id"]
F66_CONDITIONS = CANON["f66_entry_gate"]["trigger_conditions"]
F66_ON_TRIGGER = CANON["f66_entry_gate"]["on_trigger"]

# AC 34 — 계보 자막 (2026-08-12 신설)
GENEALOGY = dict(CANON["genealogy_caption"])

# AC 31 — 배제 선언 항목 수와 scope 구성
EXCLUSION_TOTAL = CANON["exclusions"]["total"]
EXCLUSION_SCOPES = dict(CANON["exclusions"]["by_scope"])

FOOTNOTE = re.compile(r"\d+\)")


# ── 공통 ───────────────────────────────────────────────────────────────────


def load(path: str):
    with open(path, encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def norm(s: str) -> str:
    """블록 스칼라(`|`)가 남기는 꼬리 개행만 없앤다. 내부 자구는 건드리지 않는다."""
    return str(s).rstrip("\n")


def captions_of(doc) -> list[dict]:
    out = []
    for group in collect_key(doc, "captions"):
        for c in group or []:
            if isinstance(c, dict):
                out.append(c)
    return out


def gae_verses() -> dict[str, str]:
    verses: dict[str, str] = {}
    with open(GAE, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"^(\d+:\d+)\s+(.+)$", line.strip())
            if m:
                verses[m.group(1)] = FOOTNOTE.sub("", m.group(2)).strip()
    return verses


# ── AC 7 · 10 — 종결 자막과 마감 세 줄 ────────────────────────────────────


def check_closing() -> list[str]:
    bad: list[str] = []
    doc = load(SCENES[4])

    cs = doc.get("closing_screen")
    if not isinstance(cs, dict):
        return ["AC 7: scene5.yml 최상위에 `closing_screen:` 블록이 없다"]

    cap = cs.get("closing_caption")
    if not isinstance(cap, dict):
        bad.append("AC 7: `closing_screen.closing_caption` 이 없다")
    else:
        if norm(cap.get("text_ko", "")) != CLOSING_CAPTION:
            bad.append(f"AC 7: 종결 자막 불일치 — {cap.get('text_ko')!r} != {CLOSING_CAPTION!r}")
        if cap.get("verse_ref") != CLOSING_VERSE_REF:
            bad.append(f"AC 7: 절 표기 불일치 — {cap.get('verse_ref')!r} != {CLOSING_VERSE_REF!r}")

    # AC 10 — 집합 크기가 요점이다. 3줄이 그대로 있는 채 6줄이 더 생기는 형태(P1·SR-9)를 잡는다.
    lines = {norm(v) for v in collect_key(doc, "closing_line_ko") if v is not None}
    if lines != set(CLOSING_LINES.values()):
        bad.append(
            f"AC 10: 마감 줄 집합 불일치 — 초과 {sorted(lines - set(CLOSING_LINES.values()))} "
            f"/ 누락 {sorted(set(CLOSING_LINES.values()) - lines)}"
        )
    if len(lines) != 3:
        bad.append(f"AC 10: 마감 줄 집합 크기 {len(lines)} != 3")

    # 라벨↔줄 결합까지 잰다 — 집합만 맞고 배정이 뒤바뀌면 평가 문장이 된다.
    for group in collect_key(doc, "routes"):
        for r in group or []:
            if not isinstance(r, dict) or "closing_line_ko" not in r:
                continue
            label = (r.get("when") or {}).get("belonging_label")
            want = CLOSING_LINES.get(label, "<라벨 미정의>")
            if norm(r["closing_line_ko"]) != want:
                bad.append(f"AC 10: 라벨 {label!r} 의 마감 줄 불일치 — {r['closing_line_ko']!r} != {want!r}")

    if any(collect_key(doc, "faith_tone_applied")):
        bad.append("AC 10: 마감 줄에 `faith_tone` 이 적용됐다 (SR-9 위반)")
    return bad


# ── AC 11 — 면책 정본·층위 ────────────────────────────────────────────────


def check_disclaimer() -> list[str]:
    bad: list[str] = []
    for i, path in enumerate(SCENES, start=1):
        doc = load(path)
        env = doc.get("environment")
        # 부모 경로를 `environment.` 까지 못 박는다 (W4) — 최상위만 보는 검사기는
        # 하우스 파일 전건에서 빈 결과를 내고, 빈 결과는 FAIL 이 아니라 조용한 PASS 다.
        overlays = (env or {}).get("ui_overlays") or []
        hits = [o for o in overlays if isinstance(o, dict) and str(o.get("id", "")).endswith("_suffering_disclaimer")]
        if not hits:
            bad.append(f"AC 11: Scene {i} 의 `environment.ui_overlays[]` 에 `*_suffering_disclaimer` 가 없다")
            continue
        for o in hits:
            if norm(o.get("text_ko", "")) != DISCLAIMER:
                bad.append(f"AC 11: Scene {i} 면책 문안 불일치 ({o.get('id')})")
            if o.get("position") != DISCLAIMER_POSITION:
                bad.append(f"AC 11: Scene {i} position {o.get('position')!r} != {DISCLAIMER_POSITION!r}")
            if o.get("style") != DISCLAIMER_STYLE:
                bad.append(f"AC 11: Scene {i} style {o.get('style')!r} != {DISCLAIMER_STYLE!r}")
    return bad


# ── AC 23 — 위기 카드 latch 3키 값 ────────────────────────────────────────


def check_latch() -> list[str]:
    bad: list[str] = []
    for i, path in enumerate(SCENES, start=1):
        doc = load(path)
        for key, want in LATCH.items():
            got = collect_key(doc, key)
            if not got:
                bad.append(f"AC 23: Scene {i} 에 `{key}` 없음")
            elif any(v != want for v in got):
                bad.append(f"AC 23: Scene {i} `{key}` = {got!r} != {want!r}")
    return bad


# ── AC 24 — 동의 카드 3장 정본·범위 ───────────────────────────────────────


def _all_cards() -> dict[str, dict]:
    cards: dict[str, dict] = {}
    for path in SCENES:
        doc = load(path)
        for group in collect_key(doc, "consent_cards"):
            for c in group or []:
                if isinstance(c, dict) and c.get("consent_card_id"):
                    cards[c["consent_card_id"]] = c
    return cards


def check_consent() -> list[str]:
    bad: list[str] = []
    cards = _all_cards()
    missing = set(CONSENT_CARDS) - set(cards)
    if missing:
        bad.append(f"AC 24: 동의 카드 누락 {sorted(missing)}")
    extra = set(cards) - set(CONSENT_CARDS)
    if extra:
        bad.append(f"AC 24: 정본에 없는 동의 카드 {sorted(extra)}")
    for cid, want in CONSENT_CARDS.items():
        got = cards.get(cid)
        if not got:
            continue
        if norm(got.get("consent_card_ko", "")) != want["text"]:
            bad.append(f"AC 24: {cid} 전문 불일치")
        if got.get("consent_covers_scenes") != want["covers"]:
            bad.append(f"AC 24: {cid} covers {got.get('consent_covers_scenes')!r} != {want['covers']!r}")
    return bad


# ── AC 26 — 스킵 목적지 타입 ──────────────────────────────────────────────


def check_skip_types() -> list[str]:
    """정수 자기참조가 이 검사를 통과하면서 축약 블록을 도달 불가로 만든 것이 X1 이다.
    값 종류별로 다르게 잰다 — int 는 Scene 파일 존재, str 은 같은 파일의 블록 id."""
    bad: list[str] = []
    found: dict[int, object] = {}
    for i, path in enumerate(SCENES, start=1):
        doc = load(path)
        vals = collect_key(doc, "skip_alternative_scene_id")
        if not vals:
            continue
        if len(set(map(str, vals))) > 1:
            bad.append(f"AC 26: Scene {i} 에 `skip_alternative_scene_id` 가 서로 다른 값으로 {vals!r}")
        v = vals[0]
        found[i] = v
        if isinstance(v, bool):
            bad.append(f"AC 26: Scene {i} 목적지가 bool 이다 — {v!r}")
        elif isinstance(v, int):
            if not os.path.exists(os.path.join(CONTENT, f"scene{v}.yml")):
                bad.append(f"AC 26: Scene {i} → scene{v}.yml 이 없다")
        elif isinstance(v, str):
            block_ids = {b.get("id") for g in collect_key(doc, "conditional_blocks") for b in (g or []) if isinstance(b, dict)}
            if v not in block_ids:
                bad.append(f"AC 26: Scene {i} 목적지 {v!r} 가 같은 파일의 `conditional_blocks[].id` {sorted(x for x in block_ids if x)} 에 없다")
            if v == doc.get("id") or v == f"ruth_scene{i}":
                bad.append(f"AC 26: Scene {i} 목적지가 자기 자신이다 — {v!r}")
        else:
            bad.append(f"AC 26: Scene {i} 목적지 타입 불명 {type(v).__name__}")
    if found != SKIP_DESTINATIONS:
        bad.append(f"AC 26: 목적지 표 불일치 — 실측 {found!r} != 기대 {SKIP_DESTINATIONS!r}")
    return bad


# ── AC 27 — 거절 목적지 ───────────────────────────────────────────────────


def check_declined() -> list[str]:
    bad: list[str] = []
    cards = _all_cards()
    got = {cid: c.get("consent_declined_route") for cid, c in cards.items()}
    want = {cid: v["declined_route"] for cid, v in CONSENT_CARDS.items()}
    if got != want:
        bad.append(f"AC 27: 거절 목적지 불일치 — 실측 {got!r} != 기대 {want!r}")
    for cid, v in got.items():
        if isinstance(v, int) and not isinstance(v, bool):
            if not os.path.exists(os.path.join(CONTENT, f"scene{v}.yml")):
                bad.append(f"AC 27: {cid} 거절 목적지 scene{v}.yml 이 없다")
    sentinels = [v for v in got.values() if v == "closing"]
    if len(sentinels) != 1:
        bad.append(f"AC 27: `closing` 센티널 {len(sentinels)}건 != 1 (2건 이상이면 종결 화면이 갈라진 것이다)")
    return bad


# ── AC 28 — 짧은 경로 자막 부재 ───────────────────────────────────────────


def check_short_path() -> list[str]:
    """추출 대상은 scene3.yml 전체다 — 조건 분기도 도달성도 따지지 않는 단일 부재 검사."""
    bad: list[str] = []
    doc = load(SCENES[2])
    for v in yaml_values(doc):
        if RUTH_2_11_FORBIDDEN_FRAGMENT in v:
            bad.append(f"AC 28: scene3.yml 에 전문 조각이 살아 있다 — {v[:60]!r}")
    caps = [c for c in captions_of(doc) if "2:11" in str(c.get("verse_ref", ""))]
    if not caps:
        bad.append("AC 28: scene3.yml 에 룻 2:11 자막이 없다")
    for c in caps:
        if norm(c.get("text_ko", "")) != RUTH_2_11_SHORT:
            bad.append(f"AC 28: 2:11 자막이 축약 정본과 다르다 — {c.get('text_ko')!r}")
    # content/ruth 전체에 전문 에셋 참조가 0건
    for name in sorted(os.listdir(CONTENT)):
        if not name.endswith(".yml"):
            continue
        for v in yaml_values(load(os.path.join(CONTENT, name))):
            if "2_11_full" in v or "ruth_scene3_alt_short" in v:
                bad.append(f"AC 28: {name} 에 전문 경로 잔존 참조 — {v!r}")
    return bad


# ── AC 29 — F66 진입 게이트 ───────────────────────────────────────────────


def check_f66() -> list[str]:
    bad: list[str] = []
    doc = load(SCENES[0])
    gates = doc.get("safety_gates") or []
    hit = [g for g in gates if isinstance(g, dict) and g.get("id") == F66_ID]
    if not hit:
        return [f"AC 29: scene1.yml 최상위 `safety_gates[]` 에 `{F66_ID}` 가 없다"]
    g = hit[0]
    conds = g.get("trigger_conditions") or []
    # 항목 수를 재는 것이 요점이다 — 두 번째 항목(F-6.2)이 빠지는 것이 rev.5 의 오독 방향이다.
    if len(conds) != 2:
        bad.append(f"AC 29: `trigger_conditions` 항목 수 {len(conds)} != 2 (F-6.2 축 누락)")
    for i, want in enumerate(F66_CONDITIONS):
        if i >= len(conds):
            break
        for k, v in want.items():
            if str((conds[i] or {}).get(k, "")).strip() != v:
                bad.append(f"AC 29: trigger_conditions[{i}].{k} 불일치 — {(conds[i] or {}).get(k)!r} != {v!r}")
    on = g.get("on_trigger") or {}
    for k, v in F66_ON_TRIGGER.items():
        if k not in on:
            bad.append(f"AC 29: `on_trigger.{k}` 없음")
        elif on[k] != v:
            bad.append(f"AC 29: `on_trigger.{k}` = {on[k]!r} != {v!r}")
    return bad


# ── AC 30 — verses-ruth.txt 자구 전수 ─────────────────────────────────────


def _verses_rows() -> list[tuple[int, str, str]]:
    rows = []
    with open(VERSES, encoding="utf-8") as fh:
        for n, line in enumerate(fh, 1):
            if "\t" not in line:
                continue
            ref, text = line.split("\t", 1)
            rows.append((n, ref.strip(), text.strip()))
    return rows


def check_verses_fidelity() -> list[str]:
    bad: list[str] = []
    gae = gae_verses()
    for n, ref, text in _verses_rows():
        key = ref.replace("룻", "").strip()
        key = re.sub(r"[a-z]$", "", key)  # 분할 접미사 a/b 제거
        src = gae.get(key)
        if src is None:
            bad.append(f"AC 30: {VERSES}:{n} 참조 {ref!r} 가 GAE 에 없다")
        elif FOOTNOTE.sub("", text).strip() not in src:
            bad.append(f"AC 30: {VERSES}:{n} {ref} 본문이 GAE 의 연속 부분문자열이 아니다")
    return bad


# ── AC 31 — 배제 선언 항목 수 ─────────────────────────────────────────────


def check_exclusion_count() -> list[str]:
    """수를 재는 것이 요점이다 (W2) — 빠진 배제는 g0b·g0e 가 못 잡는다.
    선언되지 않은 값은 어떤 게이트의 대상도 아니기 때문이다."""
    bad: list[str] = []
    cfg = load(GATE_CFG)
    ex = cfg.get("exclusions") or []
    if len(ex) != EXCLUSION_TOTAL:
        bad.append(f"AC 31: `exclusions` 항목 수 {len(ex)} != {EXCLUSION_TOTAL}")
    by: dict[str, int] = {}
    for e in ex:
        by[str((e or {}).get("scope"))] = by.get(str((e or {}).get("scope")), 0) + 1
    if by != EXCLUSION_SCOPES:
        bad.append(f"AC 31: scope 구성 불일치 — 실측 {by!r} != 기대 {EXCLUSION_SCOPES!r}")
    for e in ex:
        if (e or {}).get("scope") == "content_leaf" and (e or {}).get("expect") == "excluded_only":
            bad.append(f"AC 31: `content_leaf` + `excluded_only` 는 명시적 설정 오류다 — {e.get('value')!r}")
    return bad


# ── AC 32 — closing 화면 안전층 ───────────────────────────────────────────


def check_closing_safety() -> list[str]:
    bad: list[str] = []
    doc = load(SCENES[4])
    cs = doc.get("closing_screen")
    if not isinstance(cs, dict):
        return ["AC 32: scene5.yml 최상위에 `closing_screen:` 블록이 없다"]

    # ① 안전층. 부모가 `environment` 가 아니다 — jacob 은 `closing_only` 진입에서
    #    `environment` 를 렌더하지 않는다(`content/jacob/scene5.yml:172-173`).
    overlays = cs.get("ui_overlays") or []
    hits = [o for o in overlays if isinstance(o, dict) and str(o.get("id", "")).endswith("_suffering_disclaimer")]
    if not hits:
        bad.append("AC 32: `closing_screen.ui_overlays[]` 에 면책이 없다")
    for o in hits:
        if norm(o.get("text_ko", "")) != DISCLAIMER:
            bad.append("AC 32: closing 면책 문안이 정본과 다르다")
        if o.get("position") != DISCLAIMER_POSITION:
            bad.append(f"AC 32: closing position {o.get('position')!r} != {DISCLAIMER_POSITION!r}")
        if o.get("style") != DISCLAIMER_STYLE:
            bad.append(f"AC 32: closing style {o.get('style')!r} != {DISCLAIMER_STYLE!r}")
    if not cs.get("crisis_reminder"):
        bad.append("AC 32: `closing_screen.crisis_reminder` 없음")

    # ② 부착 지점을 블록 경로로 센다. Scene 5 를 두 번 세지 않는다 (X2).
    paths = set()
    for i, path in enumerate(SCENES, start=1):
        d = load(path)
        ov = (d.get("environment") or {}).get("ui_overlays") or []
        if any(isinstance(o, dict) and str(o.get("id", "")).endswith("_suffering_disclaimer") for o in ov):
            paths.add(f"scene{i}.environment.ui_overlays")
    if hits:
        paths.add("scene5.closing_screen.ui_overlays")
    if len(paths) != 6:
        bad.append(f"AC 32: 부착 블록 경로 {len(paths)}종 != 6 — {sorted(paths)}")

    # ③ 센티널 → 블록 결합. 컨테이너는 있는데 결합이 미측정인 상태가 X1 의 형태였다.
    if cs.get("entry_mode") != "closing_only":
        bad.append(f"AC 32: `closing_screen.entry_mode` = {cs.get('entry_mode')!r} != 'closing_only'")
    routes = [c.get("consent_declined_route") for c in _all_cards().values()]
    if routes.count("closing") != 1:
        bad.append(f"AC 32: `closing` 센티널 {routes.count('closing')}건 != 1")
    return bad


# ── AC 14 — 자막 자구·표기 대조 (기본 모드) ──────────────────────────────


def check_caption_fidelity() -> list[str]:
    """`중` 표기는 부분문자열, `중` 없는 표기는 완전일치. SR-0 규칙의 집행 수단."""
    bad: list[str] = []
    gae = gae_verses()
    for i, path in enumerate(SCENES, start=1):
        doc = load(path)
        caps = captions_of(doc)
        cs = doc.get("closing_screen")
        if isinstance(cs, dict) and isinstance(cs.get("closing_caption"), dict):
            caps.append(cs["closing_caption"])
        for c in caps:
            ref = str(c.get("verse_ref", "")).strip()
            text = norm(c.get("text_ko", ""))
            m = re.search(r"(\d+:\d+)", ref)
            if not m:
                bad.append(f"AC 14: Scene {i} 자막 {c.get('id')!r} 에 절 표기가 없다 — {ref!r}")
                continue
            src = gae.get(m.group(1))
            if src is None:
                bad.append(f"AC 14: Scene {i} {ref} 가 GAE 에 없다")
            elif ref.endswith("중"):
                if text not in src:
                    bad.append(f"AC 14: Scene {i} {ref} 자구 불일치(부분문자열 아님) — {text[:40]!r}")
            elif text != src:
                bad.append(f"AC 14: Scene {i} {ref} 자구 불일치(완전일치 아님) — {text[:40]!r}")
    return bad


# ── AC 33 — 오벳 출생 미렌더 ──────────────────────────────────────────────
#
# 이 미션에서 안전상 가장 무겁게 걸린 배제인데 **집행이 없었다.**
#
# 등재는 돼 있다 — 절 본문은 `docs/MVP-RUTH.md` §4-2 E7(4:13)·E8(4:14) 로 마커까지
# 붙어 있고, 렌더 자체는 §14「채택하지 않은 설계」에 기각 사유와 함께 적혀 있다.
# 그런데 §14 는 산문 기록표라 마커가 없어 세어지지 않고, 저작 파일이 선언한
# `baby_asset_present` / `birth_scene_rendered` 두 키를 **읽는 코드가 리포 전체에
# 0건**이었다(2026-08-11 실측). 이 파일이 다른 곳에 적어 둔 문장이 그대로 적용된다:
# 재는 쪽이 없으면 복원해도 모든 검사가 초록이다.
#
# ⚠️ 이 검사만 이 파일의 기존 스코프(`content/ruth` 저작 파일)를 **넘는다** — 런타임
#    `backend/src/main/resources/scenarios/ruth.yml` 까지 본다. 저작과 런타임은 자동
#    동기화되지 않고, 사용자에게 실제로 나가는 것은 런타임 파일이기 때문이다.
#    저작만 재면 런타임에서 되살아나도 초록이다.
#
# 재는 것 / 재지 않는 것:
#   · 잰다 — 두 키의 **값**(존재가 아니라 `False`), 저작 5파일 + 런타임 1파일의
#     에셋 id 와 자막 자구에 출생 어휘가 없는가.
#   · 재지 않는다 — 실제 3D 에셋 번들의 내용물. yml 이 참조하지 않는 에셋이
#     빌드에 들어가는 경로는 이 검사의 사정권 밖이다.

RUNTIME_SCENARIO = os.path.join(
    REPO, "backend", "src", "main", "resources", "scenarios", "ruth.yml"
)

# 선언 2키 — 값까지 계약이다.
OBED_DECL = {"baby_asset_present": False, "birth_scene_rendered": False}

# 출생 어휘. `아이`·`태어나` 는 **넣지 않는다** — 진입 동의 카드가 "한 아이가
# 태어나지만 그 장면은 화면에 나오지 않습니다" 로 미렌더를 *고지*하는 데 쓰고 있고,
# 그 문장은 배제를 지키는 문장이지 어기는 문장이다. 고지를 위반으로 세면 게이트가
# 시키는 수리가 "고지를 지우는 것"이 된다.
OBED_TOKENS = ("오벳", "아기", "젖먹이", "신생아", "출산", "해산", "포대기",
               "baby", "infant", "newborn", "cradle")


def _obed_scan(label: str, doc, bad: list[str]) -> None:
    """에셋 id 와 자막 자구에서 출생 어휘를 찾는다."""
    for key in ("assets_shared", "assets_dedicated"):
        for group in collect_key(doc, key):
            for a in group or []:
                low = str(a).lower()
                for t in OBED_TOKENS:
                    if t.lower() in low:
                        bad.append(f"AC 33: {label} 에셋 id {a!r} 에 출생 어휘 {t!r}")
    for c in captions_of(doc):
        text = norm(c.get("text_ko") or "")
        for t in OBED_TOKENS:
            if t.lower() in text.lower():
                bad.append(
                    f"AC 33: {label} 자막 {c.get('id')!r} 에 출생 어휘 {t!r} — {text[:40]!r}"
                )


def check_obed() -> list[str]:
    bad: list[str] = []

    # ① 선언 2키의 값 — 저작(scene5) 과 런타임 양쪽.
    targets = [("scene5.yml", load(SCENES[4]))]
    if not os.path.exists(RUNTIME_SCENARIO):
        bad.append(f"AC 33: 런타임 파일 부재 — {os.path.relpath(RUNTIME_SCENARIO, REPO)}")
    else:
        targets.append(("scenarios/ruth.yml", load(RUNTIME_SCENARIO)))

    for label, doc in targets:
        for key, want in OBED_DECL.items():
            got = collect_key(doc, key)
            if not got:
                bad.append(f"AC 33: {label} 에 `{key}` 선언이 없다")
            elif any(v is not want for v in got):
                bad.append(f"AC 33: {label} `{key}` = {got!r} != {want!r}")

    # ② 5개 저작 Scene + 런타임 — 에셋 id·자막에 출생 어휘 0건.
    for i, path in enumerate(SCENES, start=1):
        _obed_scan(f"Scene {i}", load(path), bad)
    for label, doc in targets[1:]:
        _obed_scan(label, doc, bad)

    return bad


# ── AC 34 — 계보 자막 존재·자구 ───────────────────────────────────────────
#
# 왜 AC 14 로 부족한가: AC 14 는 **있는** 자막의 자구만 잰다. 자막을 지우면 아무도
# 못 잡는다. 이 리포가 §4-2 에서 스스로 적은 함정과 같은 모양이다 — 재는 쪽이
# 없으면 되돌려도 모든 검사가 초록이다. 그래서 존재를 따로 잰다.
#
# 재는 것 / 재지 않는 것:
#   · 잰다 — 저작(`content/ruth/scene5.yml`) + 런타임(`scenarios/ruth.yml`) 양쪽의
#     존재와 자구, 그리고 정본 자구가 `VERSES-RUTH-GAE.md` 4:17 의 부분문자열인가.
#     마지막 항이 제3자 대조다. 정본 파일만 고쳐 성경에서 멀어지는 경로를 막는다.
#   · 재지 않는다 — 화면에서의 **순서**. 이 자막이 SR-1 앞에 와야 한다는 것은
#     헌장 §3-c 의 요구(마지막은 신의 행위)인데, 순서를 집행하는 코드는 없다.
#     여기서 만들지 않은 이유는 저작 yml 의 재생 순서 표현이 파일마다 다르고
#     그걸 이 검사기가 해석하기 시작하면 계약이 두 곳으로 갈라지기 때문이다.
#     **미해소로 남긴다** — 초록이 순서까지 보증한다고 읽지 말 것.


def check_genealogy() -> list[str]:
    bad: list[str] = []
    want_text = norm(GENEALOGY["text_ko"])
    want_ref = str(GENEALOGY["verse_ref"]).strip()

    # ① 정본 ↔ 성경. `중` 표기이므로 부분문자열이어야 한다.
    src = gae_verses().get("4:17")
    if src is None:
        bad.append("AC 34: GAE 에 4:17 이 없다")
    elif want_text not in src:
        bad.append(f"AC 34: 정본 자구가 GAE 4:17 의 부분문자열이 아니다 — {want_text[:40]!r}")

    # ② 정본 자구 자체가 AC 33 금지 토큰을 담지 않는가. 담기면 두 계약이 충돌한다.
    for t in OBED_TOKENS:
        if t.lower() in want_text.lower():
            bad.append(f"AC 34: 정본 자구에 AC 33 금지 토큰 {t!r} — 두 계약이 충돌한다")

    # ③ 저작 — id 로 찾고 자구·표기를 잰다.
    caps = captions_of(load(SCENES[4]))
    hit = [c for c in caps if str(c.get("id")) == str(GENEALOGY["id"])]
    if not hit:
        bad.append(f"AC 34: scene5.yml 에 계보 자막 {GENEALOGY['id']!r} 이 없다")
    else:
        for c in hit:
            if norm(c.get("text_ko", "")) != want_text:
                bad.append(f"AC 34: scene5.yml 자구 불일치 — {norm(c.get('text_ko',''))[:40]!r}")
            if str(c.get("verse_ref", "")).strip() != want_ref:
                bad.append(f"AC 34: scene5.yml 절 표기 불일치 — {c.get('verse_ref')!r}")

    # ④ 런타임 — 사용자에게 실제로 나가는 파일. 키 이름이 저작과 달라(`ref`) 자구로 찾는다.
    if not os.path.exists(RUNTIME_SCENARIO):
        bad.append(f"AC 34: 런타임 파일 부재 — {os.path.relpath(RUNTIME_SCENARIO, REPO)}")
    else:
        rcaps = captions_of(load(RUNTIME_SCENARIO))
        rhit = [c for c in rcaps if norm(c.get("text_ko", "")) == want_text]
        if not rhit:
            bad.append("AC 34: scenarios/ruth.yml 에 계보 자막이 없다 (저작만 반영된 상태)")
        else:
            for c in rhit:
                ref = str(c.get("ref") or c.get("verse_ref") or "").strip()
                if ref != want_ref:
                    bad.append(f"AC 34: scenarios/ruth.yml 절 표기 불일치 — {ref!r}")

    return bad


# ── 진입 ───────────────────────────────────────────────────────────────────

MODES = [
    ("closing", "AC 7·10 종결 자막·마감 세 줄", check_closing),
    ("disclaimer", "AC 11 면책 정본·층위", check_disclaimer),
    ("latch", "AC 23 위기 카드 latch 3키 값", check_latch),
    ("consent", "AC 24 동의 카드 3장 정본·범위", check_consent),
    ("skip-types", "AC 26 스킵 목적지 타입", check_skip_types),
    ("declined", "AC 27 거절 목적지", check_declined),
    ("short-path", "AC 28 짧은 경로 자막 부재", check_short_path),
    ("f66", "AC 29 F66 진입 게이트", check_f66),
    ("verses-fidelity", "AC 30 verses-ruth.txt 자구 전수", check_verses_fidelity),
    ("exclusion-count", "AC 31 배제 선언 항목 수", check_exclusion_count),
    ("closing-safety", "AC 32 closing 화면 안전층", check_closing_safety),
    ("captions", "AC 14 자막 자구·표기 대조", check_caption_fidelity),
    ("obed", "AC 33 오벳 출생 미렌더 (선언 2키 값 + 에셋·자막 0건)", check_obed),
    ("genealogy", "AC 34 계보 자막 존재·자구 (저작+런타임+성경 대조)", check_genealogy),
]


def main() -> int:
    ap = argparse.ArgumentParser(description="룻 미션 AC 집행기")
    for flag, help_, _ in MODES:
        ap.add_argument(f"--{flag}", action="store_true", help=help_)
    ap.add_argument("--all", action="store_true", help="전건 실행")
    args = ap.parse_args()

    chosen = [m for m in MODES if args.all or getattr(args, m[0].replace("-", "_"))]
    if not chosen:
        chosen = [m for m in MODES if m[0] == "captions"]

    # 출력 형식은 `check_rahab_captions.py` · `newchar_gates.py` 와 같은
    # `[STATUS] 축  설명` + 표준 집계 줄이다. 원래는 `PASS  <label>` / `--- 검사 N / FAIL M ---`
    # 였는데, 그 형식은 `ci_gates.py` 의 TALLY_RE·AXIS_RE 어느 쪽에도 걸리지 않아
    # **이 파일을 CI 러너로 등록할 수 없었다.** 형식이 등록을 막고 있었던 셈이다.
    failed = 0
    for flag, label, fn in chosen:
        problems = fn()
        status = "FAIL   " if problems else "PASS   "
        if problems:
            failed += 1
        print(f"  [{status}] {flag:<16} {label}")
        for p in problems:
            print(f"             - {p}")
    print(f"--- PASS {len(chosen) - failed} / FAIL {failed} / BLOCKED 0 ---")
    print("  ⚠️ 주장 범위: 선언된 정본과 대상 파일이 어긋나지 않았다 까지다.")
    print("     정본과 대상을 *같이* 고치면 이 검사는 통과한다 — 그때 남는 것은 정본의 diff 다.")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # 실행 실패는 판정이 아니다
        print(f"실행 실패: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(126)

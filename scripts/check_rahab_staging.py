#!/usr/bin/env python3
"""라합 — 27 게이트가 보지 않는 층위를 검사한다.

`scripts/newchar_gates.py` 전문에서 `staging_constraints` · `trigger_categories` ·
`exposure_grade` · `attribution_display_ko` 는 **각 0회**다. 곧 seed 가 이 키들로
무엇을 확정하든 게이트는 한 글자도 읽지 않는다. 헌장이 그 결과를 미리 적어 두었다 —
「별도 키가 없으면 검사할 수단이 없다」(`docs/SERIES-GRACE.md:154`) ·
「누락이 통과가 되는 구조다」(`:130`). 이 파일이 그 자리를 맡는다(`SEED-RAHAB.md` §9).

**정본을 읽는다. 사본을 두지 않는다.** 표를 스크립트 안에 베껴 두면 정본이 둘이 되고,
seed 를 고쳤을 때 조용히 갈라진다 — 룻에서 자기참조 검사기를 걷어낸 이유가 그것이다.
그래서 5 Scene 표도, 카드 7행도, 불변식이 지목하는 절 번호("수 6:23")까지도 전부
`docs/SEED-RAHAB.md` 에서 파싱한다. 이 파일이 아는 것은 **표의 위치와 열 이름**뿐이다.

검사 항목 (§9 산출물 표의 (a)~(e) + §7-2 의 코퍼스 실측):

    (a) 각 Scene 의 `staging_constraints`·`user_presence` 와 §5-1 표 일치
    (b) `trigger_categories` ↔ `trigger_scenes` **양방향** 일치 (값 표는 §5-3-a)
    (c) 절 생존 불변식 — 정의된 **단일 카드 경로 7행**에서 불변식 절이 남는가
    (d) `skip_alternative_scene_id` 가 실재하는 `conditional_blocks[].id` 로 해소되는가
    (e) 5 Scene 전건 `exposure_grade` 선언 유무와 값 일치
    (f) 코퍼스 실측 — 한 Scene 을 덮는 동의 카드 수 (§7-2 의 "최대 1" 이 아직 참인가)

**한 검사가 두 줄로 갈리는 이유.** 위 항목 대부분은 대조 대상이 둘이다 — seed 의 표와
`content/rahab/scene*.yml` 이다. 후자는 designer 산출물이라 아직 없다. 없는 쪽을 조용히
건너뛰면 그 검사는 "통과"로 보이므로, **문서 쪽과 yml 쪽을 각각 한 줄로 낸다.**
yml 이 없으면 그 줄은 PASS 가 아니라 `BLOCKED` 다.

**rc 규약**: `0` PASS · `1` FAIL · `2` BLOCKED(판정 불가) · `≥126` 실행 실패.
⚠️ `newchar_gates.py` 는 BLOCKED 에도 `1` 을 돌려준다. 여기서는 `SEED-RAHAB.md` §8-1 이
못 박은 규약을 따라 **BLOCKED 를 `2` 로 구분**한다 — FAIL(고칠 결함이 있음)과
BLOCKED(잴 수단이 없음)를 같은 숫자로 내면 수용기준 표가 둘을 구별할 수 없다.

    python3 scripts/check_rahab_staging.py
    python3 scripts/check_rahab_staging.py --seed docs/SEED-RAHAB.md --content content/rahab
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass, field

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PASS, FAIL, BLOCKED = "PASS", "FAIL", "BLOCKED"

# 절 참조에 쓰는 책 약칭. seed 가 쓰는 것만 둔다.
BOOKS = ("수", "마", "약", "히", "삼하", "눅", "룻")
DEFAULT_BOOK = "수"  # 라합 본문의 기본 책 — "6:23" 처럼 책 없이 적힌 자리에 붙인다


# ──────────────────────────────────────────────────────────────────────────
# 마크다운 파싱 — seed 의 표를 읽는다
# ──────────────────────────────────────────────────────────────────────────

def strip_markup(s: str) -> str:
    """강조·코드·취소선·HTML 태그를 걷어낸다. 표 칸의 값만 남긴다."""
    s = re.sub(r"</?[A-Za-z][^>]*>", "", s)
    s = s.replace("~~", "")
    s = re.sub(r"\*\*|\*|`|__", "", s)
    s = s.replace("​", "")
    return re.sub(r"\s+", " ", s).strip()


@dataclass
class Table:
    headers: list[str]
    rows: list[dict[str, str]]
    line: int  # 표 머리의 1-기준 줄번호. 실패를 좌표로 보고하기 위해 들고 다닌다


def parse_tables(lines: list[str], start: int, end: int) -> list[Table]:
    """[start, end) 구간의 마크다운 표를 전부 뽑는다."""
    out: list[Table] = []
    i = start
    while i < end - 1:
        line = lines[i].strip()
        nxt = lines[i + 1].strip()
        if line.startswith("|") and re.fullmatch(r"\|[\s:|-]+\|", nxt or ""):
            headers = [strip_markup(c) for c in line.strip("|").split("|")]
            rows: list[dict[str, str]] = []
            j = i + 2
            while j < end and lines[j].strip().startswith("|"):
                cells = [strip_markup(c) for c in lines[j].strip().strip("|").split("|")]
                # 열 수가 어긋난 행은 버리지 않고 있는 만큼 채운다 — 조용히 사라지면
                # "표가 짧아진 것"과 "행이 없는 것"을 구별할 수 없다.
                cells += [""] * (len(headers) - len(cells))
                rows.append(dict(zip(headers, cells[: len(headers)])))
                j += 1
            out.append(Table(headers, rows, i + 1))
            i = j
        else:
            i += 1
    return out


def section_bounds(lines: list[str], title_startswith: str) -> tuple[int, int]:
    """제목이 주어진 문자열로 시작하는 절의 [시작, 끝) 줄 인덱스.

    끝은 **같은 레벨 이하의 다음 제목**이다 — 하위 `####` 를 포함해야
    표가 하위 제목 아래 있어도 잡힌다.
    """
    for i, ln in enumerate(lines):
        m = re.match(r"^(#{2,4}) (.*)$", ln)
        if not m:
            continue
        if strip_markup(m.group(2)).startswith(title_startswith):
            level = len(m.group(1))
            for j in range(i + 1, len(lines)):
                m2 = re.match(r"^(#{1,4}) ", lines[j])
                if m2 and len(m2.group(1)) <= level:
                    return i, j
            return i, len(lines)
    raise LookupError(f"절을 찾지 못했다: {title_startswith!r}")


def pick_table(tables: list[Table], *required_headers: str) -> Table:
    """머리 칸으로 표를 고른다. 표의 순번이 아니라 열 이름으로 고른다 —
    seed 에 표가 하나 끼어들어도 검사가 엉뚱한 표를 읽지 않게."""
    for t in tables:
        joined = " | ".join(t.headers)
        if all(h in joined for h in required_headers):
            return t
    raise LookupError(f"표를 찾지 못했다 (필요 열: {required_headers})")


# ──────────────────────────────────────────────────────────────────────────
# 절 표기 정규화
# ──────────────────────────────────────────────────────────────────────────

VERSE_RE = re.compile(rf"(?:({'|'.join(BOOKS)})\s*)?(\d+):(\d+)([ab])?")


def norm_verse(tok: str) -> str | None:
    """'수 6:23 중' · '6:17b' · '마 1:5' → 정규형. 못 읽으면 None."""
    tok = strip_markup(tok)
    m = VERSE_RE.search(tok)
    if not m:
        return None
    book = m.group(1) or DEFAULT_BOOK
    return f"{book} {m.group(2)}:{m.group(3)}{m.group(4) or ''}"


def parse_verse_list(cell: str) -> set[str]:
    """'6:17b · 6:22 · 6:25 · 약 2:25' → 정규형 집합.

    범위('2:2-7 전체')는 펼친다. '없음' 으로 시작하는 칸은 빈 집합이다 —
    `rahab_height_rope` 행이 그렇고, 그 행의 델타는 절이 아니라 에셋 층위다(§5-2).
    """
    cell = strip_markup(cell)
    if not cell or cell.startswith("없음"):
        return set()
    out: set[str] = set()
    # `+` 도 구분자다 — Scene 경로 표가 "6:25 · 약 2:25 **+** 마 1:5" 처럼 쓴다.
    # 이걸 놓치면 마지막 절이 조용히 사라진다.
    for part in re.split(r"·|,|\+", cell):
        part = part.strip()
        if not part:
            continue
        rng = re.search(
            rf"(?:({'|'.join(BOOKS)})\s*)?(\d+):(\d+)\s*[-–]\s*(\d+)", part
        )
        if rng:
            book = rng.group(1) or DEFAULT_BOOK
            chap, a, b = int(rng.group(2)), int(rng.group(3)), int(rng.group(4))
            for v in range(a, b + 1):
                out.add(f"{book} {chap}:{v}")
            continue
        v = norm_verse(part)
        if v:
            out.add(v)
    return out


def parse_scenes(cell: str) -> set[int]:
    """'1 · 5' · '3 · 4' · '4' → {1,5} …"""
    return {int(x) for x in re.findall(r"\d+", strip_markup(cell))}


# ──────────────────────────────────────────────────────────────────────────
# 결과
# ──────────────────────────────────────────────────────────────────────────

@dataclass
class Result:
    key: str
    status: str
    reason: str
    details: list[str] = field(default_factory=list)


def ok(key, reason, details=None) -> Result:
    return Result(key, PASS, reason, details or [])


def bad(key, reason, details=None) -> Result:
    return Result(key, FAIL, reason, details or [])


def blocked(key, reason, details=None) -> Result:
    return Result(key, BLOCKED, reason, details or [])


# ──────────────────────────────────────────────────────────────────────────
# yml 쪽 — designer 산출물
# ──────────────────────────────────────────────────────────────────────────

def load_yaml(path: str):
    try:
        import yaml  # type: ignore
    except ImportError:
        return None
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f)


def scene_files(content_dir: str) -> list[str]:
    if not os.path.isdir(content_dir):
        return []
    return sorted(
        os.path.join(content_dir, n)
        for n in os.listdir(content_dir)
        if re.fullmatch(r"scene\d+\.yml", n)
    )


def walk_dicts(node):
    """중첩 구조 전체에서 dict 를 훑는다. 키가 최상위에만 있다고 가정하지 않는다 —
    코퍼스 실측 결과 `consent_card_id` 는 최상위에도 `conditional_blocks` 안에도 있다."""
    if isinstance(node, dict):
        yield node
        for v in node.values():
            yield from walk_dicts(v)
    elif isinstance(node, list):
        for v in node:
            yield from walk_dicts(v)


# ──────────────────────────────────────────────────────────────────────────
# 검사
# ──────────────────────────────────────────────────────────────────────────

def check(seed_path: str, content_dir: str) -> list[Result]:
    with open(seed_path, encoding="utf-8") as f:
        lines = f.read().split("\n")

    res: list[Result] = []
    files = scene_files(content_dir)
    rel_content = os.path.relpath(content_dir, ROOT)

    # ── (a) staging_constraints · user_presence ──────────────────────────
    s, e = section_bounds(lines, "5-1. staging_constraints")
    t_stag = pick_table(parse_tables(lines, s, e), "Scene", "user_presence", "staging_constraints")
    stag: dict[int, tuple[str, str]] = {}
    for r in t_stag.rows:
        sc = parse_scenes(r["Scene"])
        if len(sc) != 1:
            continue
        n = sc.pop()
        stag[n] = (
            r[[h for h in t_stag.headers if h.startswith("user_presence")][0]],
            r[[h for h in t_stag.headers if h.startswith("staging_constraints")][0]],
        )
    missing = [n for n in range(1, 6) if n not in stag]
    empty = [n for n, (u, g) in sorted(stag.items()) if not u or not g]
    if missing or empty:
        res.append(bad(
            "a-doc", f"§5-1 표 결손 — 미선언 Scene {missing} · 빈 칸 Scene {empty}",
            [f"표 위치 SEED-RAHAB.md:{t_stag.line}"],
        ))
    else:
        res.append(ok("a-doc", f"§5-1 표 5/5 Scene 이 두 열을 다 채운다 (SEED-RAHAB.md:{t_stag.line})"))

    # ── (e) exposure_grade / (b) trigger_categories 값 표 ─────────────────
    s, e = section_bounds(lines, "5-3-a.")
    tabs_53a = parse_tables(lines, s, e)
    t_scene = pick_table(tabs_53a, "Scene", "exposure_grade", "trigger_categories")
    grade: dict[int, str] = {}
    cats: dict[int, set[str]] = {}
    r5: dict[int, tuple[str, str]] = {}
    for r in t_scene.rows:
        sc = parse_scenes(r["Scene"])
        if len(sc) != 1:
            continue
        n = sc.pop()
        grade[n] = r["exposure_grade"].strip()
        cats[n] = {c.strip() for c in re.split(r"·|,", r["trigger_categories"]) if c.strip()}
        r5[n] = (r.get("default_path", "").strip(), r.get("llm_optin_only", "").strip())

    undeclared = [n for n in range(1, 6) if not grade.get(n)]
    illegal = {n: g for n, g in sorted(grade.items()) if g not in ("A", "B", "C")}
    if undeclared or illegal:
        res.append(bad(
            "e-doc", f"등급 결손 — 미선언 {undeclared} · 등급 아님 {illegal}",
            [f"표 위치 SEED-RAHAB.md:{t_scene.line}", "헌장 §2.1 의 등급은 A·B·C 뿐이다"],
        ))
    else:
        vals = sorted(set(grade.values()))
        res.append(ok(
            "e-doc", f"§5-3-a 5/5 Scene 이 exposure_grade 를 선언 — 값 {vals}",
            [f"⚠️ 값의 타당성은 재지 않는다. 5/5 선언과 A·B·C 소속만 본다 "
             f"(SEED-RAHAB.md:{t_scene.line})"],
        ))

    # ── (e-cross) §2 서사 아크 표의 「등급」 열 ↔ §5-3-a 의 exposure_grade ──
    #
    # 이 검사는 rev.10 채점이 낸 결함에서 나왔다. §5-3-a 는 5 Scene 전부 `C` 로
    # 확정했는데 §2 표의 등급 열은 C·C·B·B·B 로 남아 있었다 — rev.2 의 값이다.
    # 위의 (e) 는 §5-3-a **한 표만** 읽으므로 그 상태에서도 초록을 냈다.
    # **한 표만 읽는 검사는 두 표가 갈라진 것을 원리적으로 못 본다.**
    # 등급은 이 문서에서 두 곳이 말하므로, 두 곳을 다 읽어 대조한다.
    try:
        s2, e2 = section_bounds(lines, "2. 서사 아크")
        t_arc = pick_table(parse_tables(lines, s2, e2), "#", "제목", "등급")
    except LookupError as ex:
        res.append(blocked("e-cross", f"§2 서사 아크 표를 찾지 못했다 — {ex}"))
    else:
        arc: dict[int, str] = {}
        for r in t_arc.rows:
            num = strip_markup(r["#"]).strip()
            if num.isdigit():
                arc[int(num)] = strip_markup(r["등급"]).strip()
        clash = [f"Scene {n}: §2 표 {arc[n]!r} ≠ §5-3-a {grade[n]!r}"
                 for n in sorted(set(arc) & set(grade)) if arc[n] != grade[n]]
        only = sorted(set(grade) - set(arc)) + sorted(set(arc) - set(grade))
        if clash:
            res.append(bad(
                "e-cross", f"등급을 말하는 두 표가 {len(clash)}개 Scene 에서 갈린다", clash + [
                    f"§2 표 SEED-RAHAB.md:{t_arc.line} · §5-3-a 표 SEED-RAHAB.md:{t_scene.line}",
                    "어느 쪽이 정본인지는 이 검사기가 정하지 않는다. 두 표를 같게 만들어라",
                ]))
        elif only:
            res.append(bad("e-cross", f"한쪽 표에만 있는 Scene {only}",
                           [f"§2:{t_arc.line} · §5-3-a:{t_scene.line}"]))
        else:
            res.append(ok("e-cross",
                          f"§2 표와 §5-3-a 표의 등급이 {len(arc)}개 Scene 전건 일치 — {sorted(set(arc.values()))}",
                          [f"§2:{t_arc.line} · §5-3-a:{t_scene.line}"]))

    # ── (b) 양방향 — trigger_scenes ↔ trigger_categories ─────────────────
    s53, e53 = section_bounds(lines, "5-3. 양방향 불변식")
    body = "\n".join(lines[s53:e53])
    m = re.search(r"trigger_scenes\s*:\s*\[([0-9,\s]*)\]", body)
    if not m:
        res.append(blocked("b-doc", "§5-3 에서 trigger_scenes 선언을 찾지 못했다"))
    else:
        declared = {int(x) for x in re.findall(r"\d+", m.group(1))}
        nonempty = {n for n, c in cats.items() if c}
        if declared != nonempty:
            res.append(bad(
                "b-doc", "양방향 불일치 — trigger_scenes ≠ 트리거가 있는 Scene 집합",
                [f"trigger_scenes = {sorted(declared)} (§5-3)",
                 f"trigger_categories 비지 않은 Scene = {sorted(nonempty)} (§5-3-a)",
                 f"차집합: 선언만 {sorted(declared - nonempty)} · 값만 {sorted(nonempty - declared)}"],
            ))
        else:
            res.append(ok(
                "b-doc", f"양방향 일치 — trigger_scenes {sorted(declared)} = 트리거 보유 Scene",
                [f"트리거 어휘 {len(set().union(*cats.values()))}종 · "
                 f"빈 배열 Scene {sorted(set(range(1, 6)) - nonempty)}"],
            ))

    # ── (b2) 두 표의 Scene 배정이 서로 맞는가 ────────────────────────────
    try:
        t_new = pick_table(tabs_53a, "값", "코퍼스", "Scene")
    except LookupError:
        res.append(blocked("b-vocab", "§5-3-a 의 신규/기존 표를 찾지 못했다"))
        t_new = None
    if t_new is not None:
        by_value: dict[str, set[int]] = {}
        corpus_label: dict[str, str] = {}
        for r in t_new.rows:
            v = r["값"].strip()
            if not v:
                continue
            by_value[v] = parse_scenes(r["Scene"])
            corpus_label[v] = r["코퍼스"].strip()
        from_scene_tab: dict[str, set[int]] = {}
        for n, cs in cats.items():
            for c in cs:
                from_scene_tab.setdefault(c, set()).add(n)
        diffs = []
        for v in sorted(set(by_value) | set(from_scene_tab)):
            a_, b_ = by_value.get(v), from_scene_tab.get(v)
            if a_ != b_:
                diffs.append(f"{v}: 어휘표 {sorted(a_) if a_ else '없음'} ≠ Scene표 {sorted(b_) if b_ else '없음'}")
        if diffs:
            res.append(bad("b-vocab", f"§5-3-a 두 표의 Scene 배정 불일치 {len(diffs)}건", diffs))
        else:
            res.append(ok(
                "b-vocab", f"§5-3-a 두 표의 Scene 배정 일치 — 트리거 {len(by_value)}종",
                [f"신규 {sum(1 for x in corpus_label.values() if '신규' in x)}종 · "
                 f"기존 {sum(1 for x in corpus_label.values() if '기존' in x)}종"],
            ))

        # ── (b3) 철자 — '기존' 은 코퍼스에 실재하고 '신규' 는 없어야 한다 ──
        corpus_dir = os.path.join(ROOT, "content")
        corpus_vals: set[str] = set()
        corpus_files = []
        if os.path.isdir(corpus_dir):
            for ch in sorted(os.listdir(corpus_dir)):
                for p in scene_files(os.path.join(corpus_dir, ch)):
                    corpus_files.append(p)
                    data = load_yaml(p)
                    if data is None:
                        continue
                    for d in walk_dicts(data):
                        tc = d.get("trigger_categories")
                        if isinstance(tc, list):
                            corpus_vals |= {str(x).strip() for x in tc if x}
        if not corpus_files:
            res.append(blocked("b-spell", "코퍼스 Scene yml 이 없다 — 철자 대조 불가"))
        elif load_yaml(corpus_files[0]) is None:
            res.append(blocked("b-spell", "PyYAML 없음 — yml 을 읽을 수 없다"))
        else:
            wrong = []
            for v, label in sorted(corpus_label.items()):
                exists = v in corpus_vals
                if "기존" in label and not exists:
                    wrong.append(f"{v}: '기존' 이라 적혔으나 코퍼스에 없다 (오타이거나 라벨이 틀렸다)")
                if "신규" in label and exists:
                    wrong.append(f"{v}: '신규' 라 적혔으나 코퍼스에 이미 있다")
            if wrong:
                res.append(bad("b-spell", f"코퍼스 대조 불일치 {len(wrong)}건", wrong))
            else:
                res.append(ok(
                    "b-spell", f"신규/기존 라벨이 코퍼스 실측과 일치 — 코퍼스 어휘 {len(corpus_vals)}종",
                    [f"대조한 Scene yml {len(corpus_files)}개",
                     "⚠️ 이것은 라벨의 정합만 잰다. '신규 5종을 시리즈가 채택했는가' 는 "
                     "여전히 미결이다(§8 DP-R10)"],
                ))

    # ── (c) 절 생존 불변식 ────────────────────────────────────────────────
    s72, e72 = section_bounds(lines, "7-2. 동의 카드")
    tabs_72 = parse_tables(lines, s72, e72)
    body72 = "\n".join(lines[s72:e72])
    m = re.search(r"불변식\s*:\s*(.+?)\s*(?:은|는)\s*모든 거절", strip_markup(body72))
    invariant = norm_verse(m.group(1)) if m else None
    t_card = pick_table(tabs_72, "consent_card_id", "Scene", "skip_alternative_scene_id")
    t_surv = pick_table(tabs_72, "거절 조합", "Scene 5 에 남는 절")

    scene5_all: set[str] = set()
    for r in t_surv.rows:
        if r["거절 조합"].strip().startswith("없음"):
            scene5_all = parse_verse_list(r["Scene 5 에 남는 절"])
            break

    # 실행되는 경로 표(§7-2 rev.10). 단일 카드 행과 **다른 표**이고, 이쪽이 런타임이다.
    t_path = pick_table(tabs_72, "Scene", "실행되는 유일한 거절 경로", "그 경로가 빼는 것")

    def card_rows():
        for r in t_card.rows:
            cid = r["consent_card_id"].strip()
            if cid:
                yield cid, r

    exec_drop: dict[int, set[str]] = {}  # (d) 가 뒤에서 다시 쓴다 — 미정의로 죽지 않게

    if invariant is None:
        res.append(blocked("c-doc", "§7-2 에서 불변식 절을 문장으로 찾지 못했다"))
    elif not scene5_all:
        res.append(blocked("c-doc", "§7-2 절 생존 표에서 '거절 없음' 행을 찾지 못했다"))
    else:
        # (c-1) §9 가 명세한 대로 — 정의된 단일 카드 경로 7행
        violations, paths = [], []
        for cid, r in card_rows():
            scenes = parse_scenes(r["Scene"])
            dropped = parse_verse_list(r["거절 시 빠지는 절"])
            if 5 not in scenes:
                paths.append(f"{cid} (Scene {sorted(scenes)}) — Scene 5 를 건드리지 않는다")
                continue
            survivors = scene5_all - dropped
            paths.append(f"{cid} (Scene 5) 거절 → 남는 절 {len(survivors)}개"
                         f" {'✅' if invariant in survivors else '❌'}")
            if invariant not in survivors:
                violations.append(f"{cid}: {invariant} 가 빠진다 (제거 {sorted(dropped)})")
        if violations:
            res.append(bad("c-doc", f"불변식 위반(단일 카드 행) {len(violations)}건", violations))
        else:
            res.append(ok(
                "c-doc", f"단일 카드 행 {len(list(card_rows()))}행 전건에서 {invariant} 생존",
                paths + ["⚠️ **이 행들은 도달 불가다**(§7-2 rev.10) — 설계 의도이지 동작 명세가 "
                         "아니다. §9 가 (c) 를 이 7행으로 한정했으므로 명세대로 재되, "
                         "실행되는 경로는 아래 c-exec 이 따로 잰다"],
            ))

        # (c-2) 실제로 실행되는 Scene 경로 — rev.10 이 정정한 런타임 표
        exec_bad, exec_paths = [], []
        for r in t_path.rows:
            sc = parse_scenes(r["Scene"])
            if len(sc) != 1:
                continue
            n = sc.pop()
            exec_drop[n] = parse_verse_list(r["그 경로가 빼는 것"])
            if n != 5:
                continue
            survivors = scene5_all - exec_drop[n]
            exec_paths.append(f"Scene 5 거절 경로 → 남는 절 {sorted(survivors)}")
            if invariant not in survivors:
                exec_bad.append(f"Scene 5: {invariant} 가 빠진다 (제거 {sorted(exec_drop[n])})")
        if not exec_drop:
            res.append(blocked("c-exec", "§7-2 Scene 경로 표를 읽지 못했다"))
        elif exec_bad:
            res.append(bad(
                "c-exec", f"불변식 위반(실행 경로) {len(exec_bad)}건",
                exec_bad + ["이것이 rev.2 가 실제로 부순 자리다(§7-2:1616)"],
            ))
        else:
            res.append(ok(
                "c-exec", f"실행되는 Scene 경로 {len(exec_drop)}개에서 {invariant} 생존",
                exec_paths + ["Scene 4·5 는 카드가 둘이라 단일 카드 행과 경로가 다르다(§7-2:1577)"],
            ))

        # (c-3) 생존 표의 도달 가능한 마지막 행 = 계산한 Scene 5 실행 경로여야 한다.
        # 표에 손으로 적은 결과와 카드 델타에서 계산한 결과가 갈리면, 판정의 근거가 둘이 된다.
        reach = [r for r in t_surv.rows
                 if "✅" in r["도달"] and not r["거절 조합"].strip().startswith("없음")]
        if 5 not in exec_drop:
            res.append(blocked("c-cross", "Scene 5 실행 경로를 읽지 못해 생존 표와 대조 불가"))
        elif len(reach) != 1:
            res.append(bad(
                "c-cross", f"생존 표에 도달 가능한 거절 행이 {len(reach)}개 — 1개여야 한다",
                [f"엔진의 거절 조건은 Scene 범위 이진이므로 경로는 하나다(§7-2:1586)"]
                + [f"행: {r['거절 조합']}" for r in reach],
            ))
        else:
            stated = parse_verse_list(reach[0]["Scene 5 에 남는 절"])
            computed = scene5_all - exec_drop[5]
            if stated != computed:
                res.append(bad(
                    "c-cross", "생존 표의 서술과 카드 델타 계산이 다르다",
                    [f"표에 적힌 것: {sorted(stated)}",
                     f"델타로 계산한 것: {sorted(computed)}",
                     f"차: 표만 {sorted(stated - computed)} · 계산만 {sorted(computed - stated)}"],
                ))
            else:
                res.append(ok(
                    "c-cross", f"생존 표의 서술 = 카드 델타 계산 ({sorted(computed)})",
                    [f"대조한 행: '{reach[0]['거절 조합']}'"],
                ))

        # (c-4) Scene 경로가 빼는 것 = 그 Scene 카드들 델타의 합집합
        union_bad = []
        by_scene: dict[int, set[str]] = {}
        for cid, r in card_rows():
            for n in parse_scenes(r["Scene"]):
                by_scene.setdefault(n, set())
                by_scene[n] |= parse_verse_list(r["거절 시 빠지는 절"])
        for n, dropped in sorted(exec_drop.items()):
            u = by_scene.get(n, set())
            if u != dropped:
                union_bad.append(f"Scene {n}: 경로 {sorted(dropped)} ≠ 카드 합집합 {sorted(u)}")
        res.append(bad("c-union", f"경로 ≠ 카드 합집합 {len(union_bad)}건", union_bad) if union_bad
                   else ok("c-union",
                           f"{len(exec_drop)}개 Scene 경로가 전부 그 Scene 카드 델타의 합집합",
                           ["⚠️ 절 층위만 맞춘다. 에셋 층위 델타(rahab_height_rope)는 "
                            "절이 없으므로 이 대조에 들어오지 않는다(§5-2)"]))

    # ── (d) skip 목적지 ──────────────────────────────────────────────────
    ids, dfx = [], []
    for cid, r in card_rows():
        raw = r["skip_alternative_scene_id"].strip().strip('"').strip("'")
        if not raw:
            dfx.append(f"{cid}: 목적지가 비어 있다")
        elif raw.isdigit():
            # 룻 선례 — 정수를 쓰면 자기 참조가 타입 검사만 통과하고 도달 불가가 된다(§7-2)
            dfx.append(f"{cid}: 목적지가 정수 {raw!r} — 문자열이어야 한다")
        else:
            ids.append(raw)
    if dfx:
        res.append(bad("d-doc", f"§7-2 의 skip 목적지 결함 {len(dfx)}건", dfx))
    else:
        res.append(ok(
            "d-doc", f"§7-2 의 skip 목적지 {len(ids)}개가 전부 문자열",
            [f"목적지: {', '.join(sorted(set(ids)))}",
             "⚠️ 실재 해소는 재지 못한다 — conditional_blocks 는 yml 에 있고 그 파일이 없다"],
        ))

    # ── (d2) 카드의 목적지가 **실재하는 Scene 경로**로 해소되는가 ─────────
    # §6:1399 「한 Scene 의 거절 경로는 하나」. 그러므로 카드가 가리킬 수 있는 목적지는
    # Scene 경로 표에 선언된 id 뿐이다. yml 이 없어 conditional_blocks 대조는 못 하지만,
    # **문서 안에서의 해소**는 지금 잴 수 있다 — 그리고 이것이 rev.7 잔재를 잡는 자리다.
    path_id: dict[int, str] = {}
    for r in t_path.rows:
        sc = parse_scenes(r["Scene"])
        if len(sc) == 1:
            path_id[sc.pop()] = r["실행되는 유일한 거절 경로"].strip().strip('"').strip("'")
    unres = []
    for cid, r in card_rows():
        raw = r["skip_alternative_scene_id"].strip().strip('"').strip("'")
        for n in parse_scenes(r["Scene"]):
            want = path_id.get(n)
            if want and raw != want:
                unres.append(f"{cid} (Scene {n}): {raw!r} → Scene 경로는 {want!r} 하나뿐이다")
    if not path_id:
        res.append(blocked("d-dest", "Scene 경로 표에서 목적지 id 를 읽지 못했다"))
    elif unres:
        res.append(bad(
            "d-dest", f"카드 목적지가 실행 경로로 해소되지 않는다 {len(unres)}건",
            unres + ["rev.8 이 카드별 경로를 Scene 당 하나로 접었다(§6:1399). 접힌 뒤에도 "
                     "카드 표의 목적지 열이 남아 있으면 designer 는 도달 불가능한 "
                     "conditional_blocks 를 만든다"],
        ))
    else:
        res.append(ok(
            "d-dest", f"카드 {len(list(card_rows()))}행 전건이 Scene 경로 {len(path_id)}개로 해소",
            [f"Scene {n} → {v}" for n, v in sorted(path_id.items())],
        ))

    # ── (d3) 목적지 **이름에 적힌 절**이 그 경로의 델타와 맞는가 ──────────
    # rev.2 의 결함은 "6:27 로만 닫는다" 는 **이름**으로 남았다. 이름이 절을 담고 있으면
    # 그 이름은 designer 가 읽는 명세다 — 틀린 이름은 틀린 구현을 부른다.
    def verses_in_id(name: str) -> set[str]:
        return {f"{DEFAULT_BOOK} {c}:{v}" for c, v in re.findall(r"_(\d+)_(\d+)(?=_|$)", name)}

    name_bad, name_ok = [], []
    for n, raw in sorted(path_id.items()):
        claimed = verses_in_id(raw)
        if not claimed:
            continue
        dropped = exec_drop.get(n, set())
        if "omit" in raw:
            actual, kind = dropped, "빼는 절"
        elif "close_on" in raw:
            actual, kind = (scene5_all - dropped) if n == 5 else set(), "남는 절"
        else:
            continue
        if claimed != actual:
            name_bad.append(f"{raw}: 이름이 말하는 {kind} {sorted(claimed)} ≠ 실제 {sorted(actual)}")
        else:
            name_ok.append(f"{raw} ✅ ({kind} {sorted(actual)})")
    if name_bad:
        res.append(bad(
            "d-name", f"경로 이름과 델타 불일치 {len(name_bad)}건",
            name_bad + ["이름은 designer 가 그대로 구현할 명세다 — 이름이 절을 덜 말하면 "
                        "그만큼 덜 남는 구현이 나온다(§7-2:1616 의 재발 경로)"],
        ))
    else:
        res.append(ok("d-name", f"절을 담은 경로 이름 {len(name_ok)}개가 델타와 일치", name_ok))

    # ── (f) 코퍼스 실측 — 한 Scene 을 덮는 카드 수 ────────────────────────
    corpus_dir = os.path.join(ROOT, "content")
    cover: dict[tuple[str, int], set[str]] = {}
    n_files = 0
    yaml_ok = True
    if os.path.isdir(corpus_dir):
        for ch in sorted(os.listdir(corpus_dir)):
            for p in scene_files(os.path.join(corpus_dir, ch)):
                data = load_yaml(p)
                if data is None:
                    yaml_ok = False
                    continue
                n_files += 1
                own = int(re.search(r"scene(\d+)\.yml", os.path.basename(p)).group(1))
                for d in walk_dicts(data):
                    cid = d.get("consent_card_id")
                    if not cid:
                        continue
                    covers = d.get("consent_covers_scenes")
                    scenes = (
                        {int(x) for x in covers}
                        if isinstance(covers, list) and covers
                        else {own}
                    )
                    for n in scenes:
                        cover.setdefault((ch, n), set()).add(str(cid))
    if not yaml_ok or n_files == 0:
        res.append(blocked("f-corpus", "코퍼스를 읽지 못했다 (PyYAML 부재 또는 파일 없음)"))
    else:
        mx = max((len(v) for v in cover.values()), default=0)
        over = [f"{c} Scene {n} → {sorted(v)}" for (c, n), v in sorted(cover.items()) if len(v) > 1]
        if over:
            res.append(bad(
                "f-corpus", f"§7-2 의 '최대 1' 이 깨졌다 — 최대 {mx}",
                over + ["§7-2 의 '라합이 첫 사례' 서술과 그 위에 세운 판정을 다시 봐야 한다"],
            ))
        else:
            res.append(ok(
                "f-corpus", f"코퍼스에서 한 Scene 을 덮는 카드 최대 {mx} — §7-2 실측 유지",
                [f"Scene yml {n_files}개 · 카드 보유 (인물,Scene) {len(cover)}쌍",
                 "라합은 Scene 4·5 에 둘씩 두는 첫 사례가 된다(§7-2)"],
            ))

    # ── yml 쪽 — 대조 대상이 있어야 잴 수 있다 ────────────────────────────
    if not files:
        why = f"{rel_content}/scene*.yml 이 없다 — designer 미착수(§9)"
        res += [
            blocked("a-yml", f"(a) staging_constraints·user_presence 대조 불가 — {why}"),
            blocked("b-yml", f"(b) trigger_categories 실값 대조 불가 — {why}"),
            blocked("d-yml", f"(d) conditional_blocks[].id 해소 불가 — {why}"),
            blocked("e-yml", f"(e) exposure_grade 실값 대조 불가 — {why}"),
        ]
        return res

    if load_yaml(files[0]) is None:
        res.append(blocked("yml", "PyYAML 없음 — yml 을 읽을 수 없다"))
        return res

    docs = {}
    for p in files:
        n = int(re.search(r"scene(\d+)\.yml", os.path.basename(p)).group(1))
        docs[n] = (p, load_yaml(p))

    def top(n: int) -> dict:
        d = docs[n][1]
        return d if isinstance(d, dict) else {}

    # (a) yml
    miss = []
    for n in sorted(docs):
        d = top(n)
        for k in ("user_presence", "staging_constraints"):
            if not d.get(k):
                miss.append(f"scene{n}.yml: {k} 없음 또는 빈 값")
    res.append(bad("a-yml", f"(a) 키 결손 {len(miss)}건", miss) if miss
               else ok("a-yml", f"(a) {len(docs)}개 Scene 전건에 두 키가 있다",
                       ["⚠️ 키의 존재만 본다. 표의 문장과 yml 의 문장이 같은 제약인지는 "
                        "기계가 판정하지 못한다 — 사람 리뷰 몫(RAHAB-REVIEW-LOG.md)"]))

    # (b) yml — 양방향 + 값 일치
    bad_b = []
    for n in sorted(docs):
        got = top(n).get("trigger_categories")
        if not isinstance(got, list):
            bad_b.append(f"scene{n}.yml: trigger_categories 가 배열이 아니다 (빈 배열도 명시해야 한다 — §5-3)")
            continue
        want = cats.get(n, set())
        if {str(x).strip() for x in got} != want:
            bad_b.append(f"scene{n}.yml: {sorted(str(x) for x in got)} ≠ §5-3-a {sorted(want)}")
    res.append(bad("b-yml", f"(b) 값 불일치 {len(bad_b)}건", bad_b) if bad_b
               else ok("b-yml", f"(b) {len(docs)}개 Scene 의 trigger_categories 가 §5-3-a 표와 일치"))

    # (d) yml — 목적지 해소
    block_ids: set[str] = set()
    for n in sorted(docs):
        for d in walk_dicts(docs[n][1]):
            for b in (d.get("conditional_blocks") or []) if isinstance(d.get("conditional_blocks"), list) else []:
                if isinstance(b, dict) and b.get("id"):
                    block_ids.add(str(b["id"]))
    unresolved = []
    for n in sorted(docs):
        for d in walk_dicts(docs[n][1]):
            dest = d.get("skip_alternative_scene_id")
            if dest is None:
                continue
            if not isinstance(dest, str):
                unresolved.append(f"scene{n}.yml: {dest!r} 가 문자열이 아니다")
            elif dest not in block_ids:
                unresolved.append(f"scene{n}.yml: {dest!r} 에 해당하는 conditional_blocks[].id 가 없다")
    res.append(bad("d-yml", f"(d) 미해소 {len(unresolved)}건", unresolved) if unresolved
               else ok("d-yml", f"(d) skip 목적지 전건이 실재 conditional_blocks[].id 로 해소",
                       [f"블록 id {len(block_ids)}개"]))

    # (e) yml
    bad_e = []
    for n in sorted(docs):
        got = str(top(n).get("exposure_grade") or "").strip()
        if not got:
            bad_e.append(f"scene{n}.yml: exposure_grade 미선언")
        elif got != grade.get(n):
            bad_e.append(f"scene{n}.yml: {got!r} ≠ §5-3-a {grade.get(n)!r}")
    res.append(bad("e-yml", f"(e) 등급 불일치 {len(bad_e)}건", bad_e) if bad_e
               else ok("e-yml", f"(e) {len(docs)}개 Scene 의 exposure_grade 가 §5-3-a 표와 일치"))

    return res


# ──────────────────────────────────────────────────────────────────────────

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="라합 staging·트리거·절 생존 검사")
    # 위치 인자를 받는 이유: `code_claims_check.py` 검사축 5 가 §실측 표의 도구를
    # `python3 scripts/<tool> <입력>` 으로 **다시 돌려** EXIT·수치를 대조한다
    # (`scripts/code_claims_check.py:325`). 위치 인자가 없으면 그 재실행이
    # argparse 오류로 죽고, 이 검사기의 수치는 아무도 재검하지 않는 값이 된다.
    ap.add_argument("seed_pos", nargs="?", default=None, help="검사할 seed (위치 인자)")
    ap.add_argument("--seed", default="docs/SEED-RAHAB.md")
    ap.add_argument("--content", default="content/rahab")
    a = ap.parse_args(argv)

    a.seed = a.seed_pos or a.seed
    seed = a.seed if os.path.isabs(a.seed) else os.path.join(ROOT, a.seed)
    content = a.content if os.path.isabs(a.content) else os.path.join(ROOT, a.content)
    if not os.path.exists(seed):
        sys.stderr.write(f"seed 없음: {seed}\n")
        return 2

    try:
        results = check(seed, content)
    except LookupError as ex:
        # 절·표를 못 찾은 것은 "문제 없음"이 아니라 판정 불가다.
        sys.stderr.write(f"판정 불가 — seed 구조가 예상과 다르다: {ex}\n")
        return 2

    n_pass = sum(1 for r in results if r.status == PASS)
    n_fail = sum(1 for r in results if r.status == FAIL)
    n_block = sum(1 for r in results if r.status == BLOCKED)

    print(f"=== check_rahab_staging :: {os.path.relpath(seed, ROOT)} ===")
    for r in results:
        print(f"  [{r.status:<7}] {r.key:<9} {r.reason}")
        for d in r.details:
            print(f"              - {d}")
    print(f"--- PASS {n_pass} / FAIL {n_fail} / BLOCKED {n_block} ---")
    if n_block:
        print("  ⚠️ BLOCKED 는 PASS 가 아니다 — 잴 수단이 없는 상태다. 통과로 보고하지 말 것.")
    print("  주장 범위: 이 검사기는 **선언과 선언의 정합**을 잰다. 선언된 제약이 "
          "실제 연출에서 지켜지는지는 재지 못한다.")
    print("  rc: 0=PASS · 1=FAIL · 2=BLOCKED (SEED-RAHAB.md §8-1 규약)")

    return 1 if n_fail else (2 if n_block else 0)


if __name__ == "__main__":
    sys.exit(main())

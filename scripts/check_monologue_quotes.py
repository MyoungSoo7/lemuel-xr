#!/usr/bin/env python3
"""프론트 모놀로그 성경 인용 원문 대조 게이트.

`frontend/src/lib/content/*-monologues.ts` 안에 `"자구"(참조)` 형태로 박힌 인용을
전부 뽑아 **개역개정 정본**(`docs/verses-monologues-gae.txt`)에 실재하는지 잰다.

왜 이 파일이 필요한가 (2026-08-21 신설)
    DB 쪽 성경 본문은 2026-07-18 의 교정 마이그레이션(`V20260718004903`) 이후
    원문 대조 체계 안에 있다. 그런데 **프론트 문자열은 그 체계 밖이었다** —
    2026-08-21 전수 대조 시점까지 이 네 파일을 읽는 스크립트가 리포에 하나도
    없었다. 그날 32건 중 16건이 원문과 달랐다(어미 변형 10 · 무표시 중간절단 5 ·
    원문에 없는 쉼표 1). 틀렸다고 확인된 적이 없었던 게 아니라, **한 번도
    대조된 적이 없었다.** 그 상태가 되풀이되지 않게 고정하는 것이 이 게이트다.

기존 게이트와 무엇이 다른가
    `quote_sweep.py` 는 docs/*.md 를, `verse_lines_check.py` 는 자막 줄을 본다.
    둘 다 `frontend/src/lib/content/` 를 입력으로 갖지 않는다. 그리고 TS 소스는
    문서와 달리 **문자열 리터럴이 `" +` 로 쪼개져** 있어서, 문서용 추출기를 그대로
    들이대면 인용의 반쪽만 본다 — 실제로 삼상 17:45 는 뒤쪽 조각만 우연히 맞아
    통과했고 앞쪽에서 잘려 나간 절이 통째로 미탐이었다. 여기서는 리터럴을 먼저
    이어 붙인 뒤 추출한다.

판정 등급
    MATCH          정본 해당 절의 축자 부분문자열                      통과
    ELLIPSIS       생략부호(…)로 이은 축자 조각이 순서대로 전부 있음   통과
    PUNCT_ONLY     구두점만 다름 (원문에 없는 쉼표 삽입 등)            실패
    SPLICE_NOMARK  생략부호 없이 중간을 잘라 이어 붙임 (자구는 원문)   실패
    ALTERED        어미·인칭·역본 혼합 등 자구 자체가 다름             실패
    NO_CANON       참조가 정본 표에 없다 — 판정 불가                   실패

    ELLIPSIS 를 통과로 두는 것은 *생략했다는 사실을 독자에게 표시했기 때문*이다.
    SPLICE_NOMARK 는 자구가 전부 원문이어도 실패다 — 표시 없이 중간을 들어내면
    한정어가 사라져 뜻이 바뀌고(삼상 17:45 의 "네가 모욕하는 이스라엘 군대의"),
    독자는 잘렸다는 것을 알 길이 없다.

역본 정책
    개역개정 하나로만 잰다. `docs/CONTENT-WORKFLOW.md` 가 "MVP 는 개역개정 우선"
    으로 정해 두었고, 현대인의 성경은 `docs/BACKEND-ARCHITECTURE.md` 위험 #4 에서
    라이선스 분쟁 항목으로 잡혀 있다(완화책이 "개역개정 fallback"). 정본을 하나로
    묶어 두지 않으면 한 문장 안에서 두 역본이 섞이는 실패가 다시 난다 —
    2026-08-21 에 시 62:5 와 요 14:6 이 정확히 그 형태였다.

이 게이트가 **못 잡는 것** (적지 않으면 초록이 거짓말이 된다)
    - **인용부호 없이 쓴 성경 자구.** 참조도 따옴표도 없이 본문에 녹인 문장은
      추출 대상 밖이다. 그건 `verse_lines_check.py` 계열의 몫이다.
    - **참조는 맞는데 절 선택이 부적절한 경우.** 자구가 그 절에 실재하면 통과다.
      문맥상 엉뚱한 절을 끌어다 쓴 것은 사람이 판정한다.
    - **시나리오 yml 의 카드 라벨.** `moses.yml` 의 `label:` 은 참조를 달고 있지만
      따옴표 인용 형식이 아니라 여기 안 걸린다. 2026-08-21 현재 "제가 누구이기에"
      (출 3:11 은 "내가 누구이기에") 처럼 의역 라벨이 남아 있다 — 알고 남긴 것이다.
    - **정본 표 자체의 오류.** 표는 bskorea HTML 을 기계 파싱해 만들지만, 그 파싱이
      틀리면 게이트는 틀린 기준으로 초록을 낸다. `--refresh` 로 재생성해 diff 가
      비는지가 유일한 확인 수단이다.

사용
    python3 scripts/check_monologue_quotes.py            # 판정 (네트워크 안 씀)
    python3 scripts/check_monologue_quotes.py --list     # 전 건 판정 출력
    python3 scripts/check_monologue_quotes.py --refresh  # 정본 표 재생성 (네트워크)

rc
    0  전건 통과
    1  실패 건 있음
    2  판정 불가 (정본 표 없음 · 소스 파일 없음 · 인용 0건)
"""

from __future__ import annotations

import argparse
import re
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "frontend/src/lib/content"
CANON = ROOT / "docs/verses-monologues-gae.txt"

# ── 추출 ──────────────────────────────────────────────────────────────────
# `\n` 은 문단 경계 표식으로 남긴다 — 인용이 줄바꿈을 넘어 다음 문단을 삼키지
# 않게 하는 경계다. 리터럴 이어붙이기(`" +\n  "`) 는 그 전에 해치운다.
SENTINEL = "␄"
REF = r"\(([가-힣]+)\s?(\d+):(\d+)(?:[-~](\d+))?\)"
QUOTED = re.compile(
    r'["“”「](?P<text>[^"“”「」%s\n]{2,200})["“”」]\s*' % SENTINEL + REF
)
JOIN = re.compile(r"[\"']\s*\+\s*[\"']", re.S)

ELL = re.compile(r"…+|\.\.\.+|‥+")
FOOTNOTE = re.compile(r"\d+\)")          # bskorea 본문에 인라인으로 박히는 각주 번호
MD = re.compile(r"[*_`]")                # 인용 안에 들어간 마크다운 강조
QUOTE_CHARS = "\"“”„‟'‘’「」『』《》〈〉"
PUNCT = ",.!?;:·…‥、。~-–—"


def prepare(src: str) -> str:
    src = src.replace("\\n", SENTINEL)
    src = src.replace('\\"', '"').replace("\\'", "'")
    return JOIN.sub("", src)


def find_line(raw: str, text: str) -> int:
    """이어붙이기 전 원본에서 인용의 첫 조각이 나오는 줄을 되찾는다."""
    probe = text[:12].replace('"', "")
    for i, line in enumerate(raw.splitlines(), 1):
        if probe and probe in line.replace('\\"', '"'):
            return i
    return 0


def sources() -> list[Path]:
    return sorted(
        p for p in SRC_DIR.glob("*-monologues.ts") if not p.name.endswith(".test.ts")
    )


def extract() -> tuple[list[dict], list[tuple]]:
    pairs: list[dict] = []
    refs: set[tuple] = set()
    for f in sources():
        raw = f.read_text(encoding="utf-8")
        joined = prepare(raw)
        for m in QUOTED.finditer(joined):
            pairs.append(
                {
                    "site": f"{f.name}:{find_line(raw, m.group('text'))}",
                    "text": m.group("text").strip(),
                    "book": m.group(2),
                    "chapter": int(m.group(3)),
                    "verse": int(m.group(4)),
                    "end": int(m.group(5)) if m.group(5) else None,
                }
            )
        for m in re.finditer(REF, joined):
            refs.add(
                (
                    m.group(1),
                    int(m.group(2)),
                    int(m.group(3)),
                    int(m.group(4)) if m.group(4) else None,
                )
            )
    # end=None 과 int 가 섞이므로 정렬 키에서 None 을 0 으로 눕힌다.
    return pairs, sorted(refs, key=lambda r: (r[0], r[1], r[2], r[3] or 0))


# ── 정규화 ────────────────────────────────────────────────────────────────
def base(s: str) -> str:
    """따옴표·공백·각주번호·마크다운을 걷어낸 비교용 형태."""
    s = unicodedata.normalize("NFC", s)
    s = FOOTNOTE.sub("", s)
    s = MD.sub("", s)
    return re.sub(r"[%s\s]" % re.escape(QUOTE_CHARS), "", s)


def loose(s: str) -> str:
    """구두점까지 무시 — 구두점만 다른 경우를 따로 세기 위한 것."""
    return re.sub(r"[%s]" % re.escape(PUNCT), "", base(s))


def words(s: str) -> list[str]:
    s = unicodedata.normalize("NFC", s)
    s = MD.sub("", FOOTNOTE.sub("", s))
    s = re.sub(r"[%s]" % re.escape(QUOTE_CHARS + PUNCT), "", s)
    return [w for w in re.sub(r"\s+", " ", s).strip().split() if w]


def ordered_in(fragments, target: str) -> bool:
    pos = 0
    for f in fragments:
        if not f:
            continue
        i = target.find(f, pos)
        if i < 0:
            return False
        pos = i + len(f)
    return True


# ── 정본 표 ───────────────────────────────────────────────────────────────
def load_canon() -> dict[tuple[str, int, int], str]:
    table: dict[tuple[str, int, int], str] = {}
    for line in CANON.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        ref, _, text = line.partition("\t")
        m = re.fullmatch(r"([가-힣]+)\s(\d+):(\d+)", ref.strip())
        if not m:
            continue
        table[(m.group(1), int(m.group(2)), int(m.group(3)))] = text.strip()
    return table


def canon_text(table, book, chapter, verse, end) -> str | None:
    """범위 참조는 그 절들을 이어 붙인 것을 정본으로 본다."""
    last = end if end and end >= verse else verse
    parts = []
    for v in range(verse, last + 1):
        t = table.get((book, chapter, v))
        if t is None:
            return None
        parts.append(t)
    return " ".join(parts)


# ── 판정 ──────────────────────────────────────────────────────────────────
def classify(quote: str, canon: str) -> str:
    q_b, c_b = base(quote), base(canon)
    if not c_b:
        return "NO_CANON"
    if q_b in c_b:
        return "MATCH"
    frags = [base(x) for x in ELL.split(quote)]
    if ELL.search(quote) and len(frags) > 1 and ordered_in(frags, c_b):
        return "ELLIPSIS"
    if loose(quote) in loose(canon):
        return "PUNCT_ONLY"
    w = [re.sub(r"\s", "", x) for x in words(quote)]
    if len(w) >= 2 and ordered_in(w, loose(canon)):
        return "SPLICE_NOMARK"
    return "ALTERED"


PASSING = {"MATCH", "ELLIPSIS"}
NOTE = {
    "MATCH": "축자",
    "ELLIPSIS": "생략부호 있는 생략 인용",
    "PUNCT_ONLY": "원문에 없는 구두점",
    "SPLICE_NOMARK": "생략부호 없는 중간 절단 — 표시 없이 뜻이 좁아진다",
    "ALTERED": "자구가 개역개정과 다름",
    "NO_CANON": "정본 표에 없는 참조 — --refresh 로 표를 넓혀라",
}


# ── --refresh (네트워크) ──────────────────────────────────────────────────
BOOK_CODE = {
    "창": "gen", "출": "exo", "레": "lev", "민": "num", "신": "deu",
    "수": "jos", "삿": "jdg", "룻": "rut", "삼상": "1sa", "삼하": "2sa",
    "왕상": "1ki", "왕하": "2ki", "시": "psa", "잠": "pro", "사": "isa",
    "렘": "jer", "겔": "ezk", "단": "dan",
    "마": "mat", "막": "mrk", "눅": "luk", "요": "jhn", "행": "act",
    "롬": "rom", "고전": "1co", "고후": "2co", "갈": "gal", "엡": "eph",
    "빌": "php", "골": "col", "히": "heb", "약": "jas", "계": "rev",
}
TAG = re.compile(r"<[^>]+>")
UA = "Mozilla/5.0 (compatible; lemuel-xr quote gate)"


def fetch_chapter(book: str, chapter: int) -> dict[int, str]:
    """대한성서공회 개역개정 HTML 을 파싱한다. 손으로 옮겨 적지 않는다.

    절 표식은 `<span class="number">1&nbsp;&nbsp;&nbsp;</span>` 형태다 —
    `&nbsp;` 를 안 보면 정규식이 한 절도 못 잡는다(초판이 그랬다).
    """
    code = BOOK_CODE.get(book)
    if not code:
        raise SystemExit(f"[판정불가] 책 코드 미등록: {book} — BOOK_CODE 에 추가하라")
    url = (
        "https://www.bskorea.or.kr/bible/korbibReadpage.php"
        f"?version=GAE&book={code}&chap={chapter}"
    )
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        page = r.read().decode("utf-8", "replace")
    out: dict[int, str] = {}
    for m in re.finditer(
        r'<span class="number">(\d+)(?:&nbsp;|\s)*</span>(.*?)'
        r'(?=<span class="number">|<br\s*/?>)',
        page,
        re.S,
    ):
        t = TAG.sub("", m.group(2))
        t = FOOTNOTE.sub("", t).replace("&nbsp;", " ")
        out[int(m.group(1))] = re.sub(r"\s+", " ", t).strip()
    return out


def refresh() -> int:
    _, refs = extract()
    needed: set[tuple[str, int, int]] = set()
    for book, chapter, verse, end in refs:
        for v in range(verse, (end if end and end >= verse else verse) + 1):
            needed.add((book, chapter, v))

    chapters = sorted({(b, c) for b, c, _ in needed})
    table: dict[tuple[str, int, int], str] = {}
    for book, chapter in chapters:
        print(f"  {book} {chapter} …", file=sys.stderr)
        verses = fetch_chapter(book, chapter)
        for b, c, v in sorted(x for x in needed if x[0] == book and x[1] == chapter):
            text = verses.get(v)
            if not text:
                print(f"[판정불가] {b} {c}:{v} 본문을 못 읽었다", file=sys.stderr)
                return 2
            table[(b, c, v)] = text
        time.sleep(1.2)  # 1차 출처에 대한 예의

    order = list(BOOK_CODE)
    rows = sorted(table.items(), key=lambda kv: (order.index(kv[0][0]), kv[0][1], kv[0][2]))
    body = [
        "# docs/verses-monologues-gae.txt — 프론트 모놀로그 인용 대조 정본 (개역개정)",
        "#",
        "# 무엇인가",
        "#   frontend/src/lib/content/*-monologues.ts 가 인용하는 절의 개역개정 본문이다.",
        "#   scripts/check_monologue_quotes.py 가 이 표에 대고 자구를 잰다.",
        "#",
        "# 출처",
        "#   대한성서공회 개역개정 HTML 기계 파싱. 손으로 옮겨 적지 않았다.",
        "#   재생성: python3 scripts/check_monologue_quotes.py --refresh",
        "#   본문의 인라인 각주 번호(`3)` 등)는 파싱 단계에서 제거한다.",
        "#",
        "# 역본을 개역개정 하나로 두는 이유는 판정기 docstring 의 '역본 정책' 절에 있다.",
        "",
    ]
    body += [f"{b} {c}:{v}\t{t}" for (b, c, v), t in rows]
    CANON.write_text("\n".join(body) + "\n", encoding="utf-8")
    print(f"{CANON.relative_to(ROOT)} — {len(rows)}절 기록", file=sys.stderr)
    return 0


# ── main ─────────────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--refresh", action="store_true", help="정본 표를 1차 출처에서 재생성")
    ap.add_argument("--list", action="store_true", help="통과 건까지 전부 출력")
    args = ap.parse_args()

    if not sources():
        print(f"[판정불가] 대상 소스가 없다: {SRC_DIR.relative_to(ROOT)}")
        return 2
    if args.refresh:
        return refresh()
    if not CANON.exists():
        print(f"[판정불가] 정본 표가 없다: {CANON.relative_to(ROOT)} (--refresh)")
        return 2

    table = load_canon()
    pairs, refs = extract()
    if not pairs:
        # 인용이 0건이면 이 게이트는 아무것도 지키지 않는다. 조용한 초록은
        # 추출기가 죽은 것과 구별되지 않으므로 판정 불가로 낸다.
        print("[판정불가] 인용을 한 건도 못 뽑았다 — 추출기나 소스 형식을 확인하라")
        return 2

    failed = []
    for p in pairs:
        canon = canon_text(table, p["book"], p["chapter"], p["verse"], p["end"])
        verdict = "NO_CANON" if canon is None else classify(p["text"], canon)
        p["verdict"], p["canon"] = verdict, canon or ""
        if verdict not in PASSING:
            failed.append(p)

    ref_only = len(refs) - len({(p["book"], p["chapter"], p["verse"], p["end"]) for p in pairs})
    tally = {}
    for p in pairs:
        tally[p["verdict"]] = tally.get(p["verdict"], 0) + 1

    if args.list:
        for p in pairs:
            mark = "  " if p["verdict"] in PASSING else "✗ "
            print(f"{mark}[{p['verdict']}] {p['book']} {p['chapter']}:{p['verse']} {p['site']}")
            print(f"      인용 {p['text']}")

    for p in failed:
        print(f"✗ [{p['verdict']}] {p['book']} {p['chapter']}:{p['verse']}  {p['site']}")
        print(f"    인용   {p['text']}")
        print(f"    개역개정 {p['canon'][:140]}")
        print(f"    → {NOTE[p['verdict']]}")

    summary = " · ".join(f"{k} {v}" for k, v in sorted(tally.items()))
    print(f"\n인용 {len(pairs)}건 ({summary}) / 참조만 달고 자구 없음 {ref_only}건")
    if failed:
        print(f"실패 {len(failed)}건 — 개역개정 축자로 고치거나, 생략이면 … 로 표시하라")
        return 1
    print("전건 개역개정 축자 (생략은 … 로 표시됨)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

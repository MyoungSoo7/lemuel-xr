#!/usr/bin/env python3
"""시드된 본문이 `translation` 라벨이 말하는 번역본과 실제로 같은가 — 자구 대조.

## 왜 생겼나 (2026-08-20)

`V20260820051500` 로 미시드 참조 46행을 채우면서, 같은 방법(현대인의 성경 원문에서
기계 추출)으로 **기존 46행도 대조해 봤다.** 절 단위 비교가 가능한 27행 중 25행이
현대인의 성경 자구와 달랐다. 예:

    ps-23:1  시드 "여호와는 나의 목자시니 내게 부족함이 없으리로다"
             KLB  "여호와는 나의 목자시니 내가 부족함이 없으리라."
    1kgs-19:4 시드 "... 한 로뎀나무 아래 앉아 죽기를 간청하며 ..."
             KLB   "... 싸리나무 아래 앉아서 죽기를 바라며 ..."

일치한 2행은 `V20260718004903` 에서 **실제로 원문 대조 교정을 거친** 행들이다.
즉 교정을 거친 것만 맞고, 나머지는 `translation='modern'` 이라고 적혀 있을 뿐
그 번역본이 아니다. 시드 파일이 선언한 저작권 태도("현대인의 성경 fair use,
공개 전 개역개정 swap")도 이 라벨 위에 서 있으므로 라벨이 틀리면 그 계획도 흔들린다.

그 25행을 어느 번역본으로 정렬할지는 **라이선스 결정** 이라 검사기가 정할 수 없다.
이 검사기가 하는 일은 부채를 세어서 못 도망가게 못 박는 것뿐이다 — 그래서
초록으로 출발하지 않는다.

## 기준선 — 왜 텍스트가 아니라 해시인가

`scripts/klb_reference_hashes.json` 은 참조별 **sha256** 만 담는다. 정답 본문을
리포에 한 벌 더 복제하지 않으려는 것이다(저작권). 대신 `--refresh` 로 언제든
재생성할 수 있게 수거 경로를 코드에 남겼다 — 재현 불가능한 기준선은 기준선이 아니다.

## 판정 규칙

- **PASS** — 정규화 후 해시가 같다.
- **FAIL** — 다르다.
- **BLOCKED** — 대조 불가. 기준 해시가 없거나(그 절을 아직 안 수거),
  행의 `verse_start..verse_end` 가 번역본의 절 단위와 다르다(시드가 "..." 로
  줄여 인용한 여러 절 묶음 — 자구 대조의 대상이 아니다).

정규화: 활자 따옴표(“ ” ‘ ’)를 ASCII 로, 양끝 따옴표 제거(기존 시드는 본문을
작은따옴표로 감싸 저장한다), 연속 공백 1칸. 그 이상은 손대지 않는다 — 정규화를
느슨하게 할수록 검사가 통과하는 것이지 데이터가 맞아지는 것이 아니다.

grep 금지 — `scripts/newchar_gates.py:20`.

rc: 0=PASS · 1=FAIL · ≥126 은 판정이 아니라 실행 실패.
"""

from __future__ import annotations

import hashlib
import json
import pathlib
import re
import subprocess
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration"
FIXTURE = pathlib.Path(__file__).resolve().parent / "klb_reference_hashes.json"

VERSION = "KLB"  # BibleGateway 의 현대인의 성경 코드
UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"

# INSERT 한 행. book/book_code 순서가 마이그레이션마다 다르므로 사이는 건너뛴다.
ROW_RE = re.compile(
    r"\('([a-z0-9]+)-(\d+):(\d+)',\s*'(\w+)',.*?,\s*(\d+),\s*(\d+),\s*(\d+|NULL),"
    r"(?:\s*--[^\n]*)?\s*('(?:[^']|'')*')",
    re.S,
)
UPDATE_RE = re.compile(
    r"UPDATE scripture_passages\s+SET text = ('(?:[^']|'')*')\s+"
    r"WHERE reference = '([^']+)' AND translation = '(\w+)'"
)

# book_code -> BibleGateway 책 이름 · 절 span 클래스 접두
BOOKS = {
    "gen": ("Genesis", "Gen"), "ex": ("Exodus", "Exod"), "num": ("Numbers", "Num"),
    "ruth": ("Ruth", "Ruth"), "1sam": ("1 Samuel", "1Sam"), "1kgs": ("1 Kings", "1Kgs"),
    "ps": ("Psalm", "Ps"), "prov": ("Proverbs", "Prov"), "eccl": ("Ecclesiastes", "Eccl"),
    "isa": ("Isaiah", "Isa"), "job": ("Job", "Job"), "matt": ("Matthew", "Matt"),
    "mk": ("Mark", "Mark"), "lk": ("Luke", "Luke"), "jn": ("John", "John"),
}


def unquote(sql: str) -> str:
    return sql[1:-1].replace("''", "'")


def normalize(s: str) -> str:
    s = s.replace("“", '"').replace("”", '"')
    s = s.replace("‘", "'").replace("’", "'")
    s = s.replace("\xa0", " ")
    s = re.sub(r"^['\"]+|['\"]+$", "", s.strip())
    return re.sub(r"\s+", " ", s).strip()


def digest(s: str) -> str:
    return hashlib.sha256(normalize(s).encode("utf-8")).hexdigest()


def seeded_rows() -> list[dict]:
    rows: list[dict] = []
    for path in sorted(MIGRATIONS.glob("*.sql")):
        sql = path.read_text(encoding="utf-8")
        for m in ROW_RE.finditer(sql):
            book, ch_ref, v_ref, trans, ch, vs, ve, body = m.groups()
            rows.append({
                "ref": f"{book}-{ch_ref}:{v_ref}", "book": book, "trans": trans,
                "chapter": int(ch), "vs": int(vs),
                "ve": int(vs) if ve == "NULL" else int(ve),
                "text": unquote(body), "file": path.name,
            })
    # 뒤 마이그레이션의 UPDATE 교정을 반영한다 — 최종 상태를 재야 한다.
    for path in sorted(MIGRATIONS.glob("*.sql")):
        for body, ref, trans in UPDATE_RE.findall(path.read_text(encoding="utf-8")):
            for r in rows:
                if r["ref"] == ref and r["trans"] == trans:
                    r["text"] = unquote(body)
                    r["file"] += f" +{path.name}"
    return rows


# ---------------------------------------------------------------- --refresh
SUP_RE = re.compile(r"<sup\b.*?</sup>", re.S)
CHNUM_RE = re.compile(r'<span class="chapternum">.*?</span>', re.S)
HEAD_RE = re.compile(r"<h(\d)\b.*?</h\1>", re.S)
FOOT_RE = re.compile(r'<div class="(?:footnotes|crossrefs).*', re.S)
TAG_RE = re.compile(r"<[^>]+>")
SPAN_RE = re.compile(
    r'<span[^>]*class="text ([A-Za-z0-9]+)-(\d+)-(\d+)(?:-[A-Za-z0-9]+-\d+-(\d+))?"[^>]*>'
)
SPAN_TAG_RE = re.compile(r"<(/?)span\b[^>]*>")


def span_end(body: str, open_end: int) -> int:
    """절 span 의 닫는 태그 위치. 안쪽 중첩 span(소문자체 등)을 세어 넘긴다.

    다음 마커까지로 자르면 **장의 마지막 절만** 페이지 꼬리(내비게이션·저작권
    고지)를 통째로 삼킨다. 그 꼬리는 요청 때마다 달라져서 해시가 안 맞고,
    무엇보다 시드에 들어가면 본문이 아닌 것이 본문 행에 앉는다 —
    `V20260820051500` 의 `ruth-4:18` 이 실제로 그렇게 오염됐다(1958자).
    """
    depth = 1
    for tag in SPAN_TAG_RE.finditer(body, open_end):
        depth += -1 if tag.group(1) else 1
        if depth == 0:
            return tag.start()
    return len(body)


def fetch_chapter(book_name: str, chapter: int) -> dict[tuple[int, int], str]:
    """BibleGateway 장 페이지에서 절 텍스트를 축자 추출. (시작절, 끝절) -> 본문."""
    url = "https://www.biblegateway.com/passage/?" + urllib.parse.urlencode(
        {"search": f"{book_name} {chapter}", "version": VERSION}
    )
    raw = subprocess.run(
        ["curl", "-sL", "-A", UA, url], capture_output=True, timeout=90
    ).stdout.decode("utf-8", "replace")
    start = raw.find('class="passage-text"')
    body = raw[start:] if start > 0 else raw
    for pat in (FOOT_RE, HEAD_RE, SUP_RE, CHNUM_RE):
        body = pat.sub("", body)

    marks = list(SPAN_RE.finditer(body))
    out: dict[tuple[int, int], str] = {}
    for i, m in enumerate(marks):
        end = span_end(body, m.end())
        if i + 1 < len(marks):
            end = min(end, marks[i + 1].start())
        seg = normalize(TAG_RE.sub("", body[m.end(): end]))
        if not seg:
            continue
        key = (int(m.group(3)), int(m.group(4) or m.group(3)))
        out[key] = (out.get(key, "") + " " + seg).strip()
    return out


def refresh() -> int:
    rows = seeded_rows()
    wanted = {r["ref"] for r in rows}
    chapters = sorted({(r["book"], r["chapter"]) for r in rows})
    unknown = sorted({b for b, _ in chapters if b not in BOOKS})
    if unknown:
        print(f"[BLOCKED] BOOKS 에 없는 book_code: {unknown} — 매핑을 추가하라")
        return 126

    fixture = {
        "_source": f"BibleGateway {VERSION} (현대인의 성경) 장 페이지 HTML 축자 추출",
        "_url": "https://www.biblegateway.com/passage/?search=<Book+Chapter>&version=" + VERSION,
        "_normalize": "활자따옴표→ASCII · 양끝 따옴표 제거 · 연속공백 1칸",
        "_note": "본문 자체는 담지 않는다(저작권). sha256 과 절 범위만 담는다.",
        "refs": {},
    }
    for book, chapter in chapters:
        name, _ = BOOKS[book]
        verses = fetch_chapter(name, chapter)
        if not verses:
            print(f"[BLOCKED] {name} {chapter} 에서 절을 하나도 못 읽었다 — 판정 불가")
            return 126
        kept = 0
        for (vs, ve), text in verses.items():
            ref = f"{book}-{chapter}:{vs}"
            if ref not in wanted:      # 시드된 참조만 기준선에 남긴다
                continue
            fixture["refs"][ref] = {
                "sha256": digest(text), "verse_end": ve, "chars": len(normalize(text)),
            }
            kept += 1
        print(f"  {name} {chapter}: {len(verses)}절 중 {kept}건 채택", file=sys.stderr)

    FIXTURE.write_text(
        json.dumps(fixture, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"기준 해시 {len(fixture['refs'])}건 -> {FIXTURE.name}")
    return 0


# ---------------------------------------------------------------------- 검사
def main() -> int:
    if "--refresh" in sys.argv:
        return refresh()

    if not FIXTURE.exists():
        print(f"[BLOCKED] 기준 해시 파일이 없다 ({FIXTURE.name}) — 판정 불가")
        return 126
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))["refs"]

    rows = seeded_rows()
    if not rows:
        print("[BLOCKED] 마이그레이션에서 시드 행을 하나도 못 읽었다 — 판정 불가")
        return 126

    lines, npass, nfail, nblock = [], 0, 0, 0
    for r in sorted(rows, key=lambda x: (x["book"], x["chapter"], x["vs"])):
        base = fixture.get(r["ref"])
        if base is None:
            nblock += 1
            lines.append(f"  [BLOCKED] {r['ref']} 기준 해시 없음 — 대조 불가")
        elif base["verse_end"] != r["ve"]:
            nblock += 1
            lines.append(
                f"  [BLOCKED] {r['ref']} 절 범위가 번역본 단위와 다름 "
                f"(시드 {r['vs']}-{r['ve']} · {VERSION} {r['vs']}-{base['verse_end']}) — 대조 불가"
            )
        elif digest(r["text"]) == base["sha256"]:
            npass += 1
            lines.append(f"  [PASS   ] {r['ref']} {VERSION} 자구와 일치")
        else:
            nfail += 1
            lines.append(
                f"  [FAIL   ] {r['ref']} translation='{r['trans']}' 인데 {VERSION} 자구와 다름 "
                f"({r['file'].split()[0]})"
            )

    print(f"시드 행 {len(rows)}개 · 기준 해시 {len(fixture)}건\n")
    print("\n".join(lines))
    print(f"\n--- PASS {npass} / FAIL {nfail} / BLOCKED {nblock} ---")
    print("  ⚠️ 이 검사의 주장 범위: 저장된 자구가 현대인의 성경과 같으냐 까지다.")
    print("     어느 번역본이어야 하는지(라이선스)도, 그 본문이 화면에 열리는지도")
    print("     재지 않는다. BLOCKED 는 '괜찮다' 가 아니라 '아직 못 쟀다' 이다.")
    return 1 if nfail else 0


if __name__ == "__main__":
    sys.exit(main())

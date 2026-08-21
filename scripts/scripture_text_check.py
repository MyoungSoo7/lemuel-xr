#!/usr/bin/env python3
"""시드된 본문이 `translation` 라벨이 말하는 번역본과 실제로 같은가 — 자구 대조.

## 왜 생겼나 (2026-08-20)

`V20260820051500` 로 미시드 참조 46행을 채우면서, 같은 방법(현대인의 성경 원문에서
기계 추출)으로 **기존 46행도 대조해 봤다.** 44행이 현대인의 성경 자구와 달랐다. 예:

    ps-23:1  시드 "여호와는 나의 목자시니 내게 부족함이 없으리로다"
             KLB  "여호와는 나의 목자시니 내가 부족함이 없으리라."
    1kgs-19:4 시드 "... 한 로뎀나무 아래 앉아 죽기를 간청하며 ..."
             KLB   "... 싸리나무 아래 앉아서 죽기를 바라며 ..."

`translation='modern'` 이라고 적혀 있을 뿐 그 번역본이 아니었다. 시드 파일이
선언한 저작권 태도("현대인의 성경 fair use, 공개 전 개역개정 swap")도 이 라벨
위에 서 있으므로, 라벨이 틀리면 그 계획도 함께 흔들린다.

## 그럼 그건 뭐였나 — 개역개정도 아니었다

"라벨이 틀렸다" 만으로는 고칠 방향이 안 나온다. 그래서 대한성서공회 개역개정판
(GAE)에서 같은 46행을 수거해 한 번 더 댔다: **축자 일치 6 / 불일치 40.**
개역한글 철자(`의뢰하고` ↔ 개역개정 `신뢰하고`), 절 앞부분만 잘라 온 인용
(`ps-13:1` · `matt-6:26`), 어느 판본에도 없는 의역이 섞여 있었다. 어느 한
번역본도 아닌 **혼합** 이라는 뜻이고, 그래서 라벨만 `'rev'` 로 바꾸는 정정은
불가능했다. 런타임도 같은 답을 냈다 — `ScriptureController` 는
`defaultValue = "modern"` 에 폴백이 없어서, 라벨만 바꾸면 `/topics` 본문이
전부 `E_SCRIPTURE_NOT_FOUND` 가 된다.

결론은 자구를 KLB 축자로 맞추는 것이었다 —
`V20260820113000__align_modern_passages_to_klb.sql` 가 46행(FAIL 44 + 따옴표
껍데기 2)을 정렬했고, 지금 이 검사기는 92행 전부 PASS 다. **초록은 여기서부터
"되돌아가는 것을 잡는" 일을 한다.**

## 기준선 — 왜 텍스트가 아니라 해시인가

`klb_reference_hashes.json` · `krv_reference_hashes.json` 은 참조별 **sha256**
만 담는다. 정답 본문을 리포에 한 벌 더 복제하지 않으려는 것이다(저작권).
개역개정을 해시로만 들고 있는 이유도 같다 — 위 진단은 언제든 재현되지만
본문은 리포에 남지 않는다. `--refresh` 로 수거 경로를 코드에 남겼다;
재현 불가능한 기준선은 기준선이 아니다.

## 판정 규칙

라벨별로 대는 원문이 다르다 — `'modern'` → 현대인의 성경, `'rev'` → 개역개정.
원문이 하나뿐이면 물을 수 있는 건 "KLB 냐" 뿐이고 그건 라벨과 무관한 질문이다.

- **PASS** — 정규화 후 해시가 같다.
- **FAIL** — 다르다. 이때 *다른 번역본* 과는 맞는지도 함께 본다. "라벨만
  틀렸다" 와 "어느 번역본도 아니다" 는 고치는 방법이 다르기 때문이다.
- **BLOCKED** — 대조 불가. 라벨에 대응하는 원문 출처가 없거나, 기준 해시가
  없거나, 행의 절 범위가 번역본 단위와 어긋난다(KLB 가 38-39절을 합쳐 옮기는 식).

여러 절을 한 행에 담은 시드(`ps-88:1` 이 1-3절)는 범위를 덮는 번역본 단위를
이어 붙여 댄다. 절 단위 1:1 만 보던 때는 이런 19행이 통째로 BLOCKED 였다.

정규화(`normalize`)는 대조 전용이다: 활자 따옴표(“ ” ‘ ’)를 ASCII 로, 양끝
따옴표 제거(기존 시드 일부가 본문을 따옴표로 감싸 저장한다), 연속 공백 1칸.
**수거에는 쓰지 않는다** — `clean` 이 그 자리다. 양끝 따옴표를 떼는 규칙은
비교에서는 대칭이라 티가 안 나지만, 수거한 본문에 걸면 닫는 따옴표로 끝나는
절의 자구를 영구히 잃는다.

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
SCRIPTS = pathlib.Path(__file__).resolve().parent

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


def clean(s: str) -> str:
    """수거한 원문에서 **마크업 부스러기만** 걷어낸다. 자구는 건드리지 않는다.

    `normalize` 를 수거 단계에 쓰면 안 된다 — 그쪽은 양끝 따옴표를 떼는데, 절이
    닫는 따옴표로 끝나는 경우가 흔해서(룻 2:12 "…상을 주시기 원하네.”") 그걸
    본문에서 영구히 잃는다. 비교는 양쪽에 같은 규칙을 걸어 티가 안 나지만,
    **시드에 써넣는 순간 축자가 아닌 것이 축자 자리에 앉는다.**
    """
    s = s.replace("&nbsp;", " ").replace("\xa0", " ")
    s = s.replace("&quot;", '"').replace("&lt;", "<").replace("&gt;", ">")
    s = s.replace("&amp;", "&")
    return re.sub(r"\s+", " ", s).strip()


def normalize(s: str) -> str:
    """대조용 정규화. 양쪽에 똑같이 걸리므로 느슨해도 판정이 무너지진 않지만,
    느슨할수록 '검사가 통과하는' 것이지 데이터가 맞아지는 것이 아니다."""
    s = clean(s)
    s = s.replace("“", '"').replace("”", '"')
    s = s.replace("‘", "'").replace("’", "'")
    # 기존 시드 일부는 본문을 따옴표로 감싸 저장한다 — 그 껍데기만 벗긴다.
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


# --------------------------------------------------- 개역개정 (대한성서공회 GAE)
# 두 번째 번역본을 두는 이유는 "그럼 이건 뭐냐" 에 답하기 위해서다. 라벨이 틀렸다는
# 판정만으로는 고칠 방향이 안 나온다 — 개역개정과 대조해 보고서야 이 코퍼스가
# **어느 번역본도 아닌 혼합** 이라는 것이 드러났다(§ 머리말).
KRV_BOOKS = {
    "gen": "gen", "ex": "exo", "num": "num", "ruth": "rut", "1sam": "1sa",
    "1kgs": "1ki", "ps": "psa", "prov": "pro", "eccl": "ecc", "isa": "isa",
    "job": "job", "matt": "mat", "mk": "mrk", "lk": "luk", "jn": "jhn",
}
KRV_NUM_RE = re.compile(r'<span class="number">(\d+)(?:&nbsp;|\s)*</span>')
KRV_TITLE_RE = re.compile(r'<font class="smallTitle">.*?</font>\s*(?:<br\s*/?>)*', re.S)
KRV_SCRIPT_RE = re.compile(r"<script\b.*?</script>", re.S)
# 각주 팝업(class=D2)은 화면에 숨어 있지만 마크업상 본문 뒤에 붙어 있다. 그냥 두면
# "교훈"(시 42:1) · "시 22:1"(마 27:46) 이 절 끝에 따라붙는다.
KRV_POPUP_RE = re.compile(r"<div[^>]*class=D2\b.*?</div>", re.S | re.I)
# 각주 표시는 두 벌이다 — 관주는 `ㄱ)`, 난하주는 `4)` 처럼 **숫자** 다. 초판은 앞의
# 한 벌만 지웠고, 그 결과 `jn-1:14` 이 "은혜와 4)진리가 충만하더라" 로 시드에 앉았다
# (`V20260821030000` 에 그대로 실려 배포됨). 숫자 표시를 빼먹으면 각주 번호가 성경
# 본문인 척 화면에 뜬다.
KRV_FOOTMARK_RE = re.compile(r"(?:[ㄱ-ㅎ]|\d+)\)")


def fetch_chapter_krv(book: str, chapter: int) -> dict[tuple[int, int], str]:
    """대한성서공회 개역개정 장 페이지에서 절 텍스트를 축자 추출. 절 단위는 언제나 1절."""
    url = (
        "https://www.bskorea.or.kr/bible/korbibReadpage.php"
        f"?version=GAE&book={book}&chap={chapter}"
    )
    raw = subprocess.run(
        ["curl", "-sL", "-A", UA, url], capture_output=True, timeout=90
    ).stdout.decode("utf-8", "replace")
    i = raw.find('class="bible_read"')
    body = raw[i:] if i > 0 else raw
    for pat in (KRV_SCRIPT_RE, KRV_POPUP_RE, KRV_TITLE_RE):
        body = pat.sub("", body)

    marks = list(KRV_NUM_RE.finditer(body))
    out: dict[tuple[int, int], str] = {}
    for k, mk in enumerate(marks):
        # 마지막 절도 다음 <div 에서 끊는다 — KLB 쪽 ruth-4:18 오염과 같은 함정이다.
        if k + 1 < len(marks):
            end = marks[k + 1].start()
        else:
            nxt = re.compile(r"<div\b").search(body, mk.end())
            end = nxt.start() if nxt else len(body)
        seg = clean(KRV_FOOTMARK_RE.sub("", TAG_RE.sub("", body[mk.end(): end])))
        if seg:
            out[(int(mk.group(1)), int(mk.group(1)))] = seg
    return out


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
        seg = clean(TAG_RE.sub("", body[m.end(): end]))
        if not seg:
            continue
        key = (int(m.group(3)), int(m.group(4) or m.group(3)))
        out[key] = (out.get(key, "") + " " + seg).strip()
    return out


# ------------------------------------------------------------------- 출처표
# `translation` 라벨 값 -> 그 라벨이 가리키는 실제 번역본 수거 경로.
# 라벨마다 출처가 달라야 검사가 "라벨이 맞느냐" 를 물을 수 있다. 하나뿐이면
# 물을 수 있는 건 "KLB 냐" 뿐이고, 그건 라벨과 무관한 질문이다.
SOURCES = {
    "modern": {
        "name": "현대인의 성경",
        "source": "BibleGateway KLB (Korean Living Bible, © 1985 Biblica)",
        "url": "https://www.biblegateway.com/passage/?search=<Book+Chapter>&version=KLB",
        "fixture": SCRIPTS / "klb_reference_hashes.json",
        "books": {b: v[0] for b, v in BOOKS.items()},
        "fetch": fetch_chapter,
    },
    "rev": {
        "name": "개역개정",
        "source": "대한성서공회 개역개정판(GAE)",
        "url": "https://www.bskorea.or.kr/bible/korbibReadpage.php?version=GAE&book=<code>&chap=<n>",
        "fixture": SCRIPTS / "krv_reference_hashes.json",
        "books": KRV_BOOKS,
        "fetch": fetch_chapter_krv,
    },
}


def assemble(units: dict, vs: int, ve: int) -> tuple[str, int] | None:
    """행의 절 범위를 덮는 번역본 단위들을 이어 붙인다. (본문, 실제 끝 절).

    시드 행은 여러 절을 한 행에 담기도 한다(`ps-88:1` 이 1-3절). 번역본 쪽 단위와
    1:1 이 아니므로 범위를 덮는 단위를 모아 잇는다. 마지막 단위가 행의 끝 절을
    넘어가면(KLB 가 38-39절을 합쳐 번역하는 식) 그 사실을 두 번째 값으로 돌려준다 —
    그건 대조 불가이지 불일치가 아니다.
    """
    sel = sorted((s, e, x) for (s, e), x in units.items() if s <= ve and e >= vs)
    if not sel or min(s for s, _, _ in sel) != vs:
        return None
    return " ".join(x for _, _, x in sel), max(e for _, e, _ in sel)


def build(label: str, rows: list[dict]) -> dict | None:
    src = SOURCES[label]
    chapters = sorted({(r["book"], r["chapter"]) for r in rows})
    unknown = sorted({b for b, _ in chapters if b not in src["books"]})
    if unknown:
        print(f"[BLOCKED] {label}: 책 매핑에 없는 book_code {unknown} — 매핑을 추가하라")
        return None

    refs: dict[str, dict] = {}
    for book, chapter in chapters:
        units = src["fetch"](src["books"][book], chapter)
        if not units:
            print(f"[BLOCKED] {label}: {book} {chapter} 에서 절을 하나도 못 읽었다")
            return None
        kept = 0
        for r in rows:
            if (r["book"], r["chapter"]) != (book, chapter):
                continue
            got = assemble(units, r["vs"], r["ve"])
            if got is None:
                continue
            text, unit_end = got
            refs[r["ref"]] = {
                "sha256": digest(text), "unit_end": unit_end, "chars": len(text),
            }
            kept += 1
        print(f"  {label} {book} {chapter}: 단위 {len(units)}개 · 행 {kept}건", file=sys.stderr)

    return {
        "_source": src["source"],
        "_url": src["url"],
        "_normalize": "활자따옴표→ASCII · 양끝 따옴표 제거 · 연속공백 1칸",
        "_note": (
            "본문 자체는 담지 않는다(저작권). sha256 과 절 범위만 담는다. "
            "해시는 **행의 절 범위를 덮는 단위를 이어 붙인 본문** 의 것이라 "
            "시드 행 범위가 바뀌면 다시 수거해야 한다."
        ),
        "refs": refs,
    }


def refresh() -> int:
    rows = seeded_rows()
    if not rows:
        print("[BLOCKED] 마이그레이션에서 시드 행을 하나도 못 읽었다")
        return 126
    for label, src in SOURCES.items():
        fixture = build(label, rows)
        if fixture is None:
            return 126
        src["fixture"].write_text(
            json.dumps(fixture, ensure_ascii=False, indent=1, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(f"{src['name']} 기준 해시 {len(fixture['refs'])}건 -> {src['fixture'].name}")
    return 0


# ---------------------------------------------------------------------- 검사
def load(label: str) -> dict | None:
    path = SOURCES[label]["fixture"]
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))["refs"]


def main() -> int:
    if "--refresh" in sys.argv:
        return refresh()

    fixtures = {label: load(label) for label in SOURCES}
    missing = [SOURCES[k]["fixture"].name for k, v in fixtures.items() if v is None]
    if missing:
        print(f"[BLOCKED] 기준 해시 파일이 없다 ({', '.join(missing)}) — 판정 불가")
        return 126

    rows = seeded_rows()
    if not rows:
        print("[BLOCKED] 마이그레이션에서 시드 행을 하나도 못 읽었다 — 판정 불가")
        return 126

    lines, npass, nfail, nblock = [], 0, 0, 0
    for r in sorted(rows, key=lambda x: (x["book"], x["chapter"], x["vs"])):
        label = r["trans"]
        if label not in SOURCES:
            nblock += 1
            lines.append(f"  [BLOCKED] {r['ref']} translation='{label}' — 대조할 원문 출처가 없다")
            continue
        name = SOURCES[label]["name"]
        base = fixtures[label].get(r["ref"])
        if base is None:
            nblock += 1
            lines.append(f"  [BLOCKED] {r['ref']} {name} 기준 해시 없음 — 대조 불가")
        elif base["unit_end"] != r["ve"]:
            nblock += 1
            lines.append(
                f"  [BLOCKED] {r['ref']} 절 범위가 번역본 단위와 다름 "
                f"(시드 {r['vs']}-{r['ve']} · {name} {r['vs']}-{base['unit_end']}) — 대조 불가"
            )
        elif digest(r["text"]) == base["sha256"]:
            npass += 1
            lines.append(f"  [PASS   ] {r['ref']} {name} 자구와 일치")
        else:
            nfail += 1
            # 라벨만 틀린 것과 어느 번역본도 아닌 것은 고치는 방법이 다르다.
            other = [
                SOURCES[k]["name"] for k, fx in fixtures.items()
                if k != label and (fx.get(r["ref"]) or {}).get("sha256") == digest(r["text"])
            ]
            why = f"{other[0]} 자구다 — 라벨만 틀렸다" if other else "어느 번역본과도 다르다"
            lines.append(
                f"  [FAIL   ] {r['ref']} translation='{label}' 인데 {why} "
                f"({r['file'].split()[0]})"
            )

    print(f"시드 행 {len(rows)}개 · 기준 해시 " + " · ".join(
        f"{SOURCES[k]['name']} {len(v)}건" for k, v in fixtures.items()) + "\n")
    print("\n".join(lines))
    print(f"\n--- PASS {npass} / FAIL {nfail} / BLOCKED {nblock} ---")
    print("  ⚠️ 이 검사의 주장 범위: 저장된 자구가 `translation` 라벨이 말하는")
    print("     번역본과 같으냐 까지다. 어느 번역본이어야 하는지(라이선스)도, 그")
    print("     본문이 화면에 열리는지도 재지 않는다. BLOCKED 는 '괜찮다' 가")
    print("     아니라 '아직 못 쟀다' 이다.")
    return 1 if nfail else 0


if __name__ == "__main__":
    sys.exit(main())

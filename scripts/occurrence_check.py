#!/usr/bin/env python3
"""낱말 출현 주장 전수 대조 — 서수·개수·목록.

`quote_sweep.py` 는 **인용된 자구**를, `verse_lines_check.py` 는 **절 본문 행**을 본다.
둘 다 못 보는 세 번째 종류가 있다: **정본을 세어서 하는 주장.**

    "'기생' 호칭의 **두 번째** 출현(수 2:1 · 6:17b · 6:22 · 6:25 · 약 2:25)"
    "'기생' 호칭 반복은 … **다섯 곳**에서 성립한다"

자구는 한 글자도 안 틀렸는데 **세는 것이 틀린다.** 실제로 두 번 났다:
`VERSES-RAHAB-GAE.md:21` 이 6:17b 를 "다섯 번째"라 적었고(실제 두 번째),
그 앞 판은 수 2:1 을 빠뜨려 "네 곳"이라 적었다(실제 다섯 곳). **둘 다 사람이 읽어서
잡았다** — 기계가 못 잡는 자리에 결함이 남는다는 뜻이라 도구로 옮긴다.

검사하는 것:

1. **목록의 실재** — 나열한 절이 정본에 있고 그 낱말을 실제로 담는가.
2. **목록의 전수성** — 정본에서 그 낱말을 담은 절 중 목록에 빠진 것이 있는가.
   같은 줄에 `히 11:31 없이도` 처럼 명시 제외가 있으면 그 절은 빼고 센다.
3. **서수** — `N 번째` 주장 옆에 강조(`**6:17b**`)된 절이 하나면 그 색인과 맞는가.
4. **개수** — `N 곳`·`N 회`·`N 번` 주장이 **그 주장 바로 뒤 나열**의 길이와 맞는가.
   한 줄에 주장이 여럿이면 각각 따로 본다.

못 잡는 것: 강조가 없거나 둘 이상이면 서수 검사를 건너뛴다(모호로 보고).
개수 주장 뒤에 나열이 없고 그 줄에 주장이 둘 이상이면 역시 건너뛴다(모호로 보고) —
어느 목록에 걸린 주장인지 기계가 정할 수 없다. 조용히 통과시키지 않는다.
낱말이 인용부호에 없으면 주장으로 보지 않는다.

    python3 scripts/occurrence_check.py docs/VERSES-RAHAB-GAE.md
"""

from __future__ import annotations

import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORPUS = ROOT / "docs" / "verses-rahab.txt"

# 약칭만 알던 초판은 "여호수아 2:1" 의 책을 못 읽고 **앞에 나온 다른 책을
# 물려받았다** — 실측에서 그 절이 `마 2:1` 로 잡혔다. 전체 이름을 먼저 건다.
FULL = {"여호수아": "수", "마태복음": "마", "야고보서": "약", "히브리서": "히",
        "사무엘하": "삼하", "누가복음": "눅", "룻기": "룻"}
BOOKS = "|".join(list(FULL) + ["수", "마", "약", "히", "삼하", "눅", "룻"])
REF = re.compile(rf"(?:({BOOKS})\s*)?(\d+:\d+[ab]?)")
# 인용부호로 감싼 짧은 낱말만 주장의 주어로 본다.
TOKEN = re.compile(r"['‘“\"「]([가-힣]{1,6})['’”\"」]")
ORDINAL = re.compile(r"(한|첫|두|둘|세|셋|네|넷|다섯|여섯|일곱|여덟|아홉|열)\s*번째")
# `다섯 곳` 뿐 아니라 `5회` · `다섯 번` 도 같은 주장이다. 초판은 `곳` 만 봤고,
# 정작 이 리포에서 가장 흔한 표기는 `5회` 였다 — 그래서 seed 의 주된 계수 주장이
# 한 건도 검사되지 않았다(rev.8 · 적대 채점 N1).
# 🚨 `번 더`·`회 더` 는 **반복 부사**이지 계수 주장이 아니다("이미 있는 것을 한 번 더
# 적고"). 걸러 내지 않으면 그런 줄이 앞뒤 절 표기를 나열로 끌어와 거짓 불일치를 낸다.
# 드러난 경위를 적어 둔다 — 이 오탐은 seed rev.13 이 앞 문단에 「수 6:25」를 적자
# `doc_book` 이 여호수아로 물려져 **그제서야** 맨 절 `2:9`·`2:1` 이 해석되며 떴다.
# 곧 이 함정은 rev.12 에도 있었고 **입력이 바뀌기 전까지 아무 줄도 붉어지지 않았다.**
COUNT = re.compile(
    r"(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|\d+)\s*(?:곳|회|번(?!째))(?!\s*더)")
EMPHASIZED = re.compile(rf"\*\*\s*(?:(?:{BOOKS})\s*)?(\d+:\d+[ab]?)\s*\*\*")
# 명시 제외. "히 11:31 없이도" 뿐 아니라 "마 1:5 에는 없다" 도 **나열이 아니라
# 부재 주장**이다. 후자를 나열로 읽으면 부재를 정확히 밝힌 줄이 불일치로 뜬다.
EXCLUDED = re.compile(
    rf"(?:({BOOKS})\s*)?(\d+:\d+[ab]?)\s*(?:에|에는|은|는|이|가)?\s*없")
# 절 **앞**에서 배제를 밝히는 어순도 있다: "배제장의 히 11:31 에도 1회 더 있다".
# 이것도 나열이 아니라 범위 밖 고지다 — 나열로 읽으면 정확한 줄이 누락으로 뜬다.
EXCLUDED_PRE = re.compile(rf"(?:배제|제외)[^:]{{0,8}}?({BOOKS})\s*(\d+:\d+[ab]?)")
# `**기생**` 처럼 강조로만 쓴 낱말도 주장의 주어다. 초판은 인용부호만 봤고,
# 그래서 `THEOLOGY-REFERENCES.md:517` 의 알려진 과소계수를 한 번도 못 봤다(rev.8).
BOLD_WORD = re.compile(r"\*\*([가-힣]{1,6})\*\*")

NUM = {"한": 1, "첫": 1, "두": 2, "둘": 2, "세": 3, "셋": 3, "네": 4, "넷": 4,
       "다섯": 5, "여섯": 6, "일곱": 7, "여덟": 8, "아홉": 9, "열": 10}


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFC", s)
    s = re.sub(r"</?[A-Za-z][^>]*>", "", s)
    s = re.sub(r"\*\*|\*|__|`|_", "", s)
    return re.sub(r"\s+", " ", s).strip()


def load_corpus() -> tuple[dict[str, str], set[str]]:
    """정본 순서를 보존한다 — 서수 주장의 기준이 파일 순서다.

    `## USED` / `## EXCLUDED` 두 구간을 구분해 돌려준다. **전수성의 기준은 USED 다** —
    배제장의 절은 미션이 안 쓰기로 한 절이라, 그것을 안 적었다고 누락이 아니다.
    초판은 구간을 무시하고 파일 전체를 기준으로 삼아, 배제를 정확히 밝힌 줄을
    "목록 누락 히 11:31" 로 띄웠다(rev.8 실측 오탐).
    """
    canon: dict[str, str] = {}
    used: set[str] = set()
    section = ""
    for line in CORPUS.read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            section = line[3:].strip()
        elif "\t" in line:
            ref, text = (normalize(x) for x in line.split("\t", 1))
            canon[ref] = text
            if section == "USED":
                used.add(ref)
    return canon, used


def num_of(word: str) -> int:
    return int(word) if word.isdigit() else NUM[word]


def refs_in(text: str, book: str | None = None) -> list[str]:
    """왼쪽에서 오른쪽으로 읽으며 책 접두를 상속한다 — "수 2:1 · 6:22" 의 6:22 는 수다.

    `book` 을 주면 그것을 초기 접두로 쓴다. 낱말 **앞**에서 책이 정해지고 뒤에는
    맨 절만 나열하는 문장("'기생'은 … 5회 나온다 — 2:1 · 6:17b …")이 흔한데,
    초판은 그 경우 절을 하나도 못 읽었다(rev.8 · 적대 채점 N1).
    """
    out = []
    for m in REF.finditer(text):
        if m.group(1):
            book = FULL.get(m.group(1), m.group(1))
        if book:
            out.append(f"{book} {m.group(2)}")
    return out


def check(target: Path) -> int:
    canon, used = load_corpus()
    claims = bad = ambiguous = 0
    doc_book: str | None = None  # 문서를 훑으며 마지막으로 언급된 책

    for lineno, raw in enumerate(target.read_text(encoding="utf-8").splitlines(), 1):
        norm = normalize(BOLD_WORD.sub(r"'\1'", raw))
        line_books = [FULL.get(b, b) for b, _ in REF.findall(norm) if b]
        tok_m = TOKEN.search(norm)
        if not tok_m:
            if line_books:
                doc_book = line_books[-1]
            continue
        token = tok_m.group(1)
        # 낱말 앞에서 마지막으로 언급된 책을 초기 접두로 물려준다. 그 줄에 없으면
        # **앞 줄들**에서 물려받는다 — "'기생'은 … 5회 나온다 — 2:1 · 6:17b …" 처럼
        # 문단 전체가 한 책을 다루며 맨 절만 나열하는 어순이 이 리포에서 가장 흔한데,
        # 줄 안만 보던 초판은 그런 줄에서 절을 하나도 못 읽었다(rev.8 · 적대 채점 N1).
        before = [FULL.get(b, b) for b, _ in REF.findall(norm[:tok_m.start()]) if b]
        seed_book = before[-1] if before else doc_book
        listed = refs_in(norm[tok_m.end():], seed_book)
        if line_books:
            doc_book = line_books[-1]
        if len(listed) < 2:
            continue
        # **세는 주장**만 대상이다. 계수도 서수도 없이 절 둘을 적은 줄(예: 시리즈
        # 헌장의 인물 표 — "마 1장 위치 1:5 · 본문 수 2 · 수 6:22-25")은 목록이
        # 아니라 범위 표기이고, 전수성을 요구하면 통째로 오탐이 된다(rev.8 실측).
        if not (COUNT.search(norm) or ORDINAL.search(norm) or len(listed) >= 3):
            continue

        # 정본에서 이 낱말을 담은 절 전부(파일 순서 유지).
        actual = [r for r, t in canon.items() if token in t]
        if not actual:
            continue
        claims += 1

        excl = {f"{FULL.get(b, b)} {n}".strip()
                for b, n in EXCLUDED.findall(norm) + EXCLUDED_PRE.findall(norm)}
        # 명시 제외된 절("히 11:31 없이도")은 나열이 아니다 — 빼지 않으면
        # 제외를 밝힌 정확한 줄이 개수 불일치로 뜬다(자체 실측 오탐).
        listed = [r for r in listed if r not in excl]
        # 전수성 기준: **채택장(USED)** + 그 줄이 실제로 적은 배제장 절.
        # 배제장 절을 끌어오는 것은 문서의 선택이지 의무가 아니다.
        expected = [r for r in actual
                    if r not in excl and (r in used or r in listed)]

        problems = []
        for r in listed:
            if r not in canon:
                problems.append(f"정본에 없는 절 {r}")
            elif token not in canon[r]:
                problems.append(f"{r} 에는 '{token}' 이(가) 없다")
        for r in expected:
            if r not in listed:
                problems.append(f"목록 누락 {r}")

        # 한 줄에 개수 주장이 여럿이면 **각 주장 바로 뒤의 나열**로만 대조한다.
        # 줄 전체 나열과 맞대면 `:517` 정정 줄(세 절 → 다섯 곳)처럼 두 목록이
        # 한 줄에 있는 곳이 통째로 불일치로 뜬다 — 실측 오탐(rev.8).
        cms = list(COUNT.finditer(norm, tok_m.end()))
        for i, c_m in enumerate(cms):
            stop = cms[i + 1].start() if i + 1 < len(cms) else len(norm)
            head = [FULL.get(b, b) for b, _ in REF.findall(norm[:c_m.end()]) if b]
            seg = [r for r in refs_in(norm[c_m.end():stop],
                                      head[-1] if head else seed_book)
                   if r not in excl]
            # 주장 뒤에 나열이 없으면 대조 불가다. 단 그 줄의 유일한 주장이면
            # 나열이 앞에 있는 어순("… 2:1 · 6:25 — 다섯 곳")이므로 줄 전체를 쓴다.
            if not seg:
                if len(cms) == 1:
                    seg = listed
                else:
                    ambiguous += 1
                    print(f"  ?? {target.name}:{lineno}  개수 '{c_m.group(0)}'"
                          f" — 뒤에 나열이 없어 대조 불가")
                    continue
            if num_of(c_m.group(1)) != len(seg):
                problems.append(f"개수 '{c_m.group(0)}' ≠ 나열 {len(seg)}개 {seg}")

        o_m = ORDINAL.search(norm)
        if o_m:
            emph = EMPHASIZED.findall(raw)
            hit = [r for r in listed if any(r.endswith(e) for e in emph)]
            if len(hit) != 1:
                ambiguous += 1
                print(f"  ?? {target.name}:{lineno}  서수 '{o_m.group(1)} 번째' — "
                      f"강조된 절이 {len(hit)}개라 대상 특정 불가")
            else:
                want, got = num_of(o_m.group(1)), listed.index(hit[0]) + 1
                if want != got:
                    problems.append(
                        f"서수 '{o_m.group(1)} 번째' ≠ {hit[0]} 의 실제 {got}번째")

        if problems:
            bad += 1
            print(f"\n  ✗ {target.name}:{lineno}  '{token}'")
            for p in problems:
                print(f"      {p}")
            print(f"      나열: {listed}")
            print(f"      정본: {actual}"
                  + (f" (제외 주장: {sorted(excl)})" if excl else ""))

    print(f"\n출현 주장 대조: 주장 {claims} · 불일치 {bad} · 모호 {ambiguous}")
    return 1 if bad else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    arg = Path(sys.argv[1])
    sys.exit(check(arg if arg.is_absolute() else ROOT / arg))

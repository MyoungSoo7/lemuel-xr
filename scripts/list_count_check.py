#!/usr/bin/env python3
"""문서가 **자기 나열을 세는 주장** 전수 대조 — 「N갈래」·「N키」·「N종」.

기존 세 도구가 못 보는 네 번째 종류다:

    `quote_sweep.py`      — 인용된 **자구**
    `verse_lines_check.py`— **절 본문 행**
    `occurrence_check.py` — **정본을 세어서** 하는 주장(절 목록·서수·개수)
    이 도구              — **이 문서 자신의 나열을 세어서** 하는 주장

`occurrence_check.py` 는 절 corpus 를 기준으로 삼는다. 그래서 정본에 절이 없는
주장, 곧 「배제 사유는 **네 갈래다**」 아래에 항목이 **다섯 개** 있는 경우를
원리적으로 못 본다 — 셀 대상이 정본이 아니라 **바로 아래 붙은 나열**이기 때문이다.

실제로 두 번 났다:

    `SEED-RAHAB.md:509`  rev.10 이 갈래 5 를 신설하고 「네 갈래」를 안 고쳤다
    `SEED-RAHAB.md:759`  R1 래치 표가 8행인데 「아래 **7키**를」 이라 적었다

둘 다 **적대적 채점자가 눈으로** 잡았다. 네 도구가 전부 초록이었다.

## 언제 주장으로 보는가 — 좁게 잡는다

계수 표기(`N갈래`·`N키`…)는 이 seed 에 **123곳** 있고 대부분은 *다른 데* 있는 것을
가리키는 상호참조다(「토큰 31종」·「트리거 8종」). 그것까지 아래 나열과 맞대면
19곳 중 16곳이 오탐이었다(실측). 그래서 **전방 참조가 자구로 드러난 것만** 본다:

1. 주장 줄이 **표 행이 아니어야** 한다 — 표 칸 안의 수치는 그 표를 세는 말이 아니다.
2. 그리고 다음 둘 중 하나:
   - **전방 지시어** — `아래`·`다음` 이 계수 앞에 있거나 줄이 `:` 로 끝난다.
   - **정의형 계수** — `…는 N갈래다` 처럼 계수에 서술격 조사 `다` 가 붙는다.
3. 바로 뒤(빈 줄과 이어지는 산문 2줄까지 허용)에 **표** 또는 **1. 로 시작하는
   번호 목록**이 와야 한다. 없으면 주장으로 보지 않는다.

## 후방 참조 — `합계 … (N건 중)`

나열 **뒤**에서 되짚는 계수도 같은 종류의 주장이다. 실제로 §8-1 의 수용기준 표가
「**합계 — PASS 2 · FAIL 6 · BLOCKED 4 (12건 중)**」이라 적은 채 표는 **13행**이었다.
전방 규칙으로는 원리적으로 못 본다 — 나열이 주장보다 **앞**에 있기 때문이다.
그래서 표 바로 뒤(산문 2줄까지)에 `합계` 와 `N건` 이 같이 있는 줄을 그 표의 행 수와
맞댄다. `합계` 라는 자구를 요구하는 이유는, 표 뒤 산문에 흔한 다른 계수(「9종이 여기
없다」)를 그 표의 총량으로 오독하지 않기 위해서다.

## 못 잡는 것

- 나열이 글머리표(`-`)뿐인 곳. 번호가 없으면 어디까지가 한 항목인지 기계가 못 정한다.
- 전방 지시어 없이 제목에 계수만 적은 곳(`#### 신규 트리거 5종`) — 그 계수가 아래
  표 전체를 세는지 부분집합을 세는지 자구로 갈리지 않는다. **조용히 통과시키지
  않으려고** `--verbose` 에서 건너뛴 줄을 전부 찍는다.

    python3 scripts/list_count_check.py docs/SEED-RAHAB.md
    python3 scripts/list_count_check.py --selftest
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

NUM = {"한": 1, "두": 2, "세": 3, "네": 4, "다섯": 5, "여섯": 6,
       "일곱": 7, "여덟": 8, "아홉": 9, "열": 10}
UNIT = "갈래|키|종|항목|문장|행|가지|묶음"
# 뒤에 오는 글자를 **명시 목록**으로 제한한다. `(?![가-힣])` 로 막으면 조사가 붙은
# 「8키**를**」이 통째로 안 잡히고(실측 — :759 를 이 규칙이 놓쳤다), 아무것도 안
# 막으면 「종**류**」·「행**위**」가 계수로 잡힌다.
TAIL = r"(?=[를을는은이가의도만과와다·,.\s:)」\]]|$)"
CLAIM = re.compile(rf"(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|\d+)\s*({UNIT}){TAIL}")
# 전방 지시어는 **계수보다 앞**에 있어야 한다 — 「아래 8키를」은 아래를 가리키지만
# 「위 8키 중」은 이미 지나간 표를 되짚는 말이라 다음 나열과 맞대면 오탐이다.
FORWARD = re.compile(r"(아래|다음)")
COPULA = re.compile(rf"({UNIT})다")

MARKUP = re.compile(r"\*\*|\*|`|__")
SEP_ROW = re.compile(r"^\|[\s\-:|]+\|$")
# 후방 참조. `합계` 라는 자구가 있어야 그 표의 총량 주장으로 본다.
TOTAL = re.compile(r"(\d+)\s*건")


def strip_markup(s: str) -> str:
    return MARKUP.sub("", s)


def num_of(w: str) -> int:
    return int(w) if w.isdigit() else NUM[w]


def structure_after(lines: list[str], i: int) -> tuple[str, int, int] | None:
    """i(0-based) 다음의 열거 구조. (종류, 항목 수, 1-based 줄) 또는 None."""
    j, prose = i + 1, 0
    while j < len(lines) and prose <= 2:
        s = lines[j].strip()
        if not s:
            j += 1
            continue
        if s.startswith("|"):
            rows, k = 0, j
            while k < len(lines) and lines[k].strip().startswith("|"):
                if not SEP_ROW.match(lines[k].strip()):
                    rows += 1
                k += 1
            return ("표", rows - 1, j + 1)  # 헤더 1행 제외
        if re.match(r"^\d+\.\s", s):
            nums, k = [], j
            while k < len(lines):
                m = re.match(r"^(\d+)\.\s", lines[k])
                if m:
                    nums.append(int(m.group(1)))
                elif lines[k].strip() and not lines[k].startswith((" ", "\t")):
                    break
                k += 1
            return ("번호 목록", max(nums) if nums else 0, j + 1)
        j += 1
        prose += 1
    return None


def table_before(lines: list[str], i: int) -> tuple[int, int] | None:
    """i(0-based) **앞**에 있는 표. (항목 수, 1-based 줄) 또는 None.

    사이에 산문이 2줄을 넘으면 그 표를 되짚는 말로 보지 않는다 — 전방 규칙과 같은
    허용치를 쓴다.
    """
    j, prose = i - 1, 0
    while j >= 0 and prose <= 2:
        s = lines[j].strip()
        if not s:
            j -= 1
            continue
        if s.startswith("|"):
            rows, k = 0, j
            while k >= 0 and lines[k].strip().startswith("|"):
                if not SEP_ROW.match(lines[k].strip()):
                    rows += 1
                k -= 1
            return (rows - 1, k + 2)  # 헤더 1행 제외
        j -= 1
        prose += 1
    return None


def check(name: str, text: str, verbose: bool = False) -> tuple[int, int, int]:
    lines = text.split("\n")
    claims = bad = skipped = 0

    # ── 후방 참조: 표 바로 뒤의 `합계 … N건` ──
    for i, raw in enumerate(lines):
        line = strip_markup(raw)
        if "합계" not in line or line.strip().startswith("|"):
            continue
        m = TOTAL.search(line)
        if not m:
            continue
        tb = table_before(lines, i)
        if tb is None:
            skipped += 1
            if verbose:
                print(f"  ·· {name}:{i+1}  합계 '{m.group(0)}' — 바로 앞에 표가 없다")
            continue
        rows, at = tb
        claims += 1
        if int(m.group(1)) != rows:
            bad += 1
            print(f"\n  ✗ {name}:{i+1}  합계 '{m.group(0)}' ≠ 바로 앞 표 {rows}행")
            print(f"      표  : {name}:{at}")
            print(f"      줄  : {line.strip()[:100]}")

    for i, raw in enumerate(lines):
        line = strip_markup(raw)
        ms = list(CLAIM.finditer(line))
        if not ms:
            continue
        if line.strip().startswith("|"):
            continue  # 표 칸 안의 수치는 그 표를 세는 말이 아니다
        st = structure_after(lines, i)
        if st is None:
            continue

        kind, n, at = st
        # 전방 참조가 자구로 드러난 주장만 본다.
        anchored = [m for m in ms
                    if FORWARD.search(line[:m.start()])
                    or line.rstrip().endswith(":")
                    or COPULA.match(line[m.start(2):])]
        if not anchored:
            skipped += 1
            if verbose:
                vals = [m.group(0) for m in ms]
                print(f"  ·· {name}:{i+1}  {vals} — 전방 지시어가 없어 "
                      f"{kind}({n}개)와 맞대지 않는다")
            continue

        claims += 1
        wrong = [m for m in anchored if num_of(m.group(1)) != n]
        if len(wrong) == len(anchored):
            bad += 1
            print(f"\n  ✗ {name}:{i+1}  "
                  f"{[m.group(0) for m in wrong]} ≠ 바로 아래 {kind} {n}개")
            print(f"      나열: {name}:{at}")
            print(f"      줄  : {line.strip()[:100]}")

    print(f"\n나열 계수 대조: 주장 {claims} · 불일치 {bad} · 미판정 {skipped}")
    return claims, bad, skipped


# ── 회귀 확인 ────────────────────────────────────────────────────────────────
# 실제로 났던 두 결함을 자구 그대로 넣어 **검출됨**을, 고친 자구는 **통과함**을 잰다.
# 도구가 무엇을 잡는지 말로만 적지 않는다.
FIXTURES = [
    ("네갈래-옛", 1, """배제 사유는 **네 갈래다.**

1. **헤렘** — 수 6:1-16
2. **전멸 소문** — 수 2:10
3. **자녀 사망 저주** — 수 6:26
4. **절단 불가** — 히 11:31
5. **맹세의 면책 조건** — 수 2:16
"""),
    ("다섯갈래-고침", 0, """배제 사유는 **다섯 갈래다.**

1. **헤렘** — 수 6:1-16
2. **전멸 소문** — 수 2:10
3. **자녀 사망 저주** — 수 6:26
4. **절단 불가** — 히 11:31
5. **맹세의 면책 조건** — 수 2:16
"""),
    ("7키-옛", 1, """아래 **7키**를 **값 그대로** 5개 Scene 전건에 싣는다:

| 키                          | 값     |
| --------------------------- | ------ |
| `post_crisis_render_policy` | `a`    |
| `post_crisis_latch_scope`   | `b`    |
| `crisis_card_position`      | `c`    |
| `id`                        | `d`    |
| `enforced_at`               | `e`    |
| `action`                    | `f`    |
| `persist_utterance`         | `g`    |
| `crisis_resource_ref`       | `h`    |
"""),
    ("8키-고침", 0, """아래 **8키**를 **값 그대로** 5개 Scene 전건에 싣는다:

| 키                          | 값     |
| --------------------------- | ------ |
| `post_crisis_render_policy` | `a`    |
| `post_crisis_latch_scope`   | `b`    |
| `crisis_card_position`      | `c`    |
| `id`                        | `d`    |
| `enforced_at`               | `e`    |
| `action`                    | `f`    |
| `persist_utterance`         | `g`    |
| `crisis_resource_ref`       | `h`    |
"""),
    # 오탐 회귀: 「위 N키」는 **이미 지나간** 표를 되짚는 말이다. 뒤 표와 맞대면 안 된다.
    ("위N키-오탐방지", 0, """⚠️ 위 8키 중 게이트가 보는 것은 하나뿐이다.

| 게이트 | 판정 |
| ------ | ---- |
| g2     | PASS |
"""),
    # 후방 참조: §8-1 이 실제로 「12건 중」이라 적은 채 표가 13행이었다.
    ("합계-옛", 1, """| AC   | 판정 |
| ---- | ---- |
| AC-1 | FAIL |
| AC-2 | PASS |
| AC-3 | PASS |

**합계 — PASS 2 · FAIL 1 (2건 중).**
"""),
    ("합계-고침", 0, """| AC   | 판정 |
| ---- | ---- |
| AC-1 | FAIL |
| AC-2 | PASS |
| AC-3 | PASS |

**합계 — PASS 2 · FAIL 1 (3건 중).**
"""),
]


def selftest() -> int:
    fails = []
    for name, want_bad, body in FIXTURES:
        print(f"\n─── {name} (기대 불일치 {want_bad}) ───")
        _, got, _ = check(name, body)
        if got != want_bad:
            fails.append(f"{name}: 기대 {want_bad} · 실제 {got}")
    print("\n" + "=" * 60)
    if fails:
        print("회귀 확인 FAIL:")
        for f in fails:
            print(f"  {f}")
        return 1
    print(f"회귀 확인 PASS — 픽스처 {len(FIXTURES)}건 전건 기대와 일치")
    return 0


if __name__ == "__main__":
    argv = sys.argv[1:]
    if argv[:1] == ["--selftest"]:
        sys.exit(selftest())
    verbose = "--verbose" in argv
    argv = [a for a in argv if not a.startswith("--")]
    if len(argv) != 1:
        sys.exit(__doc__)
    p = Path(argv[0])
    p = p if p.is_absolute() else ROOT / p
    _, bad, _ = check(p.name, p.read_text(encoding="utf-8"), verbose)
    sys.exit(1 if bad else 0)

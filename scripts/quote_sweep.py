#!/usr/bin/env python3
"""인용 전수 대조.

문서 안에서 **인용부호로 감싼 모든 문자열**을 뽑아, 지정한 정본들에 실재하는지 본다.
참조 표기("수 2:19" 같은)가 붙어 있는지는 보지 않는다 — SEED-RAHAB.md:476 이
"2:19" 라고만 쓴 줄에서 자구를 고쳤고, 참조 표기를 조건으로 걸었던 이전 스윕이
그 줄을 아예 대상에서 빼는 바람에 못 잡았다.

정본에 없는 조각은 자동 실패가 아니라 **사람이 판단할 목록**으로 낸다.
개정 사유 표는 옛 오류를 일부러 인용하기 때문이다.

**rev.2 (2026-08-06) — 채점자가 실측한 구멍 셋을 막았다.** 초판은 스스로
"인용부호 전체를 본다"고 적었으나 셋 다 사실이 아니었다:

1. `[^"\\n]+` 로 **한 줄 인용만** 봤다. 실측 269건 중 **21건이 멀티라인**이라
   통째로 미탐이었고, 히 11:31 전문(`SEED-RAHAB.md:305-306`)이 그 안에 있었다.
   → 줄 단위 스캔을 버리고 문서 전체에서 찾는다. 빈 줄은 넘지 않는다(문단 경계).
2. 곡선 따옴표 `“ ” ‘ ’` 를 아예 몰랐다. → 패턴에 넣었다.
3. `p in canon`(정본 5종을 이어 붙인 blob 부분문자열)이라 **절 오귀속을 원리적으로
   못 잡았다** — 2:19 의 자구를 2:21 이라 달아도 초록이었다. → 줄에 절 참조가 있으면
   그 절의 본문에 대조한다(`--strict-attribution` 없이 항상).

**아직 못 잡는 것** (고지하지 않으면 초판과 같은 거짓말이 된다):

- **의미를 바꾸는 앞머리 절단.** 생략부호 없이 앞뒤를 잘라도 부분문자열이라 통과한다.
  한정어를 떨어뜨리는 실패(헌장 하드룰 3 사례)가 정확히 이 형태다. 절 참조가 있으면
  절 경계 대조로 일부 걸리지만, 참조 없는 상위 문서 인용은 여전히 못 본다.
- **인용부호 없이 쓴 자구.** 그건 `verse_lines_check.py` 몫이다.
- 정본 blob 부분문자열이므로 **문서가 인용한 조각이 정본의 다른 문맥에 우연히
  존재하면** 통과한다.

    python3 scripts/quote_sweep.py docs/SEED-RAHAB.md
"""

from __future__ import annotations

import bisect
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 정본. 성경 자구뿐 아니라 상위 문서도 넣는다 — 한정어 하나 떨어뜨리면
# 조항이 실제보다 넓거나 좁아지고, 그것도 자구 훼손이다.
CANON = [
    "docs/verses-rahab.txt",
    # 2026-08-06 추가. 자막은 이 파일에서만 인용한다고 규정해 놓고 **이 파일을 대조
    # 대상에 넣은 적이 없었다** — 규정과 검사 수단이 어긋나 있었다(rev.8 · 루브릭 N8).
    "docs/VERSES-RAHAB-GAE.md",
    "docs/SERIES-GRACE.md",
    "docs/FUNCTIONAL-SPEC.md",
    "docs/safety-guidelines.md",
    "docs/THEOLOGY-REFERENCES.md",
    # 2026-08-06 추가(rev.9). 라합 seed 가 룻의 **계약 문장**을 인용하기 시작했는데
    # (R1 래치 7키 · "키 존재가 아니라 값이 계약이다") 그 원문이 대조 대상 밖이었다.
    # 선례를 인용하면서 선례 파일을 정본에 안 넣는 것이 `VERSES-RAHAB-GAE.md` 와 같은 구멍이다.
    "content/ruth/README.md",
]

VERSE_TABLE = "docs/verses-rahab.txt"

# 인용부호. 곡선 따옴표를 빠뜨렸던 것이 rev.1 의 구멍 중 하나다.
# 안쪽에서 줄바꿈은 허용하되 **빈 줄은 넘지 않는다** — 짝이 안 맞는 따옴표가
# 문서 절반을 삼키는 것을 막는 경계다.
INNER = r"(?:[^{close}\n]|\n(?!\s*\n))+?"
QUOTE_PATTERNS = [
    re.compile('"(' + INNER.format(close='"') + ')"'),
    re.compile('“(' + INNER.format(close='”') + ')”'),
    re.compile('‘(' + INNER.format(close='’') + ')’'),
    re.compile("「(" + INNER.format(close="」") + ")」"),
    re.compile("«(" + INNER.format(close="»") + ")»"),
]

# 자구가 아닌 것이 인용부호에 들어가는 경우가 많다: 파일명, 코드, 라벨.
# 대조 대상에서 빼되 뺀 것을 보고해 — 조용히 줄어들면 그게 다음 :476 이다.
SKIP = re.compile(
    r"^[\x00-\x7f]*$"          # 순 ASCII (식별자·경로·코드)
    r"|^[a-z0-9_]+$"
    r"|\.(md|txt|py|yml|yaml|json)$"
)

# 절 참조. "수 2:19" 뿐 아니라 맨 "2:19" 도 잡는다 — :476 이 후자였다.
BOOKS = "수|마|약|히|삼하|눅|룻"
VERSE_REF = re.compile(rf"(?:{BOOKS})\s*\d+:\d+|\b\d+:\d+[ab]?")

# 성경체 종결. 참조 없이 자구만 인용한 줄을 건지는 두 번째 그물.
SCRIPTURE_TAIL = re.compile(
    r"(하니라|이니라|하더라|더라|하라|리라|이요|나니|으므로|았더라|였더라|"
    r"하시니|하매|하고|이라|았고|하였|주시|받은)$"
)


def is_scripture_shaped(frag: str, context: str) -> bool:
    """자구일 가능성이 있는가 — 있으면 반드시 해소해야 하는 1군으로 올린다."""
    if len(frag) < 8:
        return False
    if not re.search(r"[가-힣]", frag):
        return False
    return bool(VERSE_REF.search(context)) or bool(SCRIPTURE_TAIL.search(frag))


def normalize(s: str) -> str:
    """마크다운 강조·공백·유니코드 표기 차이를 걷어낸다."""
    s = unicodedata.normalize("NFC", s)
    # 문서가 밑줄 강조에 <U>…</U> 를 쓴다. 걷지 않으면 정확한 인용이
    # 불일치로 뜨고, 그 잡음이 진짜 훼손을 덮는다.
    s = re.sub(r"</?[A-Za-z][^>]*>", "", s)
    s = re.sub(r"\*\*|\*|__|`|_", "", s)
    s = s.replace("​", "")
    # 멀티라인 인용은 줄바꿈 자리에 마크다운 인용부호(>)가 낀다.
    s = re.sub(r"\n\s*>?\s*", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def load_canon() -> tuple[str, dict[str, str], list[str]]:
    """정본 blob 과, 절 단위 대조를 위한 `참조 → 본문` 표를 함께 만든다."""
    blobs, missing = [], []
    verses: dict[str, str] = {}
    for rel in CANON:
        p = ROOT / rel
        if not p.exists():
            missing.append(rel)
            continue
        raw = p.read_text(encoding="utf-8")
        blobs.append(normalize(raw))
        if rel == VERSE_TABLE:
            for line in raw.splitlines():
                if "\t" in line:
                    ref, text = (normalize(x) for x in line.split("\t", 1))
                    verses[ref] = text
                    # 접두 없는 "6:17b" 로도 찾을 수 있어야 한다.
                    verses.setdefault(ref.split(" ", 1)[1], text)
    if missing:
        print(f"⚠️  정본 누락: {', '.join(missing)}", file=sys.stderr)
    return "\n".join(blobs), verses, missing


def line_starts(text: str) -> list[int]:
    starts, pos = [0], text.find("\n")
    while pos != -1:
        starts.append(pos + 1)
        pos = text.find("\n", pos + 1)
    return starts


def sweep(target: Path) -> int:
    canon, verses, _ = load_canon()
    text = target.read_text(encoding="utf-8")
    starts = line_starts(text)

    checked = hits = multiline = 0
    misses: list[tuple[int, str, bool, str]] = []
    skipped: list[tuple[int, str]] = []
    seen: set[tuple[int, str]] = set()

    for pat in QUOTE_PATTERNS:
        for m in pat.finditer(text):
            frag = m.group(1)
            lineno = bisect.bisect_right(starts, m.start())
            norm = normalize(frag)
            if not norm or (lineno, norm) in seen:
                continue
            seen.add((lineno, norm))
            if SKIP.search(norm):
                skipped.append((lineno, norm))
                continue
            checked += 1
            if "\n" in frag:
                multiline += 1

            # 인용 앞머리(같은 줄 왼쪽)와 인용문 자체에서 참조를 찾는다.
            head = text[starts[lineno - 1]:m.start()]
            context = normalize(head) + " " + norm

            # 절 참조가 붙어 있으면 **그 절**에 대조한다. blob 부분문자열만 보면
            # 2:19 의 자구를 2:21 이라 달아도 통과한다 — 오귀속이 안 잡힌다.
            ref_m = VERSE_REF.search(normalize(head))
            scope, scope_name = canon, ""
            if ref_m:
                ref = re.sub(r"\s+", " ", ref_m.group(0)).strip()
                if ref in verses:
                    scope, scope_name = verses[ref], ref

            parts = [p.strip() for p in re.split(r"…|\.\.\.", norm) if p.strip()]
            bad = [p for p in parts if p not in scope]
            # 절 경계에서 틀렸다면, 정본 어딘가에는 있는지도 본다 —
            # "오귀속"과 "아예 없는 자구"는 다른 결함이다.
            if bad and scope_name:
                elsewhere = [p for p in bad if p in canon]
                note = (f"[{scope_name} 아님"
                        + (f" · 정본 다른 곳에는 있음" if elsewhere else "")
                        + "]")
            else:
                note = ""

            if not bad:
                hits += 1
            else:
                misses.append((lineno, " ⟂ ".join(bad),
                               is_scripture_shaped(norm, context), note))

    tier_a = [(n, f, x) for n, f, a, x in misses if a]
    tier_b = [(n, f, x) for n, f, a, x in misses if not a]

    print(f"대조 {checked}건 (멀티라인 {multiline}건 포함) · "
          f"정본 실재 {hits}건 · 판단 필요 {len(misses)}건")
    print(f"(자구 아님으로 제외 {len(skipped)}건)")

    # 1군은 절 참조가 붙어 있거나 성경체로 끝난다. 자구 훼손은 여기서 나온다.
    if tier_a:
        print(f"\n━━ 1군 · 자구 가능성 있음 · 전건 해소 필수 ({len(tier_a)}) ━━")
        for lineno, frag, note in tier_a:
            print(f"  {target.name}:{lineno}  {note}{' ' if note else ''}{frag}")

    # 2군은 문서 자신의 표현·설계 용어. 버리지 않고 분리만 한다.
    if tier_b:
        print(f"\n── 2군 · 자체 표현으로 보임 · 훑어볼 것 ({len(tier_b)}) ──")
        for lineno, frag, note in tier_b:
            print(f"  {target.name}:{lineno}  {note}{' ' if note else ''}{frag}")

    return 1 if tier_a else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(sweep(Path(sys.argv[1]) if Path(sys.argv[1]).is_absolute()
                   else ROOT / sys.argv[1]))

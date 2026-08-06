#!/usr/bin/env python3
"""절 목록 행 전수 대조.

`VERSES-RAHAB-GAE.md` 처럼 절을 **인용부호 없이 통째로 나열**하는 문서용.
`quote_sweep.py` 는 인용부호를 단서로 삼으므로 이런 행을 보지 못한다.
자막은 이 목록에서 인용되므로, 여기가 틀리면 하위 문서를 아무리 다듬어도 소용없다.

책 접두가 없는 형태("6:17b 기생 라합과…")도 잡는다 — 접두를 요구했더니
34행 중 3행만 검사되고 나머지가 조용히 빠졌다.

**정확한 대상은 절 목록 문서다.** 산문 문서(SEED-RAHAB.md)에 돌리면 줄바꿈으로 잘린
문단 몇 개가 목록처럼 보여 오탐이 남는다. 오탐은 눈으로 걷어내면 되지만 미탐은 안 되므로
경계를 느슨한 쪽에 뒀다. 산문의 인용 검사는 `quote_sweep.py` 가 맡는다.

    python3 scripts/verse_lines_check.py docs/VERSES-RAHAB-GAE.md
"""

from __future__ import annotations

import collections
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORPUS = ROOT / "docs" / "verses-rahab.txt"

# rev.2 (2026-08-06): 마크다운 표 칸(`| 6:17b 기생… |`)·인용블록(`> - 6:17 …`)·목록
# 머리(`- `)를 **접두로 허용**한다. 초판은 절 참조가 행 첫 칸에 있는 줄만 봤다.
#
# 🚨 rev.2 초판 주석이 "`^` 앵커를 풀었다"고 적었는데 **거짓이다.** 앵커는 그대로 있고
# 접두 문자 집합만 넓혔다. 그리고 이 완화가 `VERSES-RAHAB-GAE.md:21` 을 잡는다고 적은 것도
# **거짓이다.** 실측: 그 줄은 `> — 라합…` 으로 시작해 `—` 가 접두 집합에 없어 여전히 매치되지
# 않고, **설령 매치돼도 못 잡는다** — :21 의 결함은 자구 불일치가 아니라 **서수 오산**
# ("다섯 번째" ↔ 실제 두 번째)이고, 이 도구는 절 본문만 대조하기 때문이다.
# 서수·개수 주장은 `occurrence_check.py` 가 맡는다. 고치지도 않은 것을 고쳤다고 적는 실패는
# 이 도구가 감시하려던 실패와 같은 종류다.
LEAD = r"^(?:\s*[>|\-*]\s*)*"
VERSE_LINE = re.compile(LEAD + r"((?:수|마|약|히)\s*)?(\d+:\d+[ab]?)\s+([가-힣].*?)\s*\|?\s*$")

# 절 참조를 문장 주어로 쓴 산문("수 2:15 는 사람이 줄에 매달려…")은 목록이 아니다.
# 조사로 이어지면 산문이다 — 정본 절머리는 조사로 시작하지 않는다(라합이·누구든지·믿음으로…).
# 긴 조사를 먼저 둔다 — "에만"이 "에"로 잘리면 경계가 안 맞아 산문이 목록으로 샌다.
PROSE_PARTICLE = re.compile(
    r"^(에서|에는|에만|에도|부터|까지|으로|이라|는|은|이|가|를|을|에|도|와|과|의|만|로)\b")


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFC", s)
    s = re.sub(r"</?[A-Za-z][^>]*>", "", s)
    s = re.sub(r"\*\*|\*|__|`|_", "", s)
    return re.sub(r"\s+", " ", s).strip()


def load_corpus() -> tuple[dict[str, str], dict[str, list[str]]]:
    canon: dict[str, str] = {}
    by_suffix: dict[str, list[str]] = collections.defaultdict(list)
    for line in CORPUS.read_text(encoding="utf-8").splitlines():
        if "\t" not in line:
            continue
        ref, text = (normalize(x) for x in line.split("\t", 1))
        canon[ref] = text
        by_suffix[ref.split(" ", 1)[1]].append(ref)
    return canon, by_suffix


def check(target: Path) -> int:
    canon, by_suffix = load_corpus()
    ok = bad = ambiguous = 0

    for lineno, raw in enumerate(target.read_text(encoding="utf-8").splitlines(), 1):
        m = VERSE_LINE.match(normalize(raw))
        if not m:
            continue
        book, num, text = m.groups()
        if PROSE_PARTICLE.match(text):
            continue
        refs = [normalize(book + num)] if book else by_suffix.get(num, [])
        refs = [r for r in refs if r in canon]

        if len(refs) != 1:
            ambiguous += 1
            print(f"  ?? {target.name}:{lineno}  {num} → 후보 {refs}")
        elif canon[refs[0]] == text:
            ok += 1
        else:
            bad += 1
            print(f"\n  ✗ {target.name}:{lineno}  {refs[0]}")
            print(f"      문서: {text[:100]}")
            print(f"      정본: {canon[refs[0]][:100]}")

    print(f"\n절 행 대조: 일치 {ok} · 불일치 {bad} · 모호 {ambiguous}")
    return 1 if (bad or ambiguous) else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    arg = Path(sys.argv[1])
    sys.exit(check(arg if arg.is_absolute() else ROOT / arg))

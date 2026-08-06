#!/usr/bin/env python3
"""룻(Theme 19) 생체·시선 필드 부재 검사 — §8-1 AC 12 집행기.

⚠️ 원래 이 줄은 근거를 `SEED-RUTH.md §8-1` 로 적었으나 그 파일은 저장소에 없다
(2026-08-06 확인). 다만 이 검사는 상류 문자열 사본에 기대지 않고 *필드 부재* 를
직접 재므로, 정본 부재가 이 판정의 유효성을 깎지는 않는다.

근거: `docs/safety-guidelines.md:52` 가 생체·시선 계측 필드를 금지한다.

**rev.3 판은 오탐과 미탐을 동시에 냈다 (재채점 T7).** JSON 덤프 전체를 부분문자열
검색해서 `gaze` 가 `turning_gaze_animation_only` 같은 정상 연출 키에 걸리고(오탐),
정작 금지된 **한글 표현**(시선추적·심박·뇌파)은 목록에 없어 안 걸렸다(미탐).

그래서 이 검사기는 두 가지를 지킨다:

1. **값만 본다.** `yaml_values` 로 스칼라 값 문자열만 평탄화한다 — 키 이름과 주석은
   대상이 아니다. 연출 키 이름에 `gaze` 가 들어가는 것은 계측이 아니다.
2. **영문은 단어경계로, 한글은 그대로.** 한국어에는 `\b` 가 의미 있게 걸리지 않으므로
   토큰 종류에 따라 다른 규칙을 쓴다.

grep 금지 — `scripts/newchar_gates.py:20`.

rc: 0=PASS · 1=FAIL · ≥126 은 판정이 아니라 실행 실패.
"""

from __future__ import annotations

import os
import re
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from newchar_gates import yaml_values  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(REPO, "content", "ruth")

# 한글 토큰 — 그대로 부분문자열 검사
KO_TOKENS = ["시선추적", "시선 추적", "심박", "뇌파", "안구추적", "안구 추적", "생체신호", "생체 신호"]

# 영문 토큰 — 단어경계로 검사. `turning_gaze_animation_only` 같은 연출 표현을 잡지 않는다.
EN_PATTERNS = [
    re.compile(r"\bgaze\b", re.I),
    re.compile(r"\beye[_\- ]?track\w*\b", re.I),
    re.compile(r"\bheart[_\- ]?rate\b", re.I),
    re.compile(r"\bEEG\b"),
    re.compile(r"\bbiometric\w*\b", re.I),
    re.compile(r"\bpupil(?:lometry)?\b", re.I),
    re.compile(r"\bgalvanic\b", re.I),
]


# 비사용 선언 면제 — `safety.forbidden-tokens` 스캐너가 양보 부정을 면제하는 것과 같은 형식이다
# (`ForbiddenTokenScanner.CONCESSIVE_NEGATION`, LOOKAHEAD=12). 콘텐츠에는 "생체 신호를 쓰지
# 않는다" 처럼 **금지를 지키고 있음을 적은 주석**이 있고, 그것을 위반으로 세면 규율을 적을수록
# 검사가 빨개진다. 면제는 하되 **조용히 넘기지 않는다** — 무엇을 봐줬는지 항상 출력한다.
NON_USE = re.compile(r"(?:쓰지|사용하지|수집하지|측정하지|기록하지)\s?않|없다|없음|금지|미사용")
LOOKAHEAD = 24


def _excused(value: str, at: int) -> bool:
    return bool(NON_USE.search(value[at : at + LOOKAHEAD]))


def main() -> int:
    hits: list[str] = []
    excused: list[str] = []
    files = sorted(f for f in os.listdir(CONTENT) if f.endswith(".yml"))
    if not files:
        # 빈 결과는 PASS 가 아니다 — 대상이 없으면 아무것도 재지 않은 것이다.
        print(f"실행 실패: 검사 대상 yml 0건 ({CONTENT})", file=sys.stderr)
        return 126

    for name in files:
        with open(os.path.join(CONTENT, name), encoding="utf-8") as fh:
            doc = yaml.safe_load(fh)
        for value in yaml_values(doc):
            for tok in KO_TOKENS:
                at = value.find(tok)
                if at < 0:
                    continue
                rec = f"{name}: 한글 계측 토큰 {tok!r} — {value[:70]!r}"
                (excused if _excused(value, at + len(tok)) else hits).append(rec)
            for pat in EN_PATTERNS:
                m = pat.search(value)
                if not m:
                    continue
                rec = f"{name}: 영문 계측 토큰 /{pat.pattern}/ — {value[:70]!r}"
                (excused if _excused(value, m.end()) else hits).append(rec)

    for e in excused:
        print(f"면제  비사용 선언 — {e}")

    if hits:
        print("FAIL  AC 12 생체·시선 필드 부재")
        for h in hits:
            print(f"        {h}")
        print(f"--- 파일 {len(files)} / 위반 {len(hits)} ---")
        return 1
    print("PASS  AC 12 생체·시선 필드 부재")
    print(f"--- 파일 {len(files)} / 위반 0 ---")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"실행 실패: {type(exc).__name__}: {exc}", file=sys.stderr)
        sys.exit(126)

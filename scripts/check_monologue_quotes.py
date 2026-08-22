#!/usr/bin/env python3
"""프론트 모놀로그 성경 인용 게이트.

`frontend/src/lib/content/*-monologues.ts` 의 인용이 개역개정 정본과 맞는지 잰다.
정본은 `docs/verses-monologues-gae.txt` (대한성서공회 GAE 기계 파싱).

왜 이 파일이 있었나 (2026-08-21 신설)
    DB 성경 본문은 2026-07-18 교정 마이그레이션(`V20260718004903`) 이후 원문 대조
    체계 안에 있었다. 그런데 **프론트 문자열은 그 체계 밖이었다** — 그날 전수 대조
    시점까지 이 네 파일을 읽는 스크립트가 리포에 하나도 없었고, 32건 중 16건이
    원문과 달랐다(어미 변형 10 · 무표시 중간절단 5 · 원문에 없는 쉼표 1).
    틀렸다고 확인된 적이 없었던 게 아니라 **한 번도 대조된 적이 없었다.**

왜 다시 쓰였나 (2026-08-21, 모놀로그 API 전환)
    그 32건을 만든 근본 원인은 성경 자구가 **두 벌**(DB + 프론트 상수) 이었다는 것이다.
    그래서 프론트에서 자구를 걷어내고 `/api/scripture` 응답에서 받게 바꿨다
    (`scripture-quote.ts`). 소스에는 이제 자구 대신 `q("시 23:1", ["ps-23:1", 0, 6])`
    처럼 **참조 + 낱말 인덱스** 만 남는다.

    그 순간 이 게이트의 원래 질문("프론트 자구가 정본과 같은가")은 **구조적으로 실패할
    수 없는 질문** 이 됐다. 프론트에 잴 자구가 없기 때문이다. 실제로 전환 직후 이
    스크립트는 주석에 남은 인용 4건만 보고 "전건 통과" 를 냈다 — 32건을 지키던 게이트가
    4건짜리로 쪼그라든 것을 아무도 알 수 없는 초록이었다. **그 초록이 거짓말이라서**
    질문을 다음 넷으로 바꿔 다시 썼다.

무엇을 재는가
    A. 해석       모든 `q()` 가 시드 자구로 실제로 풀리는가 (인덱스 범위·lastWordChars).
                  실패하면 `resolveMonologue` 가 null 을 돌려 **문단이 통째로 사라진다**
                  — 한 문장이 비는 게 아니라 화면에서 없어진다.
    B. cite↔ref   인용 표기(`"출 4:14~16"`)가 클립이 여는 참조를 실제로 덮는가.
                  덮지 않으면 독자는 읽지도 않은 절을 출처로 믿는다. 자구는 맞는데
                  출처만 틀린 이 형태는 사람 눈으로 거의 안 잡힌다.
    C. 시드↔정본  클립이 여는 참조의 **시드 자구** 가 개역개정 정본과 축자로 같은가.
                  프론트가 읽는 것은 이제 시드이므로, 축자 대조의 대상도 시드다.
    D. 하드코딩   소스에 `"자구"(참조)` 형태의 성경 자구가 다시 박히지 않았는가.
                  **주석 안은 허용** 이고(신학 근거를 적는 자리다) 정본 대조만 한다.
                  코드 안이면 실패다 — 두 벌 상태로 되돌아간 것이기 때문이다.
    E. yml        시나리오·XR 콘텐츠 yml 이 **성경이라고 딱지 붙여 보여 주는 문자열** 이
                  정본과 같은가. 네 형태를 본다.
                    E1 라벨   `{ label: "…", scripture: ex-3:11 }` — 라벨이 그 절의 자구인가.
                    E2 인용   `"…" (출 3:12)` — 따옴표 인용 + 참조.
                    E3 자막   `text_ko: "<절 전문> (창 12:1)"` — XR 낭독 자막. 따옴표가
                              없을 뿐 화면은 이것을 성경으로 읽어 준다(TTS 가 그대로 읽는다).
                              키를 가리지 않는다 — yml 안의 모든 문자열 값이 대상이다.
                    E4 강조   `*자구* (출 3:12)` — 따옴표 대신 별표로 두른 인용.
    F. 표기       같은 책을 화면이 한 이름으로만 부르는가 — 인용 표기는 약칭이다
                  (`(창 12:1)`, `(창세기 12:1)` 아님). 자구를 재지 않는 유일한 축이다.
                  2026-08-21 이전에는 둘 다 아무도 안 쟀고, `moses.yml` 카드 라벨 5장 중
                  넷이 의역이었다("제가 누구이기에" / 출 3:11 은 "내가 누구이기에").
                  화면은 `scripture:` 딱지로 "이건 성경이다" 라고 말하면서 성경이 아닌
                  문장을 보여 주고 있었다. 같은 날 `david.yml`·`jesus.yml`·`joseph.yml` 의
                  인용 5건, `content/moses/scene3.yml` 카드 1장도 같은 이유로 고쳤다.

E3·E4 는 왜 나중에 붙었나 (2026-08-22)
    E1·E2 를 신설한 2026-08-21 시점에 낭독 자막 172건은 **미완화 노출로 docstring 에
    적어만 두고 재지 않았다.** 그때 손으로 전수 대조한 결과가 32건 불일치였고(자구 변형
    20 · 무표시 절단 5 · 구두점 7), 게이트를 그날 넓혔다면 즉시 빨강이었기 때문이다.
    빨강을 baseline 으로 덮는 것은 이 게이트가 고치려던 바로 그 거짓 초록이라, 노출로
    등재하고 다음 PR 로 미뤘다. 이 파일은 그 다음 PR 이다 — 32건을 개역개정으로
    정리하고(그중 5건은 애초에 인용이 아니어서 "참조/정서" 표기로 내렸다) 축을 넓혔다.
    대표적으로 `david/scene3.yml` 사울 대사는 개역한글 자구였고(개역개정은 "네가 가서
    저 블레셋 사람과 싸울 수 없으리니"), `david/scene5.yml` 은 삼상 17:45 에서 "네가
    모욕하는 이스라엘 군대의" 를 표시 없이 들어내고 있었다 — 모놀로그에서 고쳤던 바로
    그 절단이 낭독판에 그대로 남아 있었다.

정규화가 감춘 것을 F 가 도로 드러낸다 (2026-08-22)
    E 축을 만들면서 `BOOK_ALIAS` 로 "창세기" 와 "창" 을 하나로 접었다. 정본 표 키를
    하나로 잡으려던 것인데, 부작용으로 **같은 책이 화면에 두 이름으로 뜨는 것을 게이트가
    영영 못 보게** 됐다. 실측하니 콘텐츠 yml 안에서 창세기 45 / 창 11, 열왕기상 39 /
    왕상 18, 다니엘 20 / 단 8 처럼 일곱 책이 두 표기로 살고 있었다. 전건 통과였다.
    정규화는 *측정* 을 구하는 도구지 규율이 아니다 — 접은 자리에는 그 접음이 감춘 것을
    따로 재는 축이 있어야 한다. 그래서 표기를 약칭 162건으로 통일하고 F 를 붙였다.

    같은 날 두 가지가 더 나왔다. E3 이 키 화이트리스트라 목록 밖 키(`strong`)에 앉은
    같은 모양의 문자열 3건을 안 재고 있었고, E2 가 홑따옴표 인용을 안 받아 그 문체로
    쓰인 축자 인용 5건이 어느 축에도 안 잡히고 있었다. 둘 다 "무엇을 재는가" 가 아니라
    "어디를 보는가" 에서 난 구멍이다. 넓히자 `moses/scene3.yml` 의 시 103:14 가
    개역한글(“진토임을”)로 드러났다 — 전날 32건을 갚고도 남아 있던 33번째다.

C 가 `scripture_text_check.py` 와 겹치는 것에 대하여
    겹친다. 그쪽은 시드 201행 전체를 KRV/KLB 해시와 대조한다. 여기는 모놀로그가 여는
    30여 참조만, **해시가 아니라 자구로** 잰다. 해시는 "다르다" 만 말하고 어디가
    다른지는 안 말한다. 이 게이트가 남긴 실패 출력은 사람이 바로 고칠 수 있는 형태다.
    중복이지만 값이 다른 중복이라 남긴다.

판정 등급 (A · C · D 공통)
    MATCH          정본 해당 절의 축자 부분문자열                      통과
    ELLIPSIS       생략부호(…)로 이은 축자 조각이 순서대로 전부 있음   통과
    PUNCT_ONLY     구두점만 다름 (원문에 없는 쉼표 삽입 등)            실패
    SPLICE_NOMARK  생략부호 없이 중간을 잘라 이어 붙임 (자구는 원문)   실패
    ALTERED        어미·인칭·역본 혼합 등 자구 자체가 다름             실패
    NO_CANON       참조가 정본 표에 없다 — 판정 불가                   실패

    ELLIPSIS 를 통과로 두는 것은 *생략했다는 사실을 독자에게 표시했기 때문* 이다.
    `resolveQuote` 가 클립 사이를 " … " 로 잇는 것이 그 표시다. SPLICE_NOMARK 는
    자구가 전부 원문이어도 실패다 — 표시 없이 중간을 들어내면 한정어가 사라져 뜻이
    바뀌고(삼상 17:45 의 "네가 모욕하는 이스라엘 군대의"), 독자는 잘렸다는 것을 알
    길이 없다.

역본 정책
    개역개정 하나로만 잰다. `docs/CONTENT-WORKFLOW.md` 가 "MVP 는 개역개정 우선" 으로
    정해 두었고, 현대인의 성경은 `docs/BACKEND-ARCHITECTURE.md` 위험 #4 에서 라이선스
    분쟁 항목으로 잡혀 있다. 시드 쪽도 `translation='rev'` 행만 본다 —
    `fetchScripturePassage` 의 기본값이 그것이라, 다른 역본을 재면 화면과 다른 것을
    재게 된다(#84 가 정확히 그 사고였다).

이 게이트가 **못 잡는 것** (적지 않으면 초록이 거짓말이 된다)
    - **클립 구간이 뜻을 왜곡하는 경우.** 인덱스가 절 안에 있고 자구가 원문이면 통과다.
      "그 구간을 고른 것이 정당한가" 는 사람이 판정한다. 기계가 볼 수 있는 것은
      `docs/monologue-quotes.lock.txt` 의 diff 뿐이다.
    - **참조는 맞는데 절 선택이 부적절한 경우.** B 는 표기와 참조가 *서로* 맞는지만 본다.
      문맥상 엉뚱한 절을 끌어다 쓴 것은 사람의 몫이다.
    - **인용부호 없이 본문에 녹인 성경 자구.** 참조도 따옴표도 없으면 D 의 추출 대상
      밖이다. 그건 `verse_lines_check.py` 계열의 몫이다.
    - **"이 참조는 인용이 아니다" 라고 스스로 선언한 문자열.** 괄호 안 참조 뒤에
      한정어(참조·정서·배경·맥락·요약)가 붙으면 E3·E4 는 그 문자열을 재지 않는다.
      해설문을 성경 자구로 재면 고칠 수 없는 실패가 나기 때문이다(저자가 성경이라고
      주장한 적이 없다). 이 한정어는 게이트를 조용히 끄는 스위치가 아니다 — **화면에
      그대로 떠서** 사용자가 인용이 아님을 읽고, 요약줄이 그 건수를 항상 같이 낸다.
      그래도 남용은 가능하다: 틀린 자막에 "참조" 를 붙이면 빨강이 사라진다. 기계가
      막을 수 있는 것은 여기까지고, 그 다음은 리뷰가 센 건수를 보는 것이다.
    - **인용과 참조가 떨어져 있는 문장.** 추출은 따옴표(별표)와 참조가 *붙어* 있는
      것만 본다. 사이에 산문이 끼면 어느 축의 대상도 아니다. 2026-08-22 에 그런
      문장 둘을 찾아(`jesus.yml` `context_line` · `moses/scene6.yml` `strong`)
      참조를 인용 바로 뒤로 옮겨 재지게 했지만, 규칙이 아니라 자리 배치라서
      다음에 또 생길 수 있다. 잡는 방법은 지금 없다.
    - **정본 표 자체의 오류.** 표는 bskorea HTML 을 기계 파싱해 만든다. 그 파싱이
      틀리면 게이트는 틀린 기준으로 초록을 낸다. `--refresh` 로 재생성해 diff 가
      비는지가 유일한 확인 수단이다.

사용
    python3 scripts/check_monologue_quotes.py            # 판정 (네트워크 안 씀)
    python3 scripts/check_monologue_quotes.py --list     # 전 건 판정 출력
    python3 scripts/check_monologue_quotes.py --refresh  # 정본 표 재생성 (네트워크)

rc
    0  전건 통과
    1  실패 건 있음
    2  판정 불가 (정본 표 없음 · 소스 없음 · 시드 없음 · 인용 0건)
"""

from __future__ import annotations

import argparse
import re
import sys
import time
import unicodedata
import urllib.request
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
from scripture_text_check import seeded_rows  # noqa: E402  시드 파서를 두 벌 두지 않는다

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "frontend/src/lib/content"
SCEN_DIR = ROOT / "backend/src/main/resources/scenarios"
CONTENT_DIR = ROOT / "content"
CANON = ROOT / "docs/verses-monologues-gae.txt"

# 화면이 읽는 역본. `frontend/src/lib/api/content.ts` 의 기본값과 같아야 한다.
TRANSLATION = "rev"

# 시드 `book_code` → 정본 표·인용 표기의 한글 약칭.
# `docs/SCRIPTURE-REF-CONVENTION.md` 가 "같은 책이 두 약어로 살면 안 된다" 로 고정한
# 그 약어들이다(`mt` 가 아니라 `matt`). 모르는 코드는 조용히 넘기지 않고 판정불가로 낸다.
BOOK_KO = {
    "gen": "창", "ex": "출", "lev": "레", "num": "민", "deut": "신",
    "josh": "수", "judg": "삿", "ruth": "룻", "1sam": "삼상", "2sam": "삼하",
    "1kgs": "왕상", "2kgs": "왕하", "job": "욥", "ps": "시", "prov": "잠",
    "eccl": "전", "isa": "사", "jer": "렘", "ezek": "겔", "dan": "단",
    "matt": "마", "mk": "막", "lk": "눅", "jn": "요", "acts": "행",
    "rom": "롬", "1cor": "고전", "2cor": "고후", "gal": "갈", "eph": "엡",
    "phil": "빌", "col": "골", "heb": "히", "jas": "약", "rev": "계",
}

# ── 추출 ──────────────────────────────────────────────────────────────────
# `\n` 은 문단 경계 표식으로 남긴다 — 인용이 줄바꿈을 넘어 다음 문단을 삼키지
# 않게 하는 경계다. 리터럴 이어붙이기(`" +\n  "`) 는 그 전에 해치운다.
SENTINEL = "␄"
REF = r"\(([가-힣]+)\s?(\d+):(\d+)(?:[-~](\d+))?\)"
QUOTED = re.compile(
    r'["“”「](?P<text>[^"“”「」%s\n]{2,200})["“”」]\s*' % SENTINEL + REF
)

# yml 은 바깥 문자열이 큰따옴표라, 안쪽 인용을 홑따옴표로 다는 문체가 따로 있다
# ("'다 이루었다' (요 19:30)"). QUOTED 가 홑따옴표를 안 받아서 그 문체로 쓰인
# 축자 인용 5건이 어느 축에도 안 잡히고 있었다(2026-08-22 실측). TS 에는 이 꼴이
# 0건이고 거기서는 홑따옴표가 문자열 구분자라 오탐이 되므로, yml 에서만 넓힌다.
QUOTED_YML = re.compile(
    r"[\"“”「'](?P<text>[^\"“”「」'%s\n]{2,200})[\"“”」']\s*" % SENTINEL + REF
)
JOIN = re.compile(r"[\"']\s*\+\s*[\"']", re.S)

ELL = re.compile(r"…+|\.\.\.+|‥+")
FOOTNOTE = re.compile(r"\d+\)")          # bskorea 본문에 인라인으로 박히는 각주 번호
MD = re.compile(r"[*_`]")                # 인용 안에 들어간 마크다운 강조
QUOTE_CHARS = "\"“”„‟'‘’「」『』《》〈〉"
PUNCT = ",.!?;:·…‥、。~-–—"

CITE_RE = re.compile(r"^([가-힣]+)\s?(\d+):(\d+)(?:[-~](\d+))?$")

# 정경 이름의 전체 표기 → 약칭. 정본 표는 약칭 하나로 키를 잡아야 한다 — 안 그러면
# 같은 절이 두 이름으로 두 번 쌓이고, 한쪽만 고친 오자가 다른 쪽 키에 그대로 남는다.
#
# 2026-08-21 에 이 표를 넣은 것은 `content/**/*.yml` 이 "(창세기 12:1)" 로, TS 모놀로그와
# 시나리오 yml 이 "(창 12:1)" 로 서로 다르게 적고 있었기 때문이다. 그런데 정규화는
# *측정* 을 구한 것이지 화면을 구한 것이 아니다. 같은 책이 화면에 두 이름으로 뜨는 것을
# 이 표가 오히려 게이트에 안 보이게 만들었다 — 통과하니까 아무도 모른다.
# 2026-08-22 에 표기를 약칭으로 통일하고, 되돌아오는 것을 F 축이 막는다.
BOOK_ALIAS = {
    "창세기": "창", "출애굽기": "출", "레위기": "레", "민수기": "민", "신명기": "신",
    "여호수아": "수", "사사기": "삿", "룻기": "룻", "사무엘상": "삼상", "사무엘하": "삼하",
    "열왕기상": "왕상", "열왕기하": "왕하", "에스더": "에", "욥기": "욥", "시편": "시",
    "잠언": "잠", "전도서": "전", "이사야": "사", "예레미야": "렘", "에스겔": "겔",
    "다니엘": "단", "마태복음": "마", "마가복음": "막", "누가복음": "눅",
    "요한복음": "요", "사도행전": "행",
}


def norm_book(name: str) -> str:
    return BOOK_ALIAS.get(name, name)


# E1 은 `cards:` 아래 항목만 본다. 같은 `scripture:` 키가 두 계약을 지고 있기 때문이다.
#   cards   — "이 라벨이 곧 그 절의 자구" (moses.yml 다섯 변명 카드)
#   options — "이 선택지가 그 절과 관련" (jesus.yml 의 "길 — 어디로 가야 할지 모를 때",
#             `scripture: jn-14:6` 은 씬 전체가 여는 절이고 라벨은 설명문이다)
# 후자를 자구로 재면 설계상 인용이 아닌 문자열을 인용으로 몰아 실패시킨다.
CARDS_KEY = "cards"

# E3 은 키를 가리지 않는다 — yml 안의 *모든* 문자열 값 중 "…자구 (창 12:1)" 꼴로
# 끝나는 것을 잰다.
#
# 처음에는 키 화이트리스트였다(`text_ko` 등 다섯). 그 목록은 만든 사람이 아는 키만
# 담기 때문에, 같은 모양의 문자열이 목록 밖 키에 앉으면 그냥 안 재진다. 실제로
# `faith_tone_variants.strong` 3건이 그렇게 빠져 있었고(2026-08-22 실측),
# 셋 다 해설문 끝에 맨 인용 표기가 붙어 화면에서는 인용처럼 보였다.
# 목록을 늘리는 방식은 이 문제를 다시 부른다 — 다음에 생길 키는 목록에 없다.
#
# 전건으로 넓혀도 오탐이 늘지 않는 이유는 대상 조건이 키가 아니라 *모양* 이기 때문이다:
# 끝이 괄호 참조여야 하고(CAPTION_RE), 한정어가 붙으면 빠지고(참조·정서…),
# 통째로 따옴표에 싸이면 E2 가 가져간다. 넓힌 뒤 새로 걸린 것은 4건뿐이었다.

# `<자구> (창 12:1)` — 자막 전체가 인용이고 참조가 꼬리에 붙는 형태.
CAPTION_RE = re.compile(
    r"^(?P<text>.+?)\s*\((?P<book>[가-힣]+)\s?(?P<ch>\d+):(?P<v>\d+)(?:[-~](?P<ve>\d+))?\)$"
)

# `*자구* (출 3:12)` — 따옴표 대신 강조 별표로 두른 인용. yml 자막에서만 본다.
# TS 쪽에도 같은 형태가 있지만 거기 별표는 설계 주석의 소제목이라("*섭리 도입* (창 41:26)")
# 자구 주장이 아니다. 파일 종류로 나누는 편이 화자 목록보다 덜 틀린다.
EMPH = re.compile(r"\*(?P<text>[^*\n]{2,200})\*\s*" + REF)

# 참조를 달았지만 *인용이 아닌* 자막. 해설·안내 문장이 근거 절을 괄호로 가리키는
# 형태다("…라는 뜻이다 (창 45:5)"). 자구로 재면 저자가 쓴 산문을 성경과 대조하게 되고,
# 그 실패는 고칠 수 없다 — 애초에 성경이라고 주장한 적이 없기 때문이다.
# 판별을 화자로 하려다 실패했다 — `narrator` 자막 22건 중 20건이 축자 인용이었다.
# 화자는 "누가 말하는가" 이지 "인용인가" 가 아니다. 그래서 *표기* 로 나눈다:
# 괄호 안 참조 뒤에 한정어(참조·정서·배경·맥락·요약)가 붙으면 인용 주장이 아니다.
#
# 이 한정어는 게이트를 조용히 끄는 스위치가 아니다 — 화면에 그대로 떠서 사용자가
# "(왕상 19:14 정서)" 를 읽고 그것이 인용이 아님을 안다. 저자가 이미 쓰던 표기를
# (`content/elijah/scene4.yml` 의 "(왕상 19:3 정서)") 규칙으로 올린 것이다.
# 남용을 막는 것은 금지가 아니라 노출이다 — 아래 요약줄이 그 건수를 항상 같이 낸다.
KIND_KO = {"label": "라벨", "quote": "인용", "caption": "자막", "emphasis": "강조"}

REF_ONLY = re.compile(r"\(([가-힣]+)\s?\d+:\d+(?:[-~]\d+)?\s+(참조|정서|배경|맥락|요약)\)")


# F — 화면 인용 표기는 약칭으로 통일한다.
#
# 근거는 취향이 아니라 이미 있는 다수다. TS 모놀로그의 `q()` 표기 44건이 전부 약칭이고
# (`q("출 4:2~3", ...)`), 정본 표 `docs/verses-monologues-gae.txt` 의 키도 약칭이며
# (`시 23:1<TAB>...`), 잠금 파일도 그 키를 그대로 쓴다. yml 만 전체 이름을 섞어 썼다.
# 통일 방향을 반대로 잡으면 저 셋을 전부 뒤집어야 한다.
#
# 이 축은 자구를 재지 않는다 — 같은 절을 화면이 두 이름으로 부르는 것만 잡는다.
# 위 BOOK_ALIAS 가 두 표기를 하나로 접기 때문에 A~E 축은 이것을 영영 못 본다.
FULL_CITE = re.compile(
    r"\((" + "|".join(sorted(BOOK_ALIAS, key=len, reverse=True)) + r") ?(?=\d+:\d+)"
)


def sources() -> list[Path]:
    """콘텐츠 모듈 전부. `*-monologues.ts` 로 좁히지 않는다.

    D 축(하드코딩 회귀)이 좁으면 자구를 `scripture-quote.ts` 같은 이웃 파일로 옮겨
    박는 것만으로 게이트를 빠져나간다. A·B 축은 `q()` 가 있는 파일에서만 셈이 돌므로
    넓혀도 셈이 늘지 않는다 — 주석 속 사용 예시는 아래에서 걸러낸다.
    """
    return sorted(p for p in SRC_DIR.glob("*.ts") if not p.name.endswith(".test.ts"))


def yml_sources() -> list[Path]:
    """E 축 대상 — 미션 시나리오 yml + XR 낭독 콘텐츠 yml.

    둘을 한 셈에 넣는 이유는 같은 카드가 두 파일에 나뉘어 살기 때문이다.
    `moses.yml` 의 라벨은 `content/moses/scene3.yml` 카드 전문(全文)을 UI 폭에 맞게
    끊은 조각이라, 한쪽만 재면 두 파일이 어긋나는 것을 못 본다.
    """
    return sorted(SCEN_DIR.glob("*.yml")) + sorted(CONTENT_DIR.rglob("*.yml"))


def walk_cards(node) -> list[dict]:
    """문서 어디에 있든 `cards:` 리스트의 매핑 항목을 전부 모은다."""
    found: list[dict] = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == CARDS_KEY and isinstance(value, list):
                found += [x for x in value if isinstance(x, dict)]
            found += walk_cards(value)
    elif isinstance(node, list):
        for item in node:
            found += walk_cards(item)
    return found


def walk_captions(node, speaker=None) -> list[tuple[str, str | None]]:
    """자막 문자열을 화자와 함께 모은다.

    화자는 같은 매핑에 있는 `speaker` 를 쓰고, 없으면 바깥 매핑의 것을 물려받는다.
    `beats:` 항목처럼 화자가 한 단계 위에 붙는 스키마가 섞여 있기 때문이다.
    """
    found: list[tuple[str, str | None]] = []
    if isinstance(node, dict):
        here = node.get("speaker") if isinstance(node.get("speaker"), str) else speaker
        for key, value in node.items():
            if isinstance(value, str):
                found.append((value, here))
            found += walk_captions(value, here)
    elif isinstance(node, list):
        for item in node:
            found += walk_captions(item, speaker)
    return found


# 한 자막이 절 둘을 가운뎃점으로 잇는 형태 — `<자구> (욥 3:11) · <자구> (욥 6:8)`.
# `CAPTION_RE` 는 꼬리 참조 하나만 보고 그 앞을 전부 자구로 읽으므로, 이런 자막은
# 뒤 절의 자구에 **앞 절과 그 참조 표기까지 붙은 문자열** 을 대조하게 되어 반드시
# ALTERED 로 떨어진다. 자구가 틀려서가 아니라 자른 자리가 틀린 것이다
# (`content/job/scene2.yml` 의 욥 3:11 · 6:8 자막이 그 경우였다).
#
# 그래서 자르는 자리를 자막이 표기한 대로 따라간다. **느슨해지지 않는 이유** 는
# 쪼갠 조각이 *전부* 참조로 끝날 때만 쪼개기를 채택하기 때문이다 — 하나라도 아니면
# 통짜로 되돌린다. 가운뎃점이 자구 안에 있는 자막은 그래서 영향을 받지 않고,
# 채택된 경우에도 각 조각은 여전히 축자 대조를 받는다. 검사 대상이 줄지 않는다.
def split_citations(text: str) -> list[str]:
    whole = text.strip()
    if "·" not in whole:
        return [whole]
    parts = [p.strip() for p in whole.split("·")]
    if len(parts) < 2 or not all(CAPTION_RE.match(p) for p in parts):
        return [whole]
    return parts


def parse_yml(raw: str) -> list[dict]:
    """yml 한 파일에서 E1 라벨·E2 인용을 뽑는다.

    yml 주석(`#`)도 대상에 넣는다. TS 쪽과 달리 여기서는 주석을 봐주지 않는다 —
    시나리오 파일의 머리 주석("핵심 메시지: …")은 저자가 읽고 베껴 쓰는 자리라
    거기 틀린 자구가 있으면 아래 payload 로 번진다. 실제로 `david.yml` 은
    머리 주석과 `victory_note` 가 같은 오자를 나눠 갖고 있었다.
    """
    out: list[dict] = []
    for card in walk_cards(yaml.safe_load(raw)):
        label, ref = card.get("label"), card.get("scripture")
        if not isinstance(label, str) or not isinstance(ref, str):
            continue
        at = raw.find(f'"{label}"')
        out.append(
            {
                "kind": "label",
                "text": label,
                "ref": ref,
                "pos": at if at >= 0 else 0,
            }
        )
    for text, speaker in walk_captions(yaml.safe_load(raw)):
      for segment in split_citations(text):
        m = CAPTION_RE.match(segment)
        if not m:
            continue
        body = m.group("text").strip()
        # 통째로 따옴표에 싸인 자막은 E2 가 이미 잰다. 여기서 또 재면 같은 실패가 두 번 뜬다.
        if body[:1] in QUOTE_CHARS and body[-1:] in QUOTE_CHARS:
            continue
        at = raw.find(text.strip()[:40])
        out.append(
            {
                "kind": "caption",
                "text": body,
                "book": norm_book(m.group("book")),
                "chapter": int(m.group("ch")),
                "verse": int(m.group("v")),
                "end": int(m.group("ve")) if m.group("ve") else None,
                "speaker": speaker,
                "pos": at if at >= 0 else 0,
            }
        )
    prepared, idx = prepare(raw)
    for m in EMPH.finditer(prepared):
        out.append(
            {
                "kind": "emphasis",
                "text": m.group("text"),
                "book": norm_book(m.group(2)),
                "chapter": int(m.group(3)),
                "verse": int(m.group(4)),
                "end": int(m.group(5)) if m.group(5) else None,
                "pos": idx[m.start()],
            }
        )
    for m in QUOTED_YML.finditer(prepared):
        out.append(
            {
                "kind": "quote",
                "text": m.group("text"),
                "book": norm_book(m.group(2)),
                "chapter": int(m.group(3)),
                "verse": int(m.group(4)),
                "end": int(m.group(5)) if m.group(5) else None,
                "pos": idx[m.start()],
            }
        )
    return out


def comment_spans(src: str) -> list[tuple[int, int]]:
    """주석 구간 [start, end). 문자열·템플릿 리터럴 안의 `//` 를 주석으로 오인하지 않는다.

    D 축이 "주석 안이냐 코드 안이냐" 로 판정을 가르므로, 이 구분이 틀리면 하드코딩
    회귀를 통과시키거나 정당한 신학 주석을 실패로 낸다. 그래서 정규식이 아니라
    상태 기계로 훑는다.
    """
    spans: list[tuple[int, int]] = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            j = n if j < 0 else j
            spans.append((i, j))
            i = j
        elif c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            spans.append((i, j))
            i = j
        elif c in "\"'`":
            i += 1
            while i < n and src[i] != c:
                i += 2 if src[i] == "\\" else 1
            i += 1
        else:
            i += 1
    return spans


def in_comment(spans: list[tuple[int, int]], pos: int) -> bool:
    return any(a <= pos < b for a, b in spans)


def line_of(src: str, pos: int) -> int:
    return src.count("\n", 0, pos) + 1


def parse_clips(src: str) -> list[dict]:
    """`q("표기", ["ref", from, to], ["ref", from, to, lastWordChars]) ` 를 뽑는다.

    괄호 균형으로 끊는다 — 정규식 하나로 끝내려 하면 인자가 줄을 넘어갈 때
    조각만 잡는다. 그 형태의 미탐이 이 파일의 초판을 망쳤다(리터럴 `" +` 분할).

    주석 속 `q(...)` 는 사용 예시다(`scripture-quote.ts` 머리말). 세면 화면에 없는
    인용이 셈에 들어가고, 예시가 낡으면 있지도 않은 실패가 뜬다.
    """
    spans = comment_spans(src)
    out: list[dict] = []
    for m in re.finditer(r"\bq\(", src):
        if in_comment(spans, m.start()):
            continue
        depth, i, n = 0, m.start() + 1, len(src)
        while i < n:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = src[m.end() : i]
        cite_m = re.match(r'\s*"([^"]+)"', body)
        if not cite_m:
            continue
        clips = [
            {
                "ref": c.group(1),
                "from": int(c.group(2)),
                "to": int(c.group(3)),
                "last": int(c.group(4)) if c.group(4) else None,
            }
            for c in re.finditer(
                r'\[\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*(\d+)\s*)?\]', body
            )
        ]
        out.append(
            {
                "cite": cite_m.group(1),
                "clips": clips,
                "pos": m.start(),
            }
        )
    return out


ESCAPE = re.compile(r"\\n|\\\"|\\'")


def prepare(src: str) -> tuple[str, list[int]]:
    """비교용 형태 + **원본 오프셋 지도**.

    `\\n` 은 문단 경계 표식으로 남기고, 줄을 넘겨 이어 붙인 리터럴(`" +\\n  "`)은
    한 덩어리로 만든다. 둘 다 길이를 바꾸므로 변환 뒤 위치로는 원본을 못 짚는다.

    지도가 왜 필요한가: D 축은 인용이 **주석 안이냐 코드 안이냐** 로 판정을 가른다.
    초판은 위치를 잃고 `raw.find(자구 앞머리)` 로 되찾았는데, 같은 자구가 주석에도
    있으면 코드에 박힌 회귀가 주석 건으로 붙어 **코드 하드코딩이 통과했다**. 위치는
    추측하지 말고 들고 다녀야 한다.
    """
    edits: list[tuple[int, int, str]] = [
        (m.start(), m.end(), SENTINEL if m.group() == "\\n" else m.group()[1])
        for m in ESCAPE.finditer(src)
    ]
    edits += [(m.start(), m.end(), "") for m in JOIN.finditer(src)]

    out: list[str] = []
    idx: list[int] = []
    i = 0
    for a, b, rep in sorted(edits):
        if a < i:  # 겹치는 변환은 먼저 잡은 쪽이 이긴다
            continue
        out.append(src[i:a])
        idx.extend(range(i, a))
        out.append(rep)
        idx.extend([a] * len(rep))
        i = b
    out.append(src[i:])
    idx.extend(range(i, len(src)))
    return "".join(out), idx


def parse_literals(raw: str) -> list[dict]:
    """소스에 자구가 그대로 박힌 `"…"(참조)` 를 뽑는다 (D 축)."""
    joined, idx = prepare(raw)
    spans = comment_spans(raw)
    out: list[dict] = []
    for m in QUOTED.finditer(joined):
        pos = idx[m.start()]
        out.append(
            {
                "text": m.group("text").strip(),
                "book": norm_book(m.group(2)),
                "chapter": int(m.group(3)),
                "verse": int(m.group(4)),
                "end": int(m.group(5)) if m.group(5) else None,
                "line": line_of(raw, pos),
                "comment": in_comment(spans, pos),
            }
        )
    return out


# ── 시드 자구 ─────────────────────────────────────────────────────────────
def seed_texts() -> dict[str, str]:
    """`translation='rev'` 시드 행. 프론트가 `/api/scripture` 로 받는 그 자구다."""
    return {r["ref"]: r["text"] for r in seeded_rows() if r["trans"] == TRANSLATION}


def resolve_clip(clip: dict, text: str) -> str | None:
    """`scripture-quote.ts` 의 `resolveClip` 과 **같은 규칙** 으로 자른다.

    두 구현이 어긋나면 여기가 통과시킨 것을 화면이 못 그리거나 그 반대가 된다.
    낱말 분리는 공백 기준, 끝 낱말만 `lastWordChars` 글자로 자른다(조사 절단).
    """
    words = [w for w in re.split(r"\s+", text) if w]
    if clip["from"] < 0 or clip["to"] > len(words) or clip["from"] >= clip["to"]:
        return None
    picked = words[clip["from"] : clip["to"]]
    if clip["last"] is not None:
        last = picked[-1]
        if clip["last"] <= 0 or clip["last"] > len(last):
            return None
        picked[-1] = last[: clip["last"]]
    return " ".join(picked)


def resolve_quote(entry: dict, texts: dict[str, str]) -> str | None:
    parts = []
    for clip in entry["clips"]:
        text = texts.get(clip["ref"])
        if text is None:
            return None
        part = resolve_clip(clip, text)
        if part is None:
            return None
        parts.append(part)
    return " … ".join(parts)


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


def words_of(s: str) -> list[str]:
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


def split_ref(ref: str) -> tuple[str, int, int] | None:
    """`ex-4:12` → (`출`, 4, 12). 모르는 책 코드면 None."""
    m = re.fullmatch(r"([a-z0-9]+)-(\d+):(\d+)", ref)
    if not m or m.group(1) not in BOOK_KO:
        return None
    return BOOK_KO[m.group(1)], int(m.group(2)), int(m.group(3))


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
    w = [re.sub(r"\s", "", x) for x in words_of(quote)]
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
    "왕상": "1ki", "왕하": "2ki", "욥": "job", "시": "psa", "잠": "pro",
    "전": "ecc", "사": "isa", "렘": "jer", "겔": "ezk", "단": "dan",
    "마": "mat", "막": "mrk", "눅": "luk", "요": "jhn", "행": "act",
    "롬": "rom", "고전": "1co", "고후": "2co", "갈": "gal", "엡": "eph",
    "빌": "php", "골": "col", "히": "heb", "약": "jas", "계": "rev",
    "에": "est",
}
TAG = re.compile(r"<[^>]+>")
# 각주 팝업(`<div class=D2 …>`)은 화면에서 숨어 있지만 마크업상 절 본문 **뒤** 에
# 붙어 있다. 태그만 걷어내면 그 안의 주석 문구가 절 끝에 따라붙는다 — 초판의
# `요 1:14` 가 "…진리가 충만하더라 헬, 참이" 로 끝났다. 각주 *번호* 만 지우고
# 각주 *본문* 을 안 지운 탓이다. 둘을 다 지워야 본문만 남는다.
POPUP = re.compile(r"<div[^>]*class=D2\b.*?</div>", re.S | re.I)
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
    page = POPUP.sub("", page)
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


def needed_refs() -> set[tuple[str, int, int]]:
    """정본 표가 덮어야 하는 절 집합 — 클립이 여는 참조 + 주석 인용의 참조.

    `q()` 의 표기(`출 4:14~16`)가 아니라 **클립 참조** 를 기준으로 삼는다. 표기는
    그 응답이 근거로 삼은 단락을 가리키느라 실제로 읽는 절보다 넓을 수 있다 —
    표기를 기준으로 표를 만들면 읽지도 않는 절을 정본에 쌓는다.
    """
    need: set[tuple[str, int, int]] = set()
    unknown: set[str] = set()
    for f in sources():
        raw = f.read_text(encoding="utf-8")
        for entry in parse_clips(raw):
            for clip in entry["clips"]:
                parsed = split_ref(clip["ref"])
                if parsed is None:
                    unknown.add(clip["ref"])
                else:
                    need.add(parsed)
        for lit in parse_literals(raw):
            last = lit["end"] if lit["end"] and lit["end"] >= lit["verse"] else lit["verse"]
            for v in range(lit["verse"], last + 1):
                need.add((lit["book"], lit["chapter"], v))
    for f in yml_sources():
        for item in parse_yml(f.read_text(encoding="utf-8")):
            if item["kind"] == "label":
                parsed = split_ref(item["ref"])
                if parsed is None:
                    unknown.add(item["ref"])
                else:
                    need.add(parsed)
            else:
                last = (
                    item["end"]
                    if item["end"] and item["end"] >= item["verse"]
                    else item["verse"]
                )
                for v in range(item["verse"], last + 1):
                    need.add((item["book"], item["chapter"], v))
    if unknown:
        raise SystemExit(
            f"[판정불가] 모르는 책 코드: {', '.join(sorted(unknown))} — BOOK_KO 에 추가하라"
        )
    return need


def refresh() -> int:
    need = needed_refs()
    chapters = sorted({(b, c) for b, c, _ in need})
    table: dict[tuple[str, int, int], str] = {}
    for book, chapter in chapters:
        print(f"  {book} {chapter} …", file=sys.stderr)
        verses = fetch_chapter(book, chapter)
        for b, c, v in sorted(x for x in need if x[0] == book and x[1] == chapter):
            text = verses.get(v)
            if not text:
                print(f"[판정불가] {b} {c}:{v} 본문을 못 읽었다", file=sys.stderr)
                return 2
            table[(b, c, v)] = text
        time.sleep(1.2)  # 1차 출처에 대한 예의

    order = list(BOOK_CODE)
    rows = sorted(
        table.items(), key=lambda kv: (order.index(kv[0][0]), kv[0][1], kv[0][2])
    )
    body = [
        "# docs/verses-monologues-gae.txt — 프론트 모놀로그 인용 대조 정본 (개역개정)",
        "#",
        "# 무엇인가",
        "#   scripts/check_monologue_quotes.py 가 대조 기준으로 삼는 절들의 개역개정 본문이다.",
        "#   두 갈래가 들어온다.",
        "#     · frontend/src/lib/content/*.ts 의 q() 클립이 여는 참조 (A~D 축)",
        "#       소스에는 자구가 없고 참조 + 낱말 인덱스만 있다(scripture-quote.ts) — 자구는",
        "#       시드에서 온다. 이 표는 그 *시드* 가 개역개정인지를 재는 데 쓴다.",
        "#     · 시나리오/XR 콘텐츠 yml 이 성경이라 딱지 붙인 문자열의 참조 (E 축)",
        "#       카드 라벨(label+scripture)과 따옴표 인용은 자구가 yml 에 직접 들어 있어",
        "#       이 표와 곧바로 대조한다.",
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
    seeds = seed_texts()
    if not seeds:
        print(f"[판정불가] 시드에 translation='{TRANSLATION}' 행이 없다")
        return 2

    quotes: list[dict] = []
    literals: list[dict] = []
    for f in sources():
        raw = f.read_text(encoding="utf-8")
        for entry in parse_clips(raw):
            entry["site"] = f"{f.name}:{line_of(raw, entry['pos'])}"
            quotes.append(entry)
        for lit in parse_literals(raw):
            lit["site"] = f"{f.name}:{lit['line']}"
            literals.append(lit)

    if not quotes:
        # 인용이 0건이면 이 게이트는 아무것도 지키지 않는다. 조용한 초록은 추출기가
        # 죽은 것과 구별되지 않으므로 판정 불가로 낸다. 2026-08-21 의 재작성이 바로
        # 이 형태의 거짓 초록(32건 → 4건) 때문에 있었다.
        print("[판정불가] q() 인용을 한 건도 못 뽑았다 — 추출기나 소스 형식을 확인하라")
        return 2

    fails: list[str] = []

    # ── A. 해석 ───────────────────────────────────────────────────────────
    a_ok = 0
    for entry in quotes:
        if not entry["clips"]:
            fails.append(f"✗ [A 해석] {entry['site']}  q(\"{entry['cite']}\") 에 클립이 없다")
            continue
        text = resolve_quote(entry, seeds)
        if text is None:
            missing = [c["ref"] for c in entry["clips"] if c["ref"] not in seeds]
            why = (
                f"시드에 없는 참조 {', '.join(missing)}"
                if missing
                else "낱말 인덱스가 절 밖이다"
            )
            fails.append(
                f"✗ [A 해석] {entry['site']}  q(\"{entry['cite']}\") — {why}\n"
                f"    → 화면에서 이 문단이 통째로 사라진다 (resolveMonologue 는 all-or-nothing)"
            )
            continue
        entry["text"] = text
        a_ok += 1

    # ── B. cite ↔ ref ────────────────────────────────────────────────────
    b_ok = 0
    for entry in quotes:
        m = CITE_RE.fullmatch(entry["cite"].strip())
        if not m:
            fails.append(
                f"✗ [B cite] {entry['site']}  표기를 못 읽었다: \"{entry['cite']}\""
            )
            continue
        ko, ch, vs = m.group(1), int(m.group(2)), int(m.group(3))
        ve = int(m.group(4)) if m.group(4) else vs
        bad = []
        for clip in entry["clips"]:
            parsed = split_ref(clip["ref"])
            if parsed is None:
                bad.append(f"{clip['ref']} (모르는 책 코드)")
            elif parsed != (ko, ch, parsed[2]) or not (vs <= parsed[2] <= ve):
                bad.append(f"{clip['ref']} → {parsed[0]} {parsed[1]}:{parsed[2]}")
        if bad:
            fails.append(
                f"✗ [B cite] {entry['site']}  표기 \"{entry['cite']}\" 가 안 덮는 참조: "
                f"{', '.join(bad)}\n"
                f"    → 독자는 읽지도 않은 절을 출처로 믿는다"
            )
            continue
        b_ok += 1

    # ── C. 시드 ↔ 정본 ────────────────────────────────────────────────────
    c_refs = sorted({c["ref"] for e in quotes for c in e["clips"]})
    c_tally: dict[str, int] = {}
    for ref in c_refs:
        parsed = split_ref(ref)
        seed = seeds.get(ref)
        if parsed is None or seed is None:
            fails.append(
                f"✗ [C 시드] {ref} — "
                + ("모르는 책 코드" if parsed is None else "시드에 행이 없다")
            )
            continue
        canon = canon_text(table, parsed[0], parsed[1], parsed[2], None)
        verdict = "NO_CANON" if canon is None else classify(seed, canon)
        # 시드는 절 *전체* 라 정본과 축자로 같아야 한다. 부분문자열 통과(MATCH)로는
        # 시드가 뒤가 잘려 있어도 초록이 된다 — 길이까지 같은지 함께 본다.
        if verdict == "MATCH" and canon is not None and base(seed) != base(canon):
            verdict = "SPLICE_NOMARK"
        c_tally[verdict] = c_tally.get(verdict, 0) + 1
        if verdict not in PASSING:
            fails.append(
                f"✗ [C 시드] [{verdict}] {parsed[0]} {parsed[1]}:{parsed[2]} ({ref})\n"
                f"    시드   {seed[:140]}\n"
                f"    개역개정 {(canon or '')[:140]}\n"
                f"    → {NOTE[verdict]}"
            )

    # ── D. 하드코딩 회귀 ──────────────────────────────────────────────────
    d_tally: dict[str, int] = {}
    for lit in literals:
        canon = canon_text(table, lit["book"], lit["chapter"], lit["verse"], lit["end"])
        verdict = "NO_CANON" if canon is None else classify(lit["text"], canon)
        lit["verdict"], lit["canon"] = verdict, canon or ""
        d_tally[verdict] = d_tally.get(verdict, 0) + 1
        where = "주석" if lit["comment"] else "코드"
        if not lit["comment"]:
            fails.append(
                f"✗ [D 하드코딩] {lit['site']}  코드에 성경 자구가 박혔다\n"
                f"    인용 {lit['text'][:100]}\n"
                f"    → 자구는 /api/scripture 에서 받아라 (q(\"{lit['book']} "
                f"{lit['chapter']}:{lit['verse']}\", [...]) 형태)"
            )
        elif verdict not in PASSING:
            fails.append(
                f"✗ [D {where}] [{verdict}] {lit['book']} {lit['chapter']}:{lit['verse']}"
                f"  {lit['site']}\n"
                f"    인용   {lit['text']}\n"
                f"    개역개정 {lit['canon'][:140]}\n"
                f"    → {NOTE[verdict]}"
            )

    # ── E. yml 이 성경이라 딱지 붙인 문자열 ───────────────────────────────
    #   E1 라벨   `cards:` 의 label — `scripture:` 가 그 절의 자구라고 딱지 붙인 것
    #   E2 인용   따옴표 + 참조
    #   E3 자막   `text_ko` 류 낭독 자막 전체가 인용이고 참조가 꼬리에 붙은 것
    #   E4 강조   따옴표 대신 별표로 두른 인용
    e_items: list[dict] = []
    for f in yml_sources():
        raw = f.read_text(encoding="utf-8")
        for item in parse_yml(raw):
            item["site"] = f"{f.relative_to(ROOT)}:{line_of(raw, item['pos'])}"
            e_items.append(item)
    ref_only_n = sum(
        len(REF_ONLY.findall(f.read_text(encoding="utf-8"))) for f in yml_sources()
    )

    # ── F. 화면 인용 표기가 약칭인가 ──────────────────────────────────────
    for f in yml_sources() + sources():
        raw = f.read_text(encoding="utf-8")
        for m in FULL_CITE.finditer(raw):
            full = m.group(1)
            fails.append(
                f"✗ [F 표기] {f.relative_to(ROOT)}:{line_of(raw, m.start())}"
                f"  ({full} ...) → ({BOOK_ALIAS[full]} ...)\n"
                f"    → 화면 인용 표기는 약칭으로 통일한다"
                f" (docs/SCRIPTURE-REF-CONVENTION.md '화면 표기 규약')"
            )
    e_tally: dict[str, int] = {}
    for item in e_items:
        if item["kind"] == "label":
            parsed = split_ref(item["ref"])
            if parsed is None:
                fails.append(f"✗ [E 라벨] {item['site']}  모르는 책 코드 {item['ref']}")
                continue
            book, chapter, verse, end = *parsed, None
            where = f"{item['ref']}"
        else:
            book, chapter, verse, end = (
                item["book"],
                item["chapter"],
                item["verse"],
                item["end"],
            )
            where = f"{book} {chapter}:{verse}"
        canon = canon_text(table, book, chapter, verse, end)
        verdict = "NO_CANON" if canon is None else classify(item["text"], canon)
        item["verdict"] = verdict
        e_tally[verdict] = e_tally.get(verdict, 0) + 1
        if verdict not in PASSING:
            kind = KIND_KO[item["kind"]]
            fails.append(
                f"✗ [E {kind}] [{verdict}] {where}  {item['site']}\n"
                f"    문자열 {item['text']}\n"
                f"    개역개정 {(canon or '')[:140]}\n"
                f"    → {NOTE[verdict]}"
                + (
                    "\n    → `scripture:` 딱지가 붙은 문자열은 그 절의 자구여야 한다"
                    if item["kind"] == "label"
                    else ""
                )
            )

    if args.list:
        for item in e_items:
            mark = "  " if item.get("verdict") in PASSING else "✗ "
            print(f"{mark}[E/{item['kind']}] [{item.get('verdict')}] {item['site']}")
        for entry in quotes:
            mark = "  " if entry.get("text") else "✗ "
            print(f"{mark}[A] {entry['site']}  ({entry['cite']})")
            if entry.get("text"):
                print(f'      "{entry["text"]}"')
        for lit in literals:
            mark = "  " if lit["verdict"] in PASSING and lit["comment"] else "✗ "
            print(
                f"{mark}[D/{'주석' if lit['comment'] else '코드'}] [{lit['verdict']}] "
                f"{lit['book']} {lit['chapter']}:{lit['verse']}  {lit['site']}"
            )

    for line in fails:
        print(line)

    c_sum = " · ".join(f"{k} {v}" for k, v in sorted(c_tally.items())) or "0건"
    in_comment_n = sum(1 for x in literals if x["comment"])
    print(
        f"\nA 해석 {a_ok}/{len(quotes)}"
        f" · B cite↔ref {b_ok}/{len(quotes)}"
        f" · C 시드↔정본 {len(c_refs)}건 ({c_sum})"
        f" · D 리터럴 {len(literals)}건 (주석 {in_comment_n} · 코드 "
        f"{len(literals) - in_comment_n})"
        f" · E yml {len(e_items)}건 ("
        + " · ".join(
            f"{KIND_KO[k]} {sum(1 for x in e_items if x['kind'] == k)}"
            for k in ("label", "quote", "caption", "emphasis")
        )
        + f") · 참조 표기 {ref_only_n}건"
        + f" · F 표기 위반 {sum(1 for x in fails if x.startswith('✗ [F'))}건"
    )
    if fails:
        print(f"실패 {len(fails)}건")
        return 1
    print(
        "전건 통과 — 프론트에 성경 자구 없음 · 시드가 개역개정 축자 · 표기가 참조를 덮음"
        " · yml 이 성경이라 딱지 붙인 문자열이 정본 자구 · 화면 표기가 약칭 하나"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

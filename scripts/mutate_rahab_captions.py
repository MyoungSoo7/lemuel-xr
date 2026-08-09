#!/usr/bin/env python3
"""`check_rahab_captions.py` 자신을 검사한다 — 틀린 seed 를 넣으면 잡는가.

**왜 있는가.** 판정기가 초록을 낸다는 것과, 틀린 입력에서 빨강을 낸다는 것은 다르다.
이 리포는 그 차이로 두 번 데었다 — rev.11 의 「문서 층위 14/14 전건 검출」이 실행 근거
다섯뿐이었던 일(seed 행 N)과, 이 파일을 쓰기 직전 `check_rahab_captions.py` 가 낸
**「footer 부착 0/6」이라는 거짓 FAIL**(표 칸에서 이미 걷힌 백틱을 찾고 있었다)이다.
뒤엣것은 **빨강이라 눈에 띄었을 뿐**이고, 방향이 반대였으면 초록으로 지나갔다.

🚨 **rc 만 보지 않는다.** rc 는 열네 축이 다 같은 `1` 을 내므로 **엉뚱한 축이 대신
걸려도 통과**시킨다. 여기서는 각 변이가 **지목한 검사 키가 실제로 FAIL 목록에 있는지**
까지 본다. 이 규율을 세우면서 `mutate_review_log.py` 가 rc 만 보고 있던 것이 드러나
**그 파일도 같이 올렸다**(문구 대조 · 9/9 유지 — 곧 저쪽은 실제로 엉뚱한 축에 얹혀 있진
않았고, 그것을 이제 **가정이 아니라 실측으로** 안다).

각 변이는 판정기가 재겠다고 **선언한 축 하나씩**을 무너뜨린다.

    python3 scripts/mutate_rahab_captions.py
"""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "docs/SEED-RAHAB.md"
# rev.12 에서 문안이 seed 를 떠났다. 변이도 **문안이 실제로 사는 파일**을 건드려야 한다 —
# seed 만 계속 변이시키면 footer·카드·내레이션 축은 「바꿀 것이 없어서」 조용히 통과한다.
STRINGS = ROOT / "docs/RAHAB-LOCKED-STRINGS.md"
CHECK = ROOT / "scripts/check_rahab_captions.py"

SRC = SEED.read_text(encoding="utf-8")
SSRC = STRINGS.read_text(encoding="utf-8")


def line_with(*needles: str, without: str = "", src: str | None = None) -> str:
    """모든 조각을 담은 **유일한** 줄을 돌려준다.

    ⚠️ 유일성을 요구하는 이유 — 여럿이면 `str.replace` 가 어느 것을 바꿨는지 알 수 없고,
    그러면 변이가 겨냥한 축과 실제로 무너진 축이 어긋난다. 실제로 처음 쓴 needle 다섯 개가
    표 행과 산문에 동시에 걸려 여기서 멎었다 — **그 자리에서 멎지 않았으면 표를 바꿔 놓고
    카드 본문을 바꿨다고 적을 뻔했다.**

    `without` 는 「§5-4 표 행」과 「§7-2-a 카드 본문」이 **같은 문안을 쓰기 때문에** 있다.
    표 행은 `|` 로 시작하고 카드 본문은 들여쓴 산문이라, 그 한 글자로 갈린다.
    """
    hits = [ln for ln in (SRC if src is None else src).splitlines()
            if all(n in ln for n in needles) and not (without and without in ln)]
    if len(hits) != 1:
        raise AssertionError(f"{needles} 를 담은 줄이 {len(hits)}개다 — 1개여야 한다")
    return hits[0]


CAP_2_18 = line_with("| 4     | 2    | `수 2:18 중`")
CAP_2_1 = line_with("| 1     | 1    | `수 2:1 중`")
CAP_2_9 = line_with("| 1     | 2    | `수 2:9 중`")
F65_STIGMA = line_with("| `rahab_f65_stigma`", "카드 하단(동의 전)", src=SSRC)
F65_CLOSE = line_with("| `rahab_f65_close`", "Scene 5 종결 화면 하단(정지 후)", src=SSRC)
# 카드 본문 쪽 footer — §1 표 행과 문안이 같아 `without="|"` 로 갈라낸다.
# 낙인 카드가 아니라 **강요 카드**를 쓰는 이유: 낙인 카드의 footer 는 두 줄로 접혀 있어
# 줄 단위 변이가 반쪽만 지운다. 한 줄로 끝나는 자리를 골라야 변이가 겨냥한 대로 든다.
CARD_COERCION_FOOT = line_with("당신에게 일어난 일은 당신의 잘못이 아닙니다",
                               without="|", src=SSRC)
NAR_S1 = line_with("| `rahab_nar_s1_address`", src=SSRC)
NAR_MARK = line_with("| `rahab_nar_marker`", src=SSRC)
# 🚨 고지 줄은 **계보 카드** 것을 고른다. 낙인·강요는 이름만 봐도 떠오르지만
#    `rahab_lineage_birth` 는 route 가 낙인과 같아서 걸리는 장이라, 사람이 세면
#    바로 이 장이 빠진다. 변이도 사람이 틀리는 자리를 겨눈다.
CARD_LINEAGE_DISCLOSE = line_with("· 그 자리에는 성경 본문이 아닌 안내 문장", src=SSRC)

# (무너뜨리는 축, 겨냥한 검사 키, 어느 정본을 변이시키는가, 설명, before, after)
SEED_F, STR_F = "seed", "strings"
MUTANTS: list[tuple[str, str, str, str, str, str]] = [
    ("행 수", "a-rows", SEED_F, "자막 24행에서 한 행을 지운다", CAP_2_1, ""),
    ("좌표 1:1", "a-used", SEED_F, "자막 좌표를 USED 에 없는 절로 바꾼다",
     CAP_2_9, CAP_2_9.replace("수 2:9 중", "수 2:10 중")),
    ("`중` 표기", "a-jung", SEED_F, "부분 인용에서 `중` 을 뗀다",
     CAP_2_9, CAP_2_9.replace("`수 2:9 중`", "`수 2:9`")),
    ("Scene 순서", "a-order", SEED_F, "Scene 안의 순서 번호를 건너뛴다",
     CAP_2_9, CAP_2_9.replace("| 1     | 2    |", "| 1     | 9    |")),
    ("화자 표기", "a-attr", SEED_F, "🚨 2:18 의 화자 표기를 지운다 — R2 오독 경로",
     CAP_2_18, re.sub(r"`정탐꾼`", "—", CAP_2_18, count=1)),
    ("표기 근거", "a-word", SEED_F, "본문에 없는 낱말로 화자를 표기한다",
     CAP_2_18, re.sub(r"`정탐꾼`", "`두 밀정`", CAP_2_18, count=1)),
    ("footer 종수", "f-rows", STR_F, "§1 에서 한 종을 지운다", F65_CLOSE, ""),
    ("카드 집합", "f-cards", STR_F, "footer 가 없는 카드를 가리키게 한다",
     F65_STIGMA, F65_STIGMA.replace("`rahab_stigma` 카드", "`rahab_ghost` 카드")),
    ("부착 커버리지", "f-cover", STR_F,
     "🚨 카드 본문에서 footer 줄을 지운다 — rev.5·rev.7 이 두 번 틀린 자리",
     CARD_COERCION_FOOT, ""),
    ("위기 자원 토큰", "f-token", STR_F, "footer 에서 `{{crisis_resources.default}}` 를 뗀다",
     F65_CLOSE, F65_CLOSE.replace(" {{crisis_resources.default}}", "")),
    ("토큰 중복", "f-once", STR_F, "카드 본문에 위기 자원 줄을 하나 더 넣는다",
     CARD_COERCION_FOOT, CARD_COERCION_FOOT + "\n    지금 힘드시면 {{crisis_resources.default}}"),
    ("금지 토큰", "f-forbid", STR_F, "footer 문안에 금지 토큰을 넣는다",
     F65_CLOSE, F65_CLOSE.replace("지금 힘들다면", "믿음으로 이겨내 보세요. 지금 힘들다면")),
    # ── rev.12 신설. 이 네 축은 **잠그는 판에서 바로 시험한다** —
    #    잠그기만 하고 변이를 미루면 그 사이 판은 「잠겼으니 지켜진다」로 읽힌다.
    ("내레이션 종수", "n-rows", STR_F, "§3 에서 내레이션 한 줄을 지운다", NAR_S1, ""),
    ("표지 자구", "n-marker", STR_F, "🚨 정본/비정본을 가르는 유일한 낱말을 바꾼다",
     NAR_MARK, NAR_MARK.replace("본문이 아닙니다", "참고 문구")),
    ("내레이션 금지 토큰", "n-forbid", STR_F, "내레이션에 R3 어휘를 넣는다",
     NAR_S1, NAR_S1.replace("라합이 여기서", "믿음으로 이겨내 보세요. 라합이 여기서")),
    ("본문 자구 복사", "n-verse", STR_F,
     "🚨 내레이션에 성경 자구를 넣는다 — rev.12 초안이 실제로 저지른 결함",
     NAR_S1, NAR_S1.replace("라합이 여기서", "지붕에 올라가 있습니다. 라합이 여기서")),
    ("거절 고지", "n-disclose", STR_F,
     "🚨 계보 카드에서 고지 줄을 지운다 — route 가 낙인과 같아 사람이 세면 빠지는 장",
     CARD_LINEAGE_DISCLOSE, ""),
]

FAIL_LINE = re.compile(r"^\s*\[FAIL\s*\]\s*(\S+)")


def run(seed: Path, strings: Path = STRINGS) -> tuple[int, set[str]]:
    p = subprocess.run([sys.executable, str(CHECK),
                        "--seed", str(seed), "--strings", str(strings)],
                       capture_output=True, text=True, cwd=ROOT)
    keys = {m.group(1) for ln in p.stdout.splitlines() if (m := FAIL_LINE.match(ln))}
    return p.returncode, keys


def main() -> int:
    base_rc, base_keys = run(SEED)
    print(f"기준선: rc={base_rc} · FAIL 키 {sorted(base_keys) or '없음'}")
    print("  (기준선 rc=2 는 `content/rahab/` 부재로 인한 BLOCKED 다 — FAIL 이 0 이면 정상)")
    if base_keys:
        print("  ⚠️ 기준선에 FAIL 이 있다 — 아래 판정이 그 FAIL 에 얹혀 참이 될 수 있다")
    print()

    caught = 0
    with tempfile.TemporaryDirectory() as d:
        docs = Path(d) / "docs"
        docs.mkdir()
        for axis, key, which, why, before, after in MUTANTS:
            src = SRC if which == SEED_F else SSRC
            assert src.count(before) == 1, (axis, which, src.count(before))
            # 🚨 변이가 원본과 같으면 그것은 검사가 아니라 **아무것도 아닌 실행**이다.
            assert after != before, f"{axis}: 변이가 원본과 같다 — 무효 변이"
            ms, mt = docs / "MUTANT-SEED.md", docs / "MUTANT-STRINGS.md"
            mutated = src.replace(before, after)
            ms.write_text(SRC if which == STR_F else mutated, encoding="utf-8")
            mt.write_text(SSRC if which == SEED_F else mutated, encoding="utf-8")
            rc, keys = run(ms, mt)
            hit = key in keys
            caught += hit
            extra = sorted(keys - {key} - base_keys)
            print(f"{'✅' if hit else '❌'} {axis:14s} {key:8s} rc={rc}  — {why}")
            if not hit:
                print(f"     🚨 겨냥한 키가 FAIL 에 없다. 실제 FAIL: {sorted(keys) or '없음'}")
            elif extra:
                print(f"     ⓘ 함께 걸린 축: {' · '.join(extra)}")

    print(f"\n--- 검출 {caught} / {len(MUTANTS)} ---")
    if caught != len(MUTANTS):
        print("🚨 못 잡은 축이 있다 — 그 축은 선언만 있고 집행이 없다.")
        return 1
    print(f"⚠️ 이 초록이 말하지 않는 것: **잡지 않는 결함의 범위**다. 변이 {len(MUTANTS)}종은")
    print("   판정기가 재겠다고 선언한 축의 목록이지 결함 공간의 표본이 아니다. 그리고")
    print("   `a-yml`·`f-yml`·`n-yml` 세 축은 `content/rahab/` 이 없어 **한 번도 변이로")
    print("   시험받지 않았다** — designer 산출물이 생기기 전까지 그 셋은 미검증 코드다.")
    print("   `n-verse` 가 잡는 것도 **자구 복사뿐**이다. 자구를 안 겹치면서 건너뛴 절의")
    print("   사건만 옮기는 문장은 이 변이 목록에 없고, 검사기도 잡지 못한다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)

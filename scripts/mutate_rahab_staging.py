#!/usr/bin/env python3
"""`scripts/check_rahab_staging.py` 를 돌연변이로 검사한다 — 검사기가 진짜 재고 있는가.

**왜 있는가.** 초록을 내는 검사기는 두 가지 중 하나다: 대상이 옳거나, 검사기가 눈감고
있거나. 둘은 출력이 같다. 구분하는 유일한 방법은 **틀린 입력을 넣어 보는 것**이다.

**무엇을 메우는가 — 두 겹이다.**

① rev.10 도 돌연변이 검사를 했지만 **9종**이었고, 그 9종은
`b-doc`·`b-vocab`·`b-spell`·`a-doc`·`d-doc`·`e-doc`·`c-exec`·`c-cross`·`c-union` 만
건드렸다. 문서 층위 검사는 **14개**다 — 곧 `e-cross`·`c-doc`·`d-dest`·`d-name`·`f-corpus`
**다섯은 한 번도 "틀린 입력을 넣어 봤을 때 잡는가"를 확인받지 않았다.** 그중 `e-cross` 는
rev.11 이 **바로 그 판에 신설한** 검사이고, 신설 직후 실제 결함을 잡았다는 사실만으로
초록의 근거를 삼고 있었다. 한 번 잡았다는 것과 잡도록 만들어졌다는 것은 다르다.

② 🚨 **그런데 rev.10 의 9종은 리포에 남지 않은 일회성 스크립트였다.** 곧 이 파일이
다섯만 담고 있는 동안 seed 의 「문서 층위 **14/14** 전건 검출」은 **실행으로 재현되는
근거가 5종뿐**이었다 — 이 seed 가 §7-2-a 의 「footer 6/6 기계 확인」을 두고 지적한 것과
정확히 같은 형태다(리포에 없는 스크립트의 결과는 그 판 시점의 실측이지 불변식이 아니다).
rev.11 후반 채점이 이것을 잡았고, **아홉을 옮겨 적는 대신 다시 만들었다.** 지금 이 파일의
변이는 **14종**이고 문서 층위 검사 14개와 1:1 이다.

⚠️ **14/14 가 말하지 않는 것.** 이것은 「지목한 결함을 잡는다」까지다. yml 층위 4개
(`a-yml`·`b-yml`·`d-yml`·`e-yml`)는 `content/rahab/` 이 없어 **여전히 BLOCKED** 이고,
변이 14종은 검사기가 못 잡는 결함 범위의 **표본이 아니라 예시**다.

**변이는 정본을 건드리지 않는다.** seed 는 임시 사본에 변이를 넣고, `f-corpus` 만은
seed 가 아니라 `content/` 를 읽으므로 **저장소 전체를 임시 ROOT 로 복사해** 거기서 돈다.
작업 트리는 어느 경우에도 수정되지 않는다.

**판정.** 각 변이는 **지목한 검사 항목이 `FAIL` 로 뒤집히는가**로만 채점한다. 같이
뒤집힌 다른 항목은 기록만 한다 — 변이를 완전히 격리할 수 없는 항목이 있기 때문이고
(`c-union` 은 카드 델타의 합집합을 보므로 카드 표를 건드리면 함께 움직인다),
그 사실을 숨기는 것보다 적는 편이 낫다.

**rc 규약**: `0` 전건 검출 · `1` 미검출 있음 · `≥126` 실행 실패.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED_REL = "docs/SEED-RAHAB.md"
CHECKER_REL = "scripts/check_rahab_staging.py"

# (지목 항목, 정본에 정확히 1회 있는 원문, 변이문)
# 변이는 **의미를 틀리게** 만든다 — 문법을 깨는 변이는 파서를 죽일 뿐 검사를 재지 못한다.
MUTANTS: list[tuple[str, str, str, str]] = [
    (
        "e-cross",
        "§2 서사 아크 표의 Scene 3 등급을 C→B — 두 등급 표를 갈라 놓는다",
        "| 3   | 「지붕에서 한 말」              | 95초 | C    |",
        "| 3   | 「지붕에서 한 말」              | 95초 | B    |",
    ),
    (
        "c-doc",
        "`rahab_stigma` 거절 델타에 6:23 을 넣어 불변식 절을 빼앗는다",
        '| `rahab_stigma`        | 5     | 낙인 호칭 반복             | `"s5_close_on_6_23_6_27"`   | 6:17b · 6:22 · 6:25 · 약 2:25',
        '| `rahab_stigma`        | 5     | 낙인 호칭 반복             | `"s5_close_on_6_23_6_27"`   | 6:17b · 6:22 · 6:23 · 6:25 · 약 2:25',
    ),
    (
        "d-dest",
        "카드의 목적지를 실행 경로 표에 없는 id 로 바꾼다",
        '| `rahab_family_risk`   | 3     | 가족 생명 위협 · 목숨 대신 | `"s3_omit_2_13_2_14"`       |',
        '| `rahab_family_risk`   | 3     | 가족 생명 위협 · 목숨 대신 | `"s3_omit_2_13_2_99"`       |',
    ),
    (
        "d-name",
        "경로 이름은 그대로 두고 그 경로가 빼는 절만 2:14→2:15 — 이름이 거짓이 된다",
        '| 3     | `rahab_family_risk`                       | `"s3_omit_2_13_2_14"`     | 2:13 · 2:14',
        '| 3     | `rahab_family_risk`                       | `"s3_omit_2_13_2_14"`     | 2:13 · 2:15',
    ),
    # ── rev.11 후반 추가 ───────────────────────────────────────────────
    # rev.10 이 「9종으로 검사했다」고 적은 아홉 항목이다. **그 9종은 리포에 남지
    # 않은 일회성 스크립트였다** — 이 문서가 §7-2-a 의 「footer 6/6 기계 확인」을
    # 두고 지적한 것과 같은 형태다. 「14/14 전건 검출」이 실행으로 재현되려면
    # 아홉이 여기 있어야 한다. 그래서 옮겨 적는 것이 아니라 **다시 만들었다.**
    (
        "a-doc",
        "§5-1 표 Scene 3 의 `user_presence` 칸을 비운다 — 결손이 잡히는가",
        "| 3     | 지붕, 선 채로, 대화 **바깥 거리**                     |",
        "| 3     |                                                       |",
    ),
    (
        "e-doc",
        "§5-3-a Scene 1 의 등급을 C→Z — 등급이 아닌 값이 잡히는가",
        "| 1     | **C**            | `sex_work_stigma`",
        "| 1     | **Z**            | `sex_work_stigma`",
    ),
    (
        "b-doc",
        "§5-3 의 `trigger_scenes` 선언에서 Scene 5 를 뺀다 — 양방향이 갈라진다",
        "라합은 **5개 Scene 전부가 트리거를 갖는다** → `trigger_scenes: [1, 2, 3, 4, 5]`,",
        "라합은 **5개 Scene 전부가 트리거를 갖는다** → `trigger_scenes: [1, 2, 3, 4]`,",
    ),
    (
        "b-vocab",
        "신규/기존 표에서 `sex_work_stigma` 의 Scene 배정만 1·5→1·4 — 두 표가 갈라진다",
        "| `sex_work_stigma`              | **신규** | 1 · 5 |",
        "| `sex_work_stigma`              | **신규** | 1 · 4 |",
    ),
    (
        "b-spell",
        "코퍼스에 실재하는 `sexual_vulnerability_context` 를 **신규**로 라벨한다",
        "| `sexual_vulnerability_context` | 기존     | 2     |",
        "| `sexual_vulnerability_context` | **신규** | 2     |",
    ),
    (
        "c-exec",
        "Scene 5 의 실행 경로가 불변식 절 6:23 까지 빼게 만든다",
        '| 5     | `rahab_stigma` · `rahab_lineage_birth`    | `"s5_close_on_6_23_6_27"` | 6:17b · 6:22 · 6:25 · 약 2:25 **+** 마 1:5     |',
        '| 5     | `rahab_stigma` · `rahab_lineage_birth`    | `"s5_close_on_6_23_6_27"` | 6:17b · 6:22 · 6:23 · 6:25 **+** 마 1:5        |',
    ),
    (
        "c-cross",
        "생존 표에서 도달 불가 행 하나를 도달 가능으로 바꾼다 — 도달 행이 둘이 된다",
        "| `rahab_stigma` 만         | ❌   |",
        "| `rahab_stigma` 만         | ✅   |",
    ),
    (
        "c-union",
        "카드 표에서 `rahab_stigma`(Scene 5) 의 델타만 줄인다 — 경로 ≠ 카드 합집합",
        "| `rahab_stigma`        | 5     | 낙인 호칭 반복             | `\"s5_close_on_6_23_6_27\"`   | 6:17b · 6:22 · 6:25 · 약 2:25",
        "| `rahab_stigma`        | 5     | 낙인 호칭 반복             | `\"s5_close_on_6_23_6_27\"`   | 6:17b · 6:22 · 6:25",
    ),
    (
        "d-doc",
        "skip 목적지를 Scene 번호(정수)로 바꾼다 — 룻이 실제로 낸 결함 형태다",
        '| `rahab_stigma`        | 1     | 낙인 호칭                  | `"s1_no_epithet"`           |',
        '| `rahab_stigma`        | 1     | 낙인 호칭                  | `"2"`                       |',
    ),
]

ROW = re.compile(r"\[(PASS|FAIL|BLOCKED)\s*\]\s+([a-z-]+)")


def run(seed_abs: str, root: str = ROOT) -> tuple[int, dict[str, str]]:
    out = subprocess.run(
        [sys.executable, os.path.join(root, CHECKER_REL), seed_abs],
        capture_output=True, text=True, cwd=root,
    )
    return out.returncode, {m.group(2): m.group(1) for m in ROW.finditer(out.stdout)}


def mutate_corpus(dst_root: str) -> str:
    """`f-corpus` 변이 — 한 Scene 을 덮는 카드를 둘로 만든다.

    이 검사만 seed 가 아니라 `content/` 를 읽으므로 임시 ROOT 가 필요하다."""
    tgt = os.path.join(dst_root, "content/ruth/scene1.yml")
    txt = open(tgt, encoding="utf-8").read()
    m = re.search(r"^(\s*)-?\s*consent_card_id:\s*(\S+)\s*$", txt, re.M)
    if not m:
        raise LookupError("content/ruth/scene1.yml 에서 consent_card_id 를 못 찾았다")
    ind = m.group(1)
    open(tgt, "w", encoding="utf-8").write(
        txt[: m.end()] + f"\n{ind}consent_card_id: mutant_dup\n" + txt[m.end():]
    )
    return os.path.join(dst_root, SEED_REL)


def main() -> int:
    seed_abs = os.path.join(ROOT, SEED_REL)
    src = open(seed_abs, encoding="utf-8").read()

    base_rc, base = run(seed_abs)
    n_base_pass = sum(1 for v in base.values() if v == "PASS")
    print(f"기준선: rc={base_rc} · PASS {n_base_pass} / 검사 {len(base)}개")
    print("  (변이 판정은 '지목 항목이 FAIL 로 뒤집히는가' 하나로만 한다)\n")

    caught: list[str] = []
    missed: list[str] = []

    for target, why, before, after in MUTANTS:
        if src.count(before) != 1:
            print(f"❌ {target:9s} 변이 불가 — 원문이 {src.count(before)}회다 "
                  f"(정본이 바뀌었으면 이 파일도 고쳐야 한다)")
            missed.append(target)
            continue
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "SEED-RAHAB.md")
            open(p, "w", encoding="utf-8").write(src.replace(before, after))
            _, st = run(p)
        got = st.get(target, "없음")
        also = sorted(k for k, v in st.items()
                      if k != target and base.get(k) == "PASS" and v != "PASS")
        (caught if got == "FAIL" else missed).append(target)
        print(f"{'✅' if got == 'FAIL' else '❌'} {target:9s} → {got}   {why}")
        if also:
            print(f"      같이 뒤집힘: {', '.join(also)}")

    # f-corpus — 저장소를 통째로 임시 ROOT 에 복사한다
    target = "f-corpus"
    with tempfile.TemporaryDirectory() as d:
        for sub in ("content", "scripts", "docs"):
            shutil.copytree(os.path.join(ROOT, sub), os.path.join(d, sub))
        try:
            _, st = run(mutate_corpus(d), root=d)
            got = st.get(target, "없음")
        except LookupError as ex:
            got = f"변이 불가 — {ex}"
    (caught if got == "FAIL" else missed).append(target)
    print(f"{'✅' if got == 'FAIL' else '❌'} {target:9s} → {got}   "
          "룻 Scene 1 에 같은 Scene 을 덮는 두 번째 카드를 심는다")

    total = len(MUTANTS) + 1
    print(f"\n--- 검출 {len(caught)} / {total} ---")
    if missed:
        print(f"  미검출: {', '.join(missed)}")
        print("  ⚠️ 미검출 항목의 초록은 근거가 없다 — 그 항목이 무엇을 재는지 다시 봐야 한다.")
        return 1
    print("  ⚠️ 이 결과가 말하는 것은 **지목한 결함을 잡는다**뿐이다 — 잡지 않는 결함의 "
          "범위는 여전히 미측정이고, 변이 5종은 그 범위의 표본이 아니라 예시다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as ex:  # noqa: BLE001 — 실행 실패는 초록이 아니다
        sys.stderr.write(f"실행 실패: {type(ex).__name__}: {ex}\n")
        sys.exit(126)

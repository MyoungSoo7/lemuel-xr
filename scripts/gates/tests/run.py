#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""게이트 러너 **자신의** 테스트.

무엇을 증명하려는가
--------------------
게이트가 초록이라는 사실만으로는 아무것도 증명되지 않는다. 이 파이프라인이 네 번
실패한 방식이 전부 그거였다 — `-eq 5` 로 6-Scene 을 안 세고, `grep -q` 로 주석을
값으로 읽고, 빈 배열을 순회 0회로 통과시키고, `^R[23]_` 로 id 가 정확히 `R2` 인
축을 건너뛰었다. 전부 **PASS 를 찍으면서** 그랬다.

그래서 게이트마다 두 종류를 요구한다.
  GREEN : 규약을 지킨 픽스처 → 그 게이트가 PASS
  RED   : 그 게이트가 잡아야 하는 위반을 심은 픽스처 → **그 게이트가** FAIL

RED 가 vacuous 해지는 두 경로를 둘 다 막는다
--------------------------------------------
1. **다른 이유로 실패한 RED.** 케이스마다 `gate` 를 명시하고, 그 게이트가 FAIL 인지
   확인한 뒤 `reason_contains` 로 실패 사유까지 대조한다. 사유가 안 맞으면 실패다.
2. **부수 피해로 실패한 RED.** base 는 27/27 PASS 여야 한다(러너가 먼저 검사한다).
   그러면 RED 케이스에서 비-PASS 인 게이트 집합은 정확히 `{gate} ∪ also_nonpass`
   여야 한다. 하나라도 더 있으면 그 픽스처는 "의도한 게이트를 검증했다"고 말할 수 없다.
   불가피한 경우(YAML 파싱 불가 등)만 `delta_check: false` + `delta_note` 로 명시 면제한다.

BLOCKED 케이스도 테스트한다 — "빈 순회는 BLOCKED" 가 실제로 BLOCKED 로 나오는지.
BLOCKED 는 PASS 가 아니므로, 그것도 검증 대상이다.

사용법:  python3 scripts/gates/tests/run.py [--filter G5]
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("PyYAML 필요: pip3 install pyyaml\n")
    sys.exit(2)

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(HERE, "base")
CASES = os.path.join(HERE, "cases")
RUNNER = os.path.join(os.path.dirname(os.path.dirname(HERE)), "newchar_gates.py")

GREEN = "\033[32m"
RED = "\033[31m"
DIM = "\033[2m"
OFF = "\033[0m"


def run_runner(root: str) -> dict:
    cfg = os.path.join(root, "gate-config.yml")
    proc = subprocess.run(
        [sys.executable, RUNNER, "--config", cfg, "--root", root, "--json"],
        capture_output=True,
        text=True,
    )
    if not proc.stdout.strip():
        raise RuntimeError(f"러너 출력 없음\nstderr:\n{proc.stderr}")
    data = json.loads(proc.stdout)
    data["_exit"] = proc.returncode
    return data


def status_map(data: dict) -> dict[str, str]:
    return {r["gate"]: r["status"] for r in data["results"]}


def reason_blob(data: dict, gate: str) -> str:
    for r in data["results"]:
        if r["gate"] == gate:
            return r["reason"] + "\n" + "\n".join(r["details"])
    return ""


def build_case(case_dir: str, case: dict) -> str:
    tmp = tempfile.mkdtemp(prefix="gatetest-")
    root = os.path.join(tmp, "repo")
    shutil.copytree(BASE, root)
    overlay = os.path.join(case_dir, "overlay")
    if os.path.isdir(overlay):
        for dirpath, _dirs, files in os.walk(overlay):
            for fn in files:
                src = os.path.join(dirpath, fn)
                rel = os.path.relpath(src, overlay)
                dst = os.path.join(root, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
    patch = case.get("config_patch")
    if patch:
        cfg_path = os.path.join(root, "gate-config.yml")
        with open(cfg_path, encoding="utf-8") as fh:
            cfg = yaml.safe_load(fh) or {}
        cfg.update(patch)
        with open(cfg_path, "w", encoding="utf-8") as fh:
            yaml.safe_dump(cfg, fh, allow_unicode=True, sort_keys=False)
    return root


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--filter", default=None, help="케이스 이름 부분문자열")
    ap.add_argument("--keep", action="store_true", help="임시 트리 유지")
    a = ap.parse_args()

    print("=== 게이트 러너 자체 테스트 ===")

    # ── 0. GREEN 기준선: base 는 전 게이트 PASS 여야 한다 ────────────────────
    base = run_runner(BASE)
    base_status = status_map(base)
    base_nonpass = {g for g, s in base_status.items() if s != "PASS"}
    all_gates = list(base_status)
    if base_nonpass:
        print(f"{RED}[FAIL]{OFF} base 기준선이 전 게이트 PASS 가 아니다: "
              f"{sorted(base_nonpass)}")
        print("       base 가 초록이 아니면 RED 픽스처의 실패를 '내가 심은 위반 때문'이라고 "
              "말할 수 없다. 먼저 이걸 고쳐라.")
        return 1
    if base["_exit"] != 0:
        print(f"{RED}[FAIL]{OFF} base 종료코드 {base['_exit']} (0 이어야 한다)")
        return 1
    print(f"{GREEN}[OK]{OFF}   GREEN 기준선 base/ — {len(all_gates)}개 게이트 전부 PASS, 종료코드 0")
    print(f"{DIM}       (이 한 줄이 모든 게이트의 GREEN 픽스처다 — 규약을 지킨 입력에서 초록){OFF}")

    # ── 1. 케이스 ────────────────────────────────────────────────────────────
    names = sorted(os.listdir(CASES))
    if a.filter:
        names = [n for n in names if a.filter in n]
    ok_n = 0
    bad: list[str] = []
    covered_red: set[str] = set()
    green_cases = 0

    for name in names:
        case_dir = os.path.join(CASES, name)
        with open(os.path.join(case_dir, "case.yml"), encoding="utf-8") as fh:
            case = yaml.safe_load(fh)
        gate = case["gate"]
        expect = case["expect"]
        root = build_case(case_dir, case)
        try:
            data = run_runner(root)
        except Exception as e:  # noqa: BLE001
            bad.append(f"{name}: 러너 실행 실패 {e}")
            print(f"{RED}[FAIL]{OFF} {name}\n       러너 실행 실패: {e}")
            continue

        st = status_map(data)
        got = st.get(gate, "?")
        problems: list[str] = []

        if got != expect:
            problems.append(f"{gate} 기대 {expect} / 실제 {got}")
        want_sub = case.get("reason_contains")
        if want_sub and got == expect and want_sub not in reason_blob(data, gate):
            problems.append(
                f"사유 불일치 — {want_sub!r} 이(가) 사유에 없다. "
                f"이 픽스처는 다른 이유로 {expect} 가 났을 수 있다"
            )
        if expect != "PASS" and case.get("delta_check", True):
            nonpass = {g for g, s in st.items() if s != "PASS"}
            allowed = {gate} | set(case.get("also_nonpass") or [])
            extra = nonpass - allowed
            if extra:
                problems.append(
                    f"부수 피해 — {sorted(extra)} 도 비-PASS 다. "
                    f"이 픽스처가 {gate} 를 검증했다고 말할 수 없다"
                )
            missing = allowed - nonpass
            if missing - {gate}:
                problems.append(f"also_nonpass 로 선언했는데 PASS 다: {sorted(missing - {gate})}")
        if expect != "PASS" and data["_exit"] == 0:
            problems.append("종료코드 0 — FAIL/BLOCKED 가 있으면 비영이어야 한다")
        if expect == "PASS" and data["_exit"] != 0:
            # GREEN 케이스는 해당 게이트만 PASS 면 되고, 다른 게이트까지 요구하지 않는다
            pass

        if problems:
            bad.append(f"{name}: " + " / ".join(problems))
            print(f"{RED}[FAIL]{OFF} {name}  ({gate} {expect})")
            for p in problems:
                print(f"       - {p}")
        else:
            ok_n += 1
            if expect == "PASS":
                green_cases += 1
            else:
                covered_red.add(gate)
            note = case.get("planted", "")
            print(f"{GREEN}[OK]{OFF}   {name:<44} {gate:<8} {expect:<7} {DIM}{note}{OFF}")
        if not a.keep:
            shutil.rmtree(os.path.dirname(root), ignore_errors=True)
        else:
            print(f"{DIM}       tree: {root}{OFF}")

    # ── 2. 커버리지: RED 픽스처가 없는 게이트는 받지 않는다 ─────────────────
    if a.filter:
        # 부분 실행에서는 커버리지를 판정하지 않는다 — 안 돌린 게이트를
        # "RED 없음"으로 인쇄하면 그것도 원인 오귀속이다.
        print(f"\n--- 케이스 {ok_n}/{len(names)} 통과 (필터 {a.filter!r} — 커버리지 판정 생략) ---")
        return 1 if bad else 0
    print("\n--- 커버리지 ---")
    no_red = [g for g in all_gates if g not in covered_red]
    print(f"  게이트 {len(all_gates)}개 · RED 픽스처 보유 {len(covered_red)}개 · "
          f"추가 GREEN 케이스 {green_cases}개")
    if no_red:
        print(f"{RED}  RED 픽스처 없는 게이트: {no_red}{OFF}")
        print("  RED 대조군이 없는 게이트는 '막고 있다'는 증거가 없다.")
    print(f"\n--- 케이스 {ok_n}/{len(names)} 통과 ---")
    if bad or no_red:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""인물 미션의 VR asset manifest 로부터 AR manifest 를 생성한다.

여기서 말하는 AR 은 `xr_mode=ar` — *인물 미션을 패스스루로 보는 것* 이다.
docs/CROSS-MAPPING-VR-AR.md 의 "AR 1~7 가치(일상 습관)" 와는 다른 축이니
헷갈리지 말 것. 그쪽은 `ar_topic_*` 테이블이 담당한다.

AR 은 실제 방을 배경으로 쓴다. 그래서 VR manifest 에서 세 부류를 덜어낸다.

  1. `env_*`  — 방 전체 환경. 패스스루 위에 그리면 진짜 벽과 겹쳐 그려진다.
  2. `*_far`  — 원경 임포스터. 룸스케일에서는 볼 수 있는 거리가 아니다.
  3. 위 둘에만 붙던 텍스처 — 모델을 빼면 아무 데도 안 붙는데 용량만 먹는다.
     "남은 모델 중 어느 것과도 이름 토큰이 겹치지 않는 텍스처" 로 판정하고,
     오탐은 [KEEP_TEXTURES] 로 되돌린다.

그리고 두 가지를 더한다.

  1. `script_ar_anchor_placement` — 평면 히트테스트로 소품을 놓고 앵커에 고정.
  2. 묵상 씬(`env_meditation_*`)의 `shader_ar_passthrough_dim` — 어둠 속 묵상 씬은
     환경을 지우면 남는 게 없어서, 환경 모델 대신 패스스루를 어둡게 덮는
     레이어로 바꾼다. (헤드셋은 현실을 완전히 가릴 수 없으므로 '가림' 이
     아니라 '감광' 이다.)

멱등하다 — 다시 돌리면 같은 파일이 나온다. 생성 대상은
`backend/src/main/resources/manifests/{mission}/{device}/ar/scene{N}-v{version}.json`.

    python3 scripts/gen_ar_manifests.py            # AR_MISSIONS 전부
    python3 scripts/gen_ar_manifests.py moses      # 미션 지정
    python3 scripts/gen_ar_manifests.py --dry-run  # 무엇이 빠지는지만 출력
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFESTS = ROOT / "backend/src/main/resources/manifests"

# AR 을 여는 미션. backend application.yml 의 `lemuel.xr.ar-enabled-missions`
# 기본값과 같아야 한다 — 한쪽만 늘리면 게이트는 열렸는데 에셋이 없거나(404),
# 에셋은 있는데 게이트가 닫힌(400) 상태가 된다.
#
# 룻은 2026-08-20 에 **뺐다.** AR 은 씬을 사용자의 실제 방에 놓는데, 룻 Scene 4
# (밤·권력 비대칭)를 그렇게 놓는 것이 VR 과 같은 노출인지에 답할 사람이 없었다
# (`docs/RUTH-RUNTIME-SIGNOFF.md`). 목록에서 빼는 것만으로는 부족해서 이미 생성돼
# 있던 룻 AR 시드 15개도 같이 지웠다 — 목록만 고치면 환경변수 `XR_AR_MISSIONS` 한
# 줄로 되살아나고, 그때 에셋이 이미 있으면 아무 저항 없이 열린다.
# 되살리려면: 여기에 "ruth" 를 넣고 `python3 scripts/gen_ar_manifests.py ruth`.
AR_MISSIONS = ("joseph", "moses", "david", "jesus", "elijah", "solomon", "job")

# AR 을 지원하는 디바이스만. web 은 패스스루가 없어 대상이 아니다.
AR_DEVICES = ("quest3", "visionpro", "galaxyxr")

# 이름 토큰 판정에서 걸러낼 의미 없는 조각. 재질·해상도·자료구조 표기다.
NOISE_TOKENS = frozenset(
    {"tex", "model", "env", "pbr", "atlas", "ktx2", "glb", "1k", "2k", "4k", "in", "of"}
)

# 토큰 판정 오탐 되돌리기. 남은 모델과 이름이 안 겹치지만 실제로는 붙는 텍스처.
KEEP_TEXTURES = frozenset(
    {
        # moses scene5 — 남은 self 모델이 `..._sandals` 라 `shepherd` 와 안 겹치지만,
        # 이 인물의 의복 텍스처가 맞다.
        ("moses", 5, "tex_shepherd_garment"),
    }
)


def _tokens(asset_id: str) -> set[str]:
    return {t for t in re.split(r"[^a-z0-9]+", asset_id.lower()) if t and t not in NOISE_TOKENS}


def _is_environment(asset_id: str) -> bool:
    """AR 에서 무조건 덜어낼 자산 — 방 전체와 원경."""
    return asset_id.startswith("env_") or asset_id.endswith("_far")


def _to_ar_url(url: str, mission: str, device: str) -> str:
    """.../{mission}/{device}/v1.0.0/... → .../{mission}/{device}/ar/v1.0.0/..."""
    return url.replace(f"/{mission}/{device}/", f"/{mission}/{device}/ar/", 1)


def _ar_capabilities(caps: dict) -> dict:
    out = dict(caps or {})
    out["passthrough"] = True
    out["plane_detection"] = True
    out["anchors"] = True
    # 소품을 놓을 최소 여유 공간. 이보다 좁으면 클라이언트가 VR 로 안내한다.
    out["room_scale_min_meters"] = 2.0
    # 실제 방에서 던지는 거리는 VR 보다 짧아야 한다(가구·사람).
    if "throw_distance_meters" in out:
        out["throw_distance_meters"] = 1.5
    return out


def convert(doc: dict, device: str, dropped_log: list | None = None) -> dict:
    mission = doc["mission_id"]
    scene = doc.get("scene_number")
    manifest = doc["manifest"]

    # 1차 — 환경 모델·원경 임포스터를 걷어낸다.
    kept: dict[str, list] = {}
    dropped_env_ids: list[str] = []
    for kind, entries in manifest.items():
        survivors = []
        for e in entries:
            if _is_environment(e.get("id", "")):
                dropped_env_ids.append(e["id"])
            else:
                survivors.append(dict(e))
        kept[kind] = survivors

    # 2차 — 남은 모델 어디에도 안 붙는 텍스처를 걷어낸다.
    model_tokens: set[str] = set()
    for e in kept.get("models", []):
        model_tokens |= _tokens(e["id"])
    orphan_textures = []
    surviving_textures = []
    for e in kept.get("textures", []):
        keep = bool(_tokens(e["id"]) & model_tokens) or (mission, scene, e["id"]) in KEEP_TEXTURES
        (surviving_textures if keep else orphan_textures).append(e)
    if "textures" in kept:
        kept["textures"] = surviving_textures

    if dropped_log is not None:
        dropped_log.append(
            {
                "mission": mission,
                "scene": scene,
                "device": device,
                "env": dropped_env_ids,
                "textures": [e["id"] for e in orphan_textures],
            }
        )

    for entries in kept.values():
        for e in entries:
            if "url" in e:
                e["url"] = _to_ar_url(e["url"], mission, device)

    scripts = kept.setdefault("scripts", [])
    base = f"https://cdn.r2.dev/lemuel-xr/{mission}/{device}/ar/v{doc['version']}/scripts"
    scripts.append(
        {
            "id": "script_ar_anchor_placement",
            "url": f"{base}/ArAnchorPlacement.cs.bundle",
            "size_bytes": 18000,
            "entry_class": "ArAnchorPlacement",
        }
    )
    if any("meditation" in i for i in dropped_env_ids):
        scripts.append(
            {
                "id": "shader_ar_passthrough_dim",
                "url": f"{base}/ArPassthroughDim.shader.bundle",
                "size_bytes": 12000,
                "entry_class": "ArPassthroughDim",
            }
        )

    total = sum(e.get("size_bytes", 0) for entries in kept.values() for e in entries)

    return {
        "mission_id": mission,
        "scene_number": scene,
        "device_type": device,
        "xr_mode": "ar",
        "version": doc["version"],
        "capabilities_min": _ar_capabilities(doc.get("capabilities_min", {})),
        "manifest": kept,
        "audio_locale": doc.get("audio_locale"),
        "total_size_bytes": total,
        "cdn_base_url": doc.get("cdn_base_url"),
    }


def main(argv: list[str]) -> int:
    dry_run = "--dry-run" in argv
    wanted = [a for a in argv if not a.startswith("-")] or list(AR_MISSIONS)
    unknown = [m for m in wanted if m not in AR_MISSIONS]
    if unknown:
        print(f"AR 대상 미션이 아님: {', '.join(unknown)} (가능: {', '.join(AR_MISSIONS)})", file=sys.stderr)
        return 2

    dropped_log: list = []
    written = 0
    vr_total = ar_total = 0
    for mission in wanted:
        for device in AR_DEVICES:
            src_dir = MANIFESTS / mission / device
            if not src_dir.is_dir():
                print(f"skip: {src_dir} 없음", file=sys.stderr)
                continue
            out_dir = src_dir / "ar"
            for src in sorted(src_dir.glob("scene*.json")):
                doc = json.loads(src.read_text())
                ar = convert(doc, device, dropped_log)
                vr_total += doc.get("total_size_bytes", 0)
                ar_total += ar["total_size_bytes"]
                if not dry_run:
                    out_dir.mkdir(exist_ok=True)
                    (out_dir / src.name).write_text(
                        json.dumps(ar, indent=2, ensure_ascii=False) + "\n"
                    )
                written += 1

    if dry_run:
        for d in dropped_log:
            if d["device"] != AR_DEVICES[0]:
                continue  # 기기별로 같은 판정이라 하나만 보인다
            print(f"{d['mission']} scene{d['scene']}: env={d['env']} tex={d['textures']}")
        print(f"[dry-run] {written}개 대상 · {vr_total / 1e6:.0f}MB → {ar_total / 1e6:.0f}MB")
        return 0

    print(f"AR manifest {written}개 생성 · {vr_total / 1e6:.0f}MB → {ar_total / 1e6:.0f}MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

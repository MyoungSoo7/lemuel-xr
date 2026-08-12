#!/usr/bin/env python3
"""PWA 아이콘 생성기 — frontend/public/icons/*.png 를 만든다.

왜 스크립트인가. 아이콘을 바이너리로 커밋해 두면 **어떻게 만들어졌는지 아무도 모른다** —
색을 하나 바꾸려면 원본 디자인 파일을 찾아야 하고, 대개 그 파일은 없다. 여기서는 색이
`frontend/src/app/globals.css` 의 `@theme` 값과 같은 숫자이고, 이 파일이 그 출처다.

의존성 없음(표준 라이브러리 zlib 만 쓴다). Pillow·ImageMagick·rsvg 가 이 맥에 없어서
per-pixel 로 그린다 — 2배 슈퍼샘플링으로 계단만 지운다.

모티프: 지평선 위로 올라오는 해. 48px 로 줄여도 형태가 남는 것만 골랐다.

`maskable` 아이콘은 플랫폼이 바깥을 잘라낼 수 있으므로 내용물을 가운데 80% 안에 둔다
(W3C manifest 사양의 safe zone 권고).

  python3 scripts/gen_frontend_icons.py

⚠️ 실행하면 기존 png 를 덮어쓴다. 색을 바꿀 때만 돌릴 것.
"""

from __future__ import annotations

import os
import struct
import zlib

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "frontend", "public", "icons")

# globals.css @theme 와 같은 값이다. 한쪽만 바꾸면 홈화면 아이콘과 앱이 서로 다른 색이 된다.
DEEP = (0x1A, 0x1D, 0x24)
WARM = (0xC2, 0xA8, 0x78)

SS = 2  # 슈퍼샘플링 배율


def draw(size: int) -> bytearray:
    n = size * SS
    sun_cx, sun_cy, sun_r = n * 0.50, n * 0.44, n * 0.20
    hor_y, hor_h = n * 0.66, n * 0.045
    hor_x0, hor_x1 = n * 0.18, n * 0.82

    # 슈퍼샘플 버퍼 — 0.0(deep) ~ 1.0(warm) 커버리지
    cov = [0.0] * (n * n)
    for y in range(n):
        for x in range(n):
            px, py = x + 0.5, y + 0.5
            hit = (px - sun_cx) ** 2 + (py - sun_cy) ** 2 <= sun_r**2
            if not hit:
                hit = hor_x0 <= px <= hor_x1 and hor_y <= py <= hor_y + hor_h
            if hit:
                cov[y * n + x] = 1.0

    # 다운샘플 + 색 보간
    rows = bytearray()
    for y in range(size):
        rows.append(0)  # PNG filter type 0
        for x in range(size):
            acc = 0.0
            for dy in range(SS):
                for dx in range(SS):
                    acc += cov[(y * SS + dy) * n + (x * SS + dx)]
            a = acc / (SS * SS)
            for c in range(3):
                rows.append(round(DEEP[c] + (WARM[c] - DEEP[c]) * a))
    return rows


def png(size: int, raw: bytearray) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)  # 8bit truecolor
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for size in (192, 512, 180):
        name = "apple-touch-icon.png" if size == 180 else f"icon-{size}.png"
        path = os.path.join(OUT, name)
        with open(path, "wb") as fh:
            fh.write(png(size, draw(size)))
        print(f"  {os.path.relpath(path, REPO)}  {size}×{size}")


if __name__ == "__main__":
    main()

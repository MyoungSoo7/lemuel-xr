# 미디어·에셋 계획

> 2026-05-21 작성. lemuel-xr 의 시각·청각·3D 에셋 단계별 로드맵. 텍스트 + R3F 도형 placeholder 단계 → 이미지 → 3D 모델 → 오디오·Lottie 순.

## 단계 1 — Scene 배경 이미지 ✅ (2026-05-21 완료)

**상태**: Imagen 4.0 fast (Google) 로 Scene 1~5 각 1장 생성. `frontend/public/images/scenes/{1..5}.jpg`. 16:9, 약 1.5 MB/장.

**프롬프트 톤** (이론적 기반: lemuel-xr-theology-tone 스킬)
- "no faces visible / no figures" 강제 — 인물 신학 해석 분쟁 회피
- "painterly biblical aesthetic" — 사진 실사 X (윤리·신앙적 분쟁 회피)
- 색 톤: 금색 (--color-primary #c2a878) 과 어두운 deep tone 일치

**재생성 방법**: `python3 scripts/generate_scenes.py` (GEMINI_API_KEY 환경변수 필요)

## 단계 2 — 인물 카드 일러스트 (예정, 신중)

요셉·모세·다윗·예수 카드용. 신학 분쟁 회피:
- 정면 얼굴 ❌ (정확한 외모 추정 분쟁)
- 실루엣 / 뒷모습 / 상징 (지팡이·왕관·곡식·십자가) 위주
- 예수는 *상징* 으로만 (lemuel-xr-theology-tone R3 부활 회복 압박 회피와 정렬)

**진행 시점**: Track B 4 인물 시나리오가 다 작성되고 신학 자문 검수 통과 후. Phase 2.

## 단계 3 — 3D 모델 (예정, ROI 검토 후)

**MVP 웹 단계엔 R3F primitives** (현재) 가 *충분* 한 visual scaffold. Real .glb 모델은 Unity Phase 2 에서 본격.

**후보 소스 (CC0 / 무료)**
- [Polyhaven Models](https://polyhaven.com/models) — antique_ceramic_vase, barrel 등 49 매칭
- [Quaternius Stylized](https://quaternius.com/) — low-poly biblical characters 적합
- [Sketchfab Free + CC0 filter](https://sketchfab.com/search?features=downloadable&licenses=72fa4691114262a9fbabf093d0c98a17&type=models)

**주의**: Polyhaven gltf 는 *별도 텍스처 파일 N개* 필요 (jpg/png). Vercel/standalone 빌드에 다 포함시켜야. .glb (binary, embed 텍스처) 가 더 편함.

**3D 모델 → R3F 로드 예시** (구현 시):
```tsx
import { useGLTF } from "@react-three/drei";
const { scene } = useGLTF("/models/grain_sack.glb");
return <primitive object={scene} />;
```

## 단계 4 — 오디오·Lottie (D, 단기 미진행)

### 4-1. TTS 내레이션 (Coqui XTTS-v2)
**상태**: 코드/Docker 다 작성됨. PVC 가 david 노드에 묶여 scale 0 상태. 정공법:
1. local-path-provisioner PVC 의 node-affinity 우회 (PVC 삭제·재생성)
2. 또는 PVC 없이 emptyDir 로 변경 + 외부 R2 캐시 사용

### 4-2. Lottie 애니메이션 (Scene 1 7 소 영상 대체)
LottieFiles 무료 라이브러리 활용:
- "ancient cattle" / "biblical dream" 검색
- `lottie-react` 패키지 + JSON 임포트

### 4-3. BGM (Scene 별 ambient loop)
[Pixabay Music](https://pixabay.com/music/) free, 잠언 / 명상 / 묵상 검색.
파일: `frontend/public/audio/scene-{n}-bgm.mp3` (5분 loop)

## 라이선스 추적표

| 에셋 종류 | 출처 | 라이선스 | 출시 시 표기 필요 |
|---|---|---|---|
| Scene 배경 1~5 | Google Imagen 4.0 fast | 자체 생성, Google ToS 따름 | "Generated with Google Imagen" |
| 3D 모델 (예정) | Polyhaven | CC0 | 표기 불필요, 권장 |
| BGM (예정) | Pixabay | Pixabay Content License | 표기 불필요, 권장 |
| TTS 음성 | Coqui XTTS-v2 | Coqui Public Model License (research only) | ⚠️ **상업 사용 시 라이선스 협상** |
| 본문 (현대인의 성경) | 생명의말씀사 | 저작권 | ⚠️ 공개 출시 전 라이선스 협의 필수 |

## 다음 액션

1. ✅ Scene 배경 5장 (완료)
2. ⏳ 인물 카드 일러스트 — Track B 4 인물 시나리오 완성 + 신학 자문 후
3. ⏳ TTS PVC 우회 (david 의존 제거)
4. ⏳ Lottie 1개 시범 (Scene 1) 도입해 사용자 반응 측정
5. ⏳ BGM Scene 별 1개씩 (Pixabay 무료)

## 비용 예상 (전 단계 완료 시점)

| 항목 | 비용 |
|---|---|
| Scene 배경 5장 (재생성) | Gemini API 무료 tier 충분 |
| 인물 일러스트 4장 | Imagen $0.04 × 4 = $0.16 |
| 3D 모델 | $0 (Polyhaven CC0) |
| BGM 5곡 | $0 (Pixabay free) |
| TTS | $0 (자체 호스팅) |
| **합계** | **~$0.16** |

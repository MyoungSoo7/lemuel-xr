# Contributing to lemuel-xr

> 이 프로젝트는 Phase 0 (시장 검증) 단계. 코드보다 *문서·인터뷰·기획* 작업이 많습니다.

## 시작하기

```bash
git clone https://github.com/MyoungSoo7/lemuel-xr.git
cd lemuel-xr
cat README.md         # 5분 개요
cat CLAUDE.md         # 작업 가이드 + 컨벤션
cat docs/PLAN.md      # 전체 기획서
cat docs/MVP-JOSEPH.md # MVP 요셉 게임 상세
```

## 협업 흐름

### 1. 이슈로 작업 단위 정의

- 모든 작업은 GitHub Issue 로 먼저 등록
- 라벨로 카테고리 표시: `docs`, `theology`, `design`, `code`, `interview`, `research`
- assignee 명확히 (혼자 잡지 말고 합의)

### 2. 브랜치

- `master` — 언제든 머지 가능한 상태 유지. 직접 push 금지
- `docs/<topic>` — 문서 작업 (예: `docs/interview-template`)
- `feature/<name>` — 기능/코드 작업 (예: `feature/joseph-scene-1`)
- `theology/<topic>` — 신학 검토 작업 (예: `theology/joseph-genesis-41`)
- 브랜치 이름은 *한 문장 요약* 형태로 명확하게

### 3. PR

- 작은 단위로 자주 — 1 PR ≤ 300 줄 권장
- 제목: `<type>: <한 줄 요약>` (예: `docs: 요셉 V1 시나리오 5장 추가`)
- 본문 — 무엇을·왜·테스트 방법 3줄 이상
- 셀프 리뷰 후 다른 사람 리뷰 요청

### 4. 커밋

- 한국어 커밋 메시지 OK (이 프로젝트는 한국 시장 우선)
- 형식: `<type>(<scope>): <요약>`
- type: `feat`, `fix`, `docs`, `refactor`, `theology`, `chore`
- 예시: `docs(joseph): 시나리오 5장 분기 추가`

## 문서 컨벤션

- Markdown (Word/PPT 지양)
- 파일명 영문 + 소문자 + 하이픈 (`docs/interview-template.md`)
- 인터뷰 노트: `docs/interviews/YYYY-MM-DD-<이니셜>.md`
- 신학 검토: `docs/theology-review/<주제>.md`
- 한국어 + 영어 용어 혼용 OK (예: "수정필요", "TODO", "MVP")

## 신학적 검증 원칙

> *AI 가 생성한 묵상·해석은 자동 출판 금지.*

- 출력 → 검토자 1명 이상 reviewing → 게시
- 의심 시 `theology/` 브랜치에 올리고 자문 받기
- 환각 가능성 — 항상 *성경 본문 직접 인용* 으로 보완

## 코드 (V1.0 부터)

- Backend: Spring Boot 4 + Kotlin
- Frontend: React Native
- 헥사고날 아키텍처 (settlement 프로젝트 패턴 재사용)
- 테스트 커버리지 80%+ 목표

## 의사소통

- **이슈**: 작업 단위
- **PR**: 코드/문서 변경
- **Discussions**: 자유로운 아이디어 (GitHub Discussions 활성화 권장)
- **외부**: Telegram 으로 긴급 또는 비공식 논의

## 질문이 있다면

- 이슈로 등록 (`question` 라벨)
- 또는 [@MyoungSoo7](https://github.com/MyoungSoo7) mention

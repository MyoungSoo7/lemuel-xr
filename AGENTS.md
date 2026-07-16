# AGENTS.md — AI 에이전트 작업 가이드

> AI 코딩 에이전트(Claude Code 등)가 lemuel-xr 에서 작업할 때 따르는 규약.
> 사람용 협업 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md), 프로젝트 지식은 [`CLAUDE.md`](CLAUDE.md).
> 하네스(문서·에이전트 체계) 유지보수 규칙은 [`HARNESS.md`](HARNESS.md).

**우선순위**: 사용자 직접 지시 > `CLAUDE.md` / `AGENTS.md` > 에이전트 기본 동작. 충돌 시 상위가 이긴다.

---

## 1. 처음 오면 이 순서로 읽는다

1. [`README.md`](README.md) — 한 줄 컨셉·듀얼 트랙.
2. [`CLAUDE.md`](CLAUDE.md) — 미션·정체성·톤·안전 정책 (**정적 지식**).
3. [`SPEC.md`](SPEC.md) — 기능명세. [`STRUCTURE.md`](STRUCTURE.md) — 코드 구조. [`ARCHITECTURE.md`](ARCHITECTURE.md) — 아키텍처.
4. 심화가 필요하면 [`docs/`](docs/) (FUNCTIONAL-SPEC · BACKEND-ARCHITECTURE · AI-ARCHITECTURE · DB-SCHEMA · governance/…).

---

## 2. 빌드 · 테스트 · 실행

**Gradle 래퍼(`gradlew`) 없음** — 시스템 `gradle` 사용.

```bash
# 백엔드 (Spring Boot 4 + Kotlin, JDK 25 툴체인)
cd backend
gradle bootJar -x test         # CI 와 동일 (jar 빌드)
gradle test                    # 테스트 (JUnit, 61개 .kt 테스트)
gradle build                   # 전체

# AI / TTS 사이드카 (Python)
cd ai  && pip install -r requirements.txt && python app.py
cd tts && pip install -r requirements.txt && python app.py

# 프론트엔드 (Next.js)
cd frontend && npm install && npm run dev

# 로컬 스택 (postgres + ai + tts)
docker compose up -d
```

CI(`.github/workflows/ci.yml`): `changes` → `backend`(`gradle --no-daemon bootJar -x test`) · `tts` · `ai` · `frontend` 병렬. paths-filter 로 변경된 영역만 실행.

---

## 3. 코딩 컨벤션

### 백엔드 (Kotlin)
- **100% Kotlin**. Java 파일 추가 금지, Lombok 금지. `.kt` 만.
- **헥사고날(ports & adapters)** 준수 — 컨텍스트별 `domain` / `application`(+`port/out`) / `adapter`(`in/web`, `out/persistence·metrics·sidecar`).
  - `domain` 은 프레임워크 무의존 순수 Kotlin.
  - `application` 은 `port/out` 인터페이스만 안다 — **application 레이어에서 JPA 직접 사용 금지**.
  - `adapter/out/persistence` 가 `*Port` 구현 + 도메인 ↔ `*JpaEntity` 매핑.
  - 도메인 모델을 web DTO 로 노출 금지 (DTO leak 방지).
- **DB 마이그레이션**: `backend/src/main/resources/db/migration/`. V1~V12 정수 유지, **V13 부터 `V{YYYYMMDDhhmmss}__` 타임스탬프** (병행 작성 충돌 회피). 익명 우선·pgcrypto·벡터 분리 원칙.
- JDK 25 툴체인 / Kotlin JVM 타깃 24.

### 문서
- **한국어**로 작성 (코드 주석은 한/영 혼용 OK). Word/PPT 대신 markdown 일원화.

### 프론트엔드
- Next.js App Router. 백엔드 호출은 `/api/[...path]` 프록시 경유.

---

## 4. 반드시 통과해야 하는 게이트 (프로젝트 요구사항)

이 게이트들은 *구현 도구와 무관하게* lemuel-xr 의 요구사항이다. Claude Code 에서는 아래 서브에이전트/스킬로 실행된다.

| 게이트 | 무엇을 막나 | Claude Code 구현 |
|---|---|---|
| **헥사고날 경계** | 의존 방향 위반·layer leak·DTO leak·application 안 JPA | `lemuel-hexagonal-enforcer` agent |
| **신학 정통성** | 영지주의·뉴에이지 해석, 분쟁 지점 미표기 | `lemuel-theology-reviewer` agent · `lemuel-xr-theology-tone` skill |
| **정신건강 안전** | 자해 키워드 false negative·고난 가스라이팅·부활 회복 압박·트라우마 트리거·AI opt-out | `lemuel-mental-health-safety` agent · `lemuel-xr-mental-health-safety` skill |
| **Flyway 컨벤션** | 번호 규칙·스키마 설계 원칙 위반 | `lemuel-xr-flyway-migration` skill |
| **시퀀스 다이어그램** | 새 endpoint/이벤트 문서 누락 | `lemuel-xr-mermaid-sequence` skill |
| **시나리오 설계** | 인물 미션 형식·3차원 매핑·신학 검증형 구조 | `lemuel-scenario-designer` agent |

- **사용자 노출 콘텐츠**(묵상·Scene 분기·시스템 프롬프트·trigger_warning ≥ medium)는 **신학 + 임상 2-축 approve** 가 PUBLISHED 조건. 상세 [`SPEC.md §7`](SPEC.md) · [`docs/governance/CLINICAL-REVIEW.md`](docs/governance/CLINICAL-REVIEW.md).
- AI 생성 묵상·해석은 **자동 출판 금지** — 출력 → 검토 → 게시, *"AI 보조"* 표시 필수.

---

## 5. 안전 불변식 (절대 건드리지 말 것)

미션·톤 변경과 무관하게 항상 가동되는 4-layer:
1. Disclaimer Gate (*치료 도구 아님 / 예방 영적 교육*).
2. CrisisLockout — 위기 키워드 → 1393 즉시 전달 (`safety/application/CrisisKeywordScanner`).
3. AI 라벨링 — 모든 LLM 응답에 *AI 보조 — 본문은 성경 참조*.
4. `X-Lemuel-Disclaimer: not-medical-device` 응답 헤더.

**금지 어휘/행위**: 치료·진단·상담·처방·의료 권고, 자살 사고에 대한 직접 조언. 이 영역은 앱이 담당하지 않는다(1393·전문가).

---

## 6. Do / Don't

**Do**
- 작은 단위 커밋/PR (≤ 300줄 권장). 제목 `<type>: <한 줄 요약>`.
- 새 기능 → 관련 게이트 에이전트 통과 후 머지.
- 코드 변경 시 `SPEC/STRUCTURE/ARCHITECTURE` 및 `docs/` 동기화 여부 확인.
- 트랙 A(감정 회복 정적)와 트랙 B(서사 게임) UI 를 *섞지 않는다*.

**Don't**
- Java/Lombok 추가, 헥사고날 경계 위반, application 레이어 JPA 직접 사용.
- 안전 4-layer 우회·약화.
- 신학/임상 미검토 콘텐츠 자동 게시.
- 어음/저작권 미확보 자산 추가.
- `main` 직접 오염 (작업은 브랜치 → 리뷰/게이트 → 머지).

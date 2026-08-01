# ARCHITECTURE.md — 아키텍처

> CLAUDE.md 에서 분리한 **아키텍처** 문서. 기술 스택·헥사고날 구조·사이드카·안전 아키텍처·배포.
> 코드 구조는 [`STRUCTURE.md`](STRUCTURE.md), 기능은 [`SPEC.md`](SPEC.md).
> 심화: [`docs/BACKEND-ARCHITECTURE.md`](docs/BACKEND-ARCHITECTURE.md) · [`docs/AI-ARCHITECTURE.md`](docs/AI-ARCHITECTURE.md) · [`docs/XR-INTEGRATION.md`](docs/XR-INTEGRATION.md) · [`docs/DB-SCHEMA.md`](docs/DB-SCHEMA.md) · [`docs/SEQUENCE-DIAGRAMS.md`](docs/SEQUENCE-DIAGRAMS.md)

---

## 1. 기술 스택

| 구분 | 기술 | 비고 |
|---|---|---|
| Backend | **Spring Boot 4.0.4 + Kotlin 2.2.20** | 100% Kotlin (Java 0개, `.kt` 305개), Lombok 완전 제거 |
| JDK / 바이트코드 | JDK 25 툴체인 / Kotlin JVM 타깃 24 | JVM 25 로드 정상 |
| 빌드 | Gradle Kotlin DSL (`build.gradle.kts`) | gradlew 래퍼 없음 → `gradle bootJar -x test` |
| AI 사이드카 | Python (`ai/`) — OpenAI / Gemini / Claude | WebClient proxy |
| TTS 사이드카 | Python (`tts/`) | WebClient proxy |
| Game / XR | Unity 6 LTS | 트랙 B |
| Frontend | React Native / Next.js | 웹 클라이언트 |
| DB | PostgreSQL 16 + **pgvector** | 성경 임베딩 유사도 검색 |

---

## 2. 시스템 컴포넌트

```
                    ┌─────────────────────────────┐
                    │  Frontend (Next.js)         │
                    │  /api/[...path] 프록시       │
                    └──────────────┬──────────────┘
                                   │ REST
                    ┌──────────────▼──────────────┐
   Unity 6 (XR) ───►│  Backend (Spring Boot 4)    │
   트랙 B 게임      │  헥사고날 · 15 바운디드 컨텍스트 │
                    └───┬───────────┬──────────┬──┘
              WebClient │           │ JPA      │ WebClient
                        ▼           ▼          ▼
              ┌──────────────┐ ┌─────────┐ ┌──────────────┐
              │ ai/ (Python) │ │ Postgres│ │ tts/ (Python)│
              │ LLM proxy    │ │+pgvector│ │ 음성 합성     │
              └──────┬───────┘ └─────────┘ └──────────────┘
                     ▼
         OpenAI · Gemini · Claude
```

**사이드카 원칙**: AI/TTS 는 Python 사이드카로 분리하되, 외부에 직접 노출하지 않고 백엔드 `adapter/out/sidecar` 를 통해서만 호출한다. LLM 키·프로바이더 선택·프롬프트 가드가 모두 백엔드 경계 안에서 통제된다.

---

## 3. 헥사고날 아키텍처 (Ports & Adapters)

각 바운디드 컨텍스트가 동일한 3-레이어를 따른다:

```
        ┌───────────── adapter/in/web ─────────────┐
        │  REST Controller (DTO ↔ 유스케이스 호출)   │
        └───────────────────┬──────────────────────┘
                            │
        ┌────────── application ───────────┐
        │  *UseCase (오케스트레이션)          │
        │  port/out/*Port (아웃바운드 인터페이스)│
        └───────┬───────────────────┬───────┘
                │                   │
        ┌───────▼────────┐  ┌───────▼─────────────────┐
        │ domain (순수)   │  │ adapter/out/…            │
        │ 프레임워크 무의존 │  │ persistence · metrics ·  │
        │                │  │ sidecar (Port 구현)       │
        └────────────────┘  └──────────────────────────┘
```

**의존 방향 규칙**:
- `domain` 은 아무것에도 의존하지 않는 순수 Kotlin 모델.
- `application` 은 `domain` + `port/out` 인터페이스만 안다. JPA/Spring 세부를 모른다.
- `adapter/out/persistence` 가 `*Port` 를 구현하고, 순수 도메인 모델 ↔ `*JpaEntity` 를 매핑.
- `adapter/in/web` 이 유스케이스를 호출. 도메인 모델이 web DTO 로 새어나가지 않게 매핑.

> 이 경계는 `lemuel-hexagonal-enforcer` agent 로 PR/commit 전 검증(의존 방향 위반·layer leak·DTO leakage·application 안 JPA 직접 사용 방지).

**컨텍스트 목록**: `auth · emotion · scripture · content · values · journey · game · recovery · safety · ai · tts · asset · analytics · outbox` (+ `common` · `config` 공통 인프라).

---

## 4. 데이터 아키텍처

- **PostgreSQL 16 + pgvector** — 성경 본문 임베딩을 벡터 컬럼으로 저장, 유사도 검색으로 관련 구절 매칭.
- **Flyway 마이그레이션** — `backend/src/main/resources/db/migration/` (V1~V12 정수 번호 + V13~ `V{YYYYMMDDhhmmss}__` 타임스탬프 컨벤션, multi-agent 병행 작성 충돌 회피). 현재 25개.
- **설계 원칙**: 익명 우선 · 헥사고날 분리 · JSONB 적극 · 벡터 분리 · 삭제 가능 · pgcrypto 암호화.
- 상세: [`docs/DB-SCHEMA.md`](docs/DB-SCHEMA.md).

---

## 5. 안전 아키텍처 (4-layer — 항상 가동)

미션 변경과 무관하게 깔리는 안전선. 코드상 `safety` 컨텍스트 + `common/web` 필터에 구현.

1. **Disclaimer Gate** — *치료 도구 아님 / 예방 영적 교육* 고지.
2. **CrisisLockout** — `safety/application/CrisisKeywordScanner` 가 위기 키워드 매칭 시 109 즉시 전달, `SafetyAlert` 기록 + Micrometer 메트릭.
3. **AI 라벨링** — 모든 LLM 응답에 *AI 보조 — 본문은 성경 참조*.
4. **ResponseHeaderFilter** — 응답에 `X-Lemuel-Disclaimer: not-medical-device` 헤더.

**게시 게이트 (신학 + 임상 병렬 검증)**: AI 생성 사용자 노출 콘텐츠는 자동 출판 금지 — 신학·임상 2-축 approve 가 PUBLISHED 조건. 상세는 [`SPEC.md §7`](SPEC.md) · [`docs/governance/CLINICAL-REVIEW.md`](docs/governance/CLINICAL-REVIEW.md).

---

## 6. 이벤트 & 관측성

- **Outbox** — `outbox` 컨텍스트로 도메인 이벤트 신뢰 발행 (트랜잭션 아웃박스 패턴).
- **Metrics** — 각 컨텍스트 `adapter/out/metrics` 에서 Micrometer 로 안전 알림·LLM 사용량 등 집계.
- **Analytics** — `analytics` 컨텍스트 + DB 분석 뷰(Flyway `V12__analytic_views.sql`).
- **Tracing** — AI 사이드카 `ai/tracing.py`.

---

## 7. 배포 (로컬 스택)

`docker-compose.yml` 서비스:

| 서비스 | 역할 |
|---|---|
| `postgres` (`lemuel_xr_pg`) | PostgreSQL 16 + pgvector |
| `ai` | Python LLM proxy 사이드카 |
| `tts` | Python TTS 사이드카 |

- 백엔드는 `gradle bootJar -x test` 로 빌드, Dockerfile build self-test 로 검증.
- 원격 운영/배포는 GitOps (lemuel-xr `main` + helm-deploy `master` + ArgoCD Image Updater), 공개 도메인 `xr.lemuel.co.kr`. `kubectl` 직접 접근은 홈 LAN(192.168.219.101) 전용.

---

## 8. 아키텍처 결정 요약

| 결정 | 이유 |
|---|---|
| 100% Kotlin, Lombok 제거 | 널 안전·간결성, Java/Lombok 이중 스택 제거 |
| 헥사고날 per-context | 도메인 순수성 유지, 컨텍스트별 독립 진화, 경계 자동 검증 가능 |
| AI/TTS 사이드카 분리 | Python AI 생태계 활용 + 키/프롬프트 가드를 백엔드 경계 안에 통제 |
| pgvector | 별도 벡터 DB 없이 Postgres 안에서 성경 임베딩 검색 |
| 안전선을 코드 레이어로 고정 | 미션/톤 변경과 무관하게 항상 가동되는 불변 안전 보장 |

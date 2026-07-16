# HARNESS.md — 문서·에이전트 하네스 유지보수

> lemuel-xr 의 *문서 체계 + AI 에이전트 게이트* 를 어떻게 구성·유지하는지에 대한 메타 문서.
> 작업 규약 자체는 [`AGENTS.md`](AGENTS.md), 프로젝트 지식은 [`CLAUDE.md`](CLAUDE.md).

---

## 1. 하네스란

lemuel-xr 의 하네스 = **사람과 AI 에이전트가 같은 규칙으로 일하게 만드는 문서·게이트 묶음**. 세 층위로 나뉜다.

```
메타     HARNESS.md          ← 본 문서. 하네스 자체의 구조·유지 규칙
  │
규약     AGENTS.md           ← 에이전트 작업 규약 (빌드·컨벤션·게이트·Do/Don't)
  │      CONTRIBUTING.md     ← 사람 협업 규칙 (이슈·브랜치·PR)
  │
지식     CLAUDE.md           ← 정적 프로젝트 지식 (미션·정체성·톤·안전 정책)
  │      README.md           ← 한 줄 컨셉·듀얼 트랙
  │
요약     SPEC.md             ← 기능명세 (CLAUDE.md 에서 분리)
  │      STRUCTURE.md        ← 리포 구조
  │      ARCHITECTURE.md     ← 아키텍처
  │
심화     docs/**             ← 설계·거버넌스·콘텐츠 원장
```

**단일 진실 출처(SSOT) 규칙**: 한 사실은 한 문서에만 정본으로 둔다. 나머지는 링크. 예 — 안전 4-layer 의 정본은 `CLAUDE.md`/`SPEC.md §7`, 다른 문서는 그리로 링크만.

---

## 2. 문서 지도 & 책임

| 문서 | 성격 | 갱신 시점 |
|---|---|---|
| `CLAUDE.md` | 정적 지식 (미션·톤·안전·11주제·기술스택) | 미션/정체성/정책이 바뀔 때만 |
| `SPEC.md` | 기능명세 | 기능 추가·변경 시 |
| `STRUCTURE.md` | 리포 구조 | 디렉토리/컨텍스트 구조 변경 시 |
| `ARCHITECTURE.md` | 아키텍처 | 기술스택·경계·배포 변경 시 |
| `AGENTS.md` | 에이전트 규약 | 빌드·컨벤션·게이트 변경 시 |
| `HARNESS.md` | 메타 | 문서/에이전트 체계 자체가 바뀔 때 |
| `docs/**` | 심화 원장 | 해당 주제 상세가 생길 때 |

> `CLAUDE.md` 와 요약 3종(SPEC/STRUCTURE/ARCHITECTURE) 사이에 충돌이 보이면 — **코드가 최신 진실**. 실제 코드(최신 Kotlin OOP·헥사고날)를 확인해 요약을 맞춘다.

---

## 3. 에이전트 게이트 로스터

작업 성격별로 통과해야 하는 게이트. 상세는 [`AGENTS.md §4`](AGENTS.md).

| 영역 | 게이트 (Claude Code) | 종류 |
|---|---|---|
| 헥사고날 경계 | `lemuel-hexagonal-enforcer` | agent (사후 검증) |
| 신학 정통성 | `lemuel-theology-reviewer` | agent (사후) |
| 신학 톤 사전적용 | `lemuel-xr-theology-tone` | skill (작성 시) |
| 정신건강 안전 | `lemuel-mental-health-safety` | agent (사후) |
| 정신건강 안전 사전적용 | `lemuel-xr-mental-health-safety` | skill (작성 시) |
| Flyway 마이그레이션 | `lemuel-xr-flyway-migration` | skill (작성 시) |
| 시퀀스 다이어그램 | `lemuel-xr-mermaid-sequence` | skill (작성 시) |
| 시나리오 설계 | `lemuel-scenario-designer` | agent |
| 클러스터 운영 | `lemuel-cluster-toolbox` | agent (배포/진단) |

**패턴**: *작성 시점* 스킬이 규칙을 미리 적용 → *사후* 에이전트가 게시 전 검수. 특히 사용자 노출 콘텐츠는 **신학 + 임상 2-축 approve** 없이는 PUBLISHED 불가.

> 이 게이트들은 현재 Claude Code 글로벌 설정(`~/.claude/`)에 정의돼 있고 리포에 커밋돼 있지 않다. 다른 기여자/도구는 [`AGENTS.md §4`](AGENTS.md)·[`docs/governance/CLINICAL-REVIEW.md`](docs/governance/CLINICAL-REVIEW.md) 의 *요구사항* 을 수동/자체 도구로 충족하면 된다.

---

## 4. 문서 갱신 워크플로

새 작업을 끝낼 때 스스로 점검:

1. **코드/스키마 바꿨나?** → `STRUCTURE.md`·`ARCHITECTURE.md`·`DB-SCHEMA.md` 영향 확인.
2. **기능 추가/변경했나?** → `SPEC.md` + `docs/FUNCTIONAL-SPEC.md` 갱신, 새 endpoint 면 `docs/SEQUENCE-DIAGRAMS.md`.
3. **미션·톤·안전 정책 건드렸나?** → `CLAUDE.md` 정본 갱신 (드물게).
4. **작업 규약 바꿨나?** → `AGENTS.md`.
5. **사용자 노출 콘텐츠?** → 신학·임상 게이트 통과 확인.
6. 링크 깨짐/SSOT 중복 없는지 확인.

---

## 5. 유지보수 원칙

- **정본은 하나, 나머지는 링크** — 같은 표를 여러 문서에 복붙하지 않는다.
- **코드가 최신 진실** — 문서-코드 충돌 시 코드 확인 후 문서를 맞춘다 (최신 Kotlin OOP·헥사고날 기준).
- **한국어 · markdown 일원화**.
- **작은 PR** — 문서도 코드와 같은 브랜치→머지 흐름 (`docs/<topic>`).
- **안전 불변식은 논의 대상 아님** — 4-layer·금지 어휘·1393 이관은 리팩터/정리 명목으로도 제거 금지.

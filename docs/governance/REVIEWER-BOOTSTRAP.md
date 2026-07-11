# Reviewer Bootstrap — 초기 자문가 등록 가이드

> **위치**: [`docs/governance/CLINICAL-REVIEW.md`](CLINICAL-REVIEW.md) 와 짝.
> **이슈**: [#4](https://github.com/MyoungSoo7/lemuel-xr/issues/4) 산출물 — *"초기 임상자문 1~2명 영입 + 본인·godjinho 신학 자문 등록"*
> **DB**: V20260521040956 (`reviewer_profiles` + `clinical_reviews`) + V20260521135130 (`theology_reviews.reviewer_profile_id`)
> **상태**: 운영 시작 시점 / 매번 신규 자문가 영입 시점에 *수동 수행*.

---

## 0. 왜 SQL 마이그레이션이 *아니라* 가이드인가

`reviewer_profiles` 시드를 Flyway 마이그레이션에 hardcode 하지 *않는* 이유:

1. **PII 가 포함됨** — 자문가 이름·소속·credential 은 *비공개 가능성* 있는 정보. git history 에 영구 박히면 회수 불가.
2. **시점 의존성** — 자문가 영입 시점 / 비활성 시점이 *런타임 결정*. 마이그레이션은 *스키마 변경* 만 담는 게 깨끗.
3. **`user_id` 결정 어려움** — `users.id UUID` 는 *해당 사용자의 OAuth 첫 로그인 시* 생성. 마이그레이션 시점엔 알 수 없음.
4. **삭제 가능성 (GDPR)** — 자문가가 데이터 삭제 요청 시 *마이그레이션 row* 를 어떻게 처리할지 모호 (Flyway baseline 시 부활 위험).

→ **수동 SQL 또는 Spring Boot CommandLineRunner** 로 idempotent 시드.

---

## 1. 전제 — 자문가가 먼저 *사용자 가입* 했는지 확인

`reviewer_profiles.user_id` 는 `users.id` FK (`ON DELETE CASCADE`). 자문가는 *먼저* lemuel-xr 에 사용자로 가입해야 한다.

| 자문가 유형 | 가입 경로 | `users.user_type` |
|---|---|---|
| 본인 (@MyoungSoo7) | OAuth (GitHub 또는 Google) | `oauth_google` |
| godjinho (@godjinho) | OAuth (GitHub 또는 Google) | `oauth_google` |
| 영입 임상자문 (정신과 의사 등) | OAuth (이메일 기반) | `oauth_google` |
| 영입 신학자문 (목사 등) | OAuth (이메일 기반) | `oauth_google` |

게스트 모드 사용자 (`user_type='guest'`) 는 자문가가 될 수 없다 — `external_id` 가 없어서 신원 확인 불가.

가입 후 `users.id` 를 얻은 다음 시드 진행.

---

## 2. 수동 SQL 시드 — psql

### 2.1 신학 자문 — 본인 + godjinho

```sql
-- Step 1. 자문가의 user_id 확인 (OAuth external_id 기준)
SELECT id, external_id, user_type, created_at
FROM users
WHERE external_id IN (
    'oauth-google|myoungsoo7@example.com',     -- 본인 (실제 sub claim 대치)
    'oauth-google|godjinho@example.com'        -- godjinho
);
-- → 두 UUID 기록

-- Step 2. reviewer_profiles 시드 (idempotent — UNIQUE (user_id, role) 보장)
INSERT INTO reviewer_profiles (
    user_id, role, credential, organization, bio, can_veto, review_scopes
)
VALUES
    (
        '<MyoungSoo7 user_id UUID>',
        'theology',
        '운영 책임자 / 신학 자문 코디네이터',
        'MyoungSoo7 / lemuel ecosystem',
        '운영 책임자. 모든 신학 검토의 1차 게이트.',
        FALSE,                                            -- veto 권한 없음 (운영자가 veto 도 가지면 권력 집중)
        '["theme_1","theme_2","theme_3","theme_4","theme_5","theme_6","theme_7","theme_8","theme_9","theme_10"]'::jsonb
    ),
    (
        '<godjinho user_id UUID>',
        'theology',
        '신학 협업자',
        '개인',
        'Theme 11 (예수 서사) 1차 담당. Issue #3 의 MVP-JESUS 설계 책임.',
        FALSE,
        '["theme_11"]'::jsonb
    )
ON CONFLICT (user_id, role) DO UPDATE
SET credential   = EXCLUDED.credential,
    organization = EXCLUDED.organization,
    bio          = EXCLUDED.bio,
    review_scopes = EXCLUDED.review_scopes,
    updated_at    = NOW();
```

### 2.2 임상 자문 — 영입 후

영입 완료 후 동일 패턴:

```sql
INSERT INTO reviewer_profiles (
    user_id, role, credential, organization, bio, can_veto, review_scopes
)
VALUES (
    '<영입 임상자문 user_id UUID>',
    'clinical',
    '정신과 의사 (대한신경정신의학회 회원 #...)',  -- 실제 자격 — 자문가 동의 받은 범위
    '<소속 병원·기관>',                              -- 자문가 명시 동의 범위
    '<공개 bio — 자문가 작성>',
    TRUE,                                            -- 임상자문은 veto 권한 (F-7.5.4)
    '["theme_5","theme_11","trigger_high","f6_safety","llm_system_prompt"]'::jsonb
)
ON CONFLICT (user_id, role) DO UPDATE
SET credential   = EXCLUDED.credential,
    organization = EXCLUDED.organization,
    bio          = EXCLUDED.bio,
    review_scopes = EXCLUDED.review_scopes,
    can_veto     = EXCLUDED.can_veto,
    updated_at    = NOW();
```

⚠️ **PII 동의 범위** — `credential` / `organization` / `bio` 에 들어가는 정보는 *자문가가 명시 동의한 범위* 만. 자문가가 *익명 자문* 을 원하면 `credential='임상심리사 1급 (이름 비공개)'`, `organization=NULL`, `bio=''` 으로.

---

## 3. 비활성화 — 자문가 사임 시

자문가 사임은 *물리 삭제 안 함* (audit 로그 보존):

```sql
UPDATE reviewer_profiles
SET is_active      = FALSE,
    deactivated_at = NOW()
WHERE user_id = '<해당 user_id>' AND role = '<theology|clinical>';
```

기존 검토 결과 (`theology_reviews` / `clinical_reviews`) 는 그대로 보존. `reviewer_profile_id` FK 는 `ON DELETE SET NULL` 이므로 *프로필을 진짜 삭제* 해도 review row 는 살아있음 (단 *프로필 매칭만 끊김*).

---

## 4. 자문가 데이터 삭제 요청 (GDPR / 개인정보보호법)

자문가가 *본인 데이터 완전 삭제* 요청 시:

1. `reviewer_profiles` 의 `bio` / `credential` / `organization` 컬럼 NULL 또는 *익명화 문구* 로 치환
2. `is_active=FALSE`, `deactivated_at=NOW()`
3. 30일 grace 후 *물리 삭제* (옵션 — 사용자 데이터 삭제 잡 (F-8.2) 와 동일 배치)
4. `theology_reviews` / `clinical_reviews` 의 `reviewer_id` 는 `ON DELETE SET NULL` 작동 — review 내용 자체는 보존 (audit 목적)

```sql
-- 익명화 (즉시)
UPDATE reviewer_profiles
SET credential   = '익명 처리됨 (사용자 요청)',
    organization = NULL,
    bio          = NULL,
    is_active    = FALSE,
    deactivated_at = NOW()
WHERE user_id = '<해당 user_id>';

-- 30일 후 (배치 잡)
DELETE FROM reviewer_profiles
WHERE deactivated_at < NOW() - INTERVAL '30 days'
  AND credential = '익명 처리됨 (사용자 요청)';
```

---

## 5. Spring Boot CommandLineRunner — 자동화 옵션 (구현됨 — *추천*)

수동 SQL 의 대안. *구현 완료* — `theology.application.ReviewerBootstrap` (`@PostConstruct` + `@Transactional`). 환경변수로 자문가 목록 받아서 startup 시 idempotent 시드.

### 5.1 구현 위치

- 코드: [`backend/src/main/java/github/lms/lemuel/xr/theology/application/ReviewerBootstrap.java`](../../backend/src/main/java/github/lms/lemuel/xr/theology/application/ReviewerBootstrap.java)
- 설정 예시: [`backend/src/main/resources/application-bootstrap.yml.example`](../../backend/src/main/resources/application-bootstrap.yml.example)

### 5.2 활성화

`SPRING_PROFILES_ACTIVE` 에 `bootstrap` 추가 + `application-bootstrap.yml` 마운트 (K8s Secret 또는 외부 파일):

```bash
# 로컬
SPRING_PROFILES_ACTIVE=docker,bootstrap \
SPRING_CONFIG_ADDITIONAL_LOCATION=file:/etc/lemuel-xr/ \
  ./gradlew bootRun

# K8s
# Deployment env:
#   - name: SPRING_PROFILES_ACTIVE
#     value: "k8s,bootstrap"
# Volume mount:
#   - secretName: lemuel-xr-reviewers
#     mountPath: /etc/lemuel-xr/application-bootstrap.yml
```

### 5.3 application-bootstrap.yml 구조

```yaml
lemuel:
  bootstrap:
    reviewers:
      enabled: true                                # toggle — false 면 ReviewerBootstrap bean 미생성
      reviewers:
        - external-id: "oauth-google|myoungsoo7@..."   # users.external_id (OAuth 가입 후 결정)
          role: theology                               # theology | clinical | ethics | editorial
          credential: "운영 책임자 / 신학 자문 코디네이터"
          organization: "MyoungSoo7 / lemuel ecosystem"
          bio: "운영 책임자."
          can-veto: false                              # 임상 default TRUE, 신학 default FALSE
          review-scopes: [theme_1, theme_2, ..., theme_10]

        - external-id: "oauth-google|godjinho@..."
          role: theology
          credential: "신학 협업자"
          bio: "Theme 11 (예수 서사) 담당."
          can-veto: false
          review-scopes: [theme_11]

        # 임상 자문은 영입 후 entry 추가
        - external-id: "oauth-google|clinician-a@..."
          role: clinical
          credential: "정신과 의사 (대한신경정신의학회 회원 #...)"
          can-veto: true
          review-scopes: [theme_5, theme_11, trigger_high, f6_safety, llm_system_prompt]
```

### 5.4 동작 보장

| 동작 | 보장 |
|---|---|
| **Idempotent** | `ON CONFLICT (user_id, role) DO UPDATE` — 재배포 안전 |
| **`external_id` → `user_id` 매칭** | 가입 안 한 자문가는 skip + WARN 로그 |
| **재활성화** | 비활성된 자문가를 yml 에 다시 넣으면 `is_active=TRUE`, `deactivated_at=NULL` (의도적) |
| **JSON scopes** | List&lt;String&gt; → JSON array 직렬화 (ObjectMapper 의존 X — 단순) |
| **트랜잭션** | 전체 시드가 단일 `@Transactional` — 부분 성공 없음 |

### 5.5 disable / 시드 안 함

`lemuel.bootstrap.reviewers.enabled=false` (또는 미설정) — `@ConditionalOnProperty` 로 bean 자체 미생성. 로컬 개발 / 테스트 환경 기본값.

### 5.6 PII 보호

- `application-bootstrap.yml` 은 **git 에 체크인되지 않음** (`.gitignore` 또는 K8s Secret)
- `application-bootstrap.yml.example` 은 *템플릿* 으로 git 에 있음 — 실제 값 placeholder
- yml 의 `credential` / `organization` / `bio` 는 자문가 *명시 동의 범위* 만

---

## 6. 검증 — 시드 후 확인

```sql
-- 활성 자문가 목록
SELECT rp.role, u.external_id, rp.credential, rp.can_veto, rp.review_scopes
FROM reviewer_profiles rp
JOIN users u ON u.id = rp.user_id
WHERE rp.is_active = TRUE
ORDER BY rp.role, rp.activated_at;

-- 각 role 별 자문가 수
SELECT role, COUNT(*) AS active_count
FROM reviewer_profiles
WHERE is_active = TRUE
GROUP BY role;
-- 목표: theology >= 2 (본인 + godjinho), clinical >= 1 (영입 후), ethics 0 (선택)

-- review_scopes 별 cover 확인
SELECT
    s.scope,
    COUNT(*) FILTER (WHERE rp.role = 'theology') AS theology_count,
    COUNT(*) FILTER (WHERE rp.role = 'clinical') AS clinical_count
FROM reviewer_profiles rp,
     LATERAL jsonb_array_elements_text(rp.review_scopes) AS s(scope)
WHERE rp.is_active = TRUE
GROUP BY s.scope
ORDER BY s.scope;
```

---

## 7. SLA 모니터링 (F-7.5.9)

자문가별 검토 SLA 준수 여부:

```sql
SELECT
    rp.role,
    u.external_id,
    COUNT(*) FILTER (WHERE tr.reviewed_at - cv.created_at > INTERVAL '5 days') AS over_5d,
    AVG(EXTRACT(EPOCH FROM (tr.reviewed_at - cv.created_at)) / 3600) AS avg_hours
FROM reviewer_profiles rp
JOIN users u ON u.id = rp.user_id
LEFT JOIN theology_reviews tr ON tr.reviewer_profile_id = rp.id
LEFT JOIN content_versions cv ON cv.id = tr.content_version_id
WHERE rp.is_active = TRUE AND rp.role = 'theology'
GROUP BY rp.role, u.external_id;
```

(clinical 도 동일 패턴 — `clinical_reviews` 로 join 대치)

---

## 8. 체크리스트 (영입 시)

신규 자문가 영입 시 한 줄씩:

- [ ] 자문가 OAuth 로그인 → `users.id` 확보
- [ ] 자격 / 소속 / bio 의 *공개 범위 동의* 받음 (서면 또는 이슈 코멘트)
- [ ] `review_scopes` 합의 (어떤 Theme / trigger_warning level 까지 본인이 검토할지)
- [ ] `can_veto` 합의 — 임상 자문은 default TRUE, 신학 자문은 default FALSE
- [ ] §2 의 INSERT SQL 실행 (또는 §5 의 CommandLineRunner 활용)
- [ ] §6 의 검증 query 로 시드 확인
- [ ] 자문가에게 *검토 큐 URL* + Admin Telegram 봇 알림 채널 안내
- [ ] CLINICAL-REVIEW.md §3 워크플로 + §4 권한 1회 cross-training

---

> *본 가이드는 PII 안전 운영의 핵심 — Flyway 마이그레이션에 자문가 정보 hardcode 금지. 반드시 본 가이드 또는 §5 의 CommandLineRunner 경유.*

# Volume 8 — 대기열 시스템 과제 계획

> Redis Sorted Set 기반 대기열로 처리량을 제어하고, 입장 토큰과 실시간 순번 조회를 통해 주문 API 앞단을 보호한다.
> 설계 원리 및 학습 자료: `waiting-queue-systems.md`

---

## 체크리스트

### Step 1 — 대기열

- [x] Redis Sorted Set 기반 대기열 진입 API (`POST /api/v1/queue/enter`)
- [x] 순번 조회 API (`GET /api/v1/queue/position`)
- [x] userId 기반 중복 진입 방지 (ZADD NX)
- [x] 전체 대기 인원 조회

### Step 2 — 입장 토큰 & 스케줄러

- [x] 스케줄러가 주기적으로 대기열에서 N명을 꺼내 입장 토큰 발급
- [x] 토큰 TTL 설정 (5분)
- [x] 주문 API 진입 시 토큰 검증 (`QueueTokenInterceptor`)
- [x] 주문 완료 후 토큰 삭제
- [x] 처리량 기준으로 스케줄러 배치 크기 산정 근거 문서화

### Step 3 — 실시간 순번 조회

- [x] 예상 대기 시간 계산 로직 구현
- [x] Polling 기반 순번 + 예상 대기 시간 응답
- [x] 토큰 발급 시 순번 조회 응답에 토큰 포함

### 검증

- [x] 동시 진입 테스트 — 대기열 순서 정확히 보장되는지 확인
- [x] 토큰 만료 테스트 — TTL 초과 시 토큰 무효화 확인
- [x] 처리량 초과 테스트 — 배치 크기 이상 요청 시 시스템 안정성 확인

---

## 아키텍처 결정

### Redis 자료구조

| 용도 | Key | 자료구조 | Value | 비고 |
|---|---|---|---|---|
| 대기열 | `queue:waiting` | Sorted Set | userId | score = 진입 시각(ms) |
| 입장 토큰 | `queue:token:{userId}` | String | UUID (토큰값) | TTL 5분 |

- **ZADD NX**: 같은 userId가 이미 있으면 score 갱신 없이 무시 → 중복 진입 방지 원자적 처리
- **ZRANK**: 0-based rank → +1이 현재 순번
- **ZCARD**: 전체 대기 인원

### 계층 배치

```
commerce-api
├── domain/queue/
│   ├── WaitingQueueService.java          ← 대기열 비즈니스 로직 (진입, 순번 조회, 배치 추출)
│   └── WaitingQueueRepository.java       ← Redis 접근 인터페이스
├── infrastructure/queue/
│   └── WaitingQueueRepositoryImpl.java   ← Redis Sorted Set 구현체
├── application/queue/
│   ├── QueueFacade.java                  ← 진입/조회 유스케이스 조합
│   └── QueueInfo.java                    ← Controller 반환 객체
├── interfaces/api/queue/
│   ├── QueueV1Controller.java
│   ├── QueueV1ApiSpec.java
│   └── QueueV1Dto.java
└── interfaces/api/common/interceptor/
    └── QueueTokenInterceptor.java        ← 주문 API 앞단 토큰 검증
```

### API 목록

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/queue/enter` | X-Loopers-LoginId/Pw | 대기열 진입 (중복 시 기존 순번 반환) |
| `GET` | `/api/v1/queue/position` | X-Loopers-LoginId/Pw | 순번 + 예상 대기 시간 + 토큰(발급된 경우) |

### 주문 API 흐름 변경

```
기존: AuthInterceptor → OrderV1Controller.create()
변경: AuthInterceptor → QueueTokenInterceptor → OrderV1Controller.create()
```

- `QueueTokenInterceptor`: 헤더 `X-Queue-Token` 검증
  - 없거나 만료 시 → `403 FORBIDDEN` (`QUEUE_TOKEN_REQUIRED`)
  - 유효 시 → 통과, token 속성 request에 저장
- `OrderFacade.create()` 완료 후 → `WaitingQueueService.removeToken(userId)` 호출

### 스케줄러 배치 크기 산정

```
[기준값]
- DB 커넥션 풀: 40개 (jpa.yml maximum-pool-size 설정값)
  → HikariCP 권장 (코어 수 × 2) + 1 기준보다 넉넉하게 설정
  → 실무에서 단일 앱 서버 기준 충분히 수용 가능한 수치
  → MySQL max_connections 기본값 151 대비 여유 있음
- DB 쿼리 처리 시간: ~100ms (재고 확인/주문 저장/결제 저장 INSERT 3건)
  → PG 요청 대기 시간은 DB 커넥션 비점유 구간이므로 제외

[DB TPS 계산]
- DB TPS = 40 / 0.1 = 400 TPS
- 주문 API Rate Limit = 400 TPS (DB 보호 최종 안전장치)

[동시 입장자 산정]
- 피크 가정: 1분 안에 전체 입장자의 50%가 주문 시도
- 동시 입장자 N × 50% / 60초 ≤ 400 TPS
- N ≤ 400 × 60 / 0.5 = 48,000명

[토큰 TTL]
- 고객 입력 시간(배송지/요청사항/카드정보): 약 4분
- 시스템 처리 최악(PG 재시도 3회 + 백오프): ~11초
- TTL = 300초(5분) — 입력 여유 포함

[채택]
- 초당 발급 수: 160명 (48,000 / 300)
- 스케줄러 주기: 1초 / 배치 크기: 160명
- 예상 대기 시간: 순번 / 80 (최대 대기 시간 기준)
- Rate Limit 초과(429) 시: 토큰 유지, TTL 내 재시도 가능
```

### ErrorType 추가 예정

| 코드 | HTTP | 설명 |
|---|---|---|
| `QUEUE_TOKEN_REQUIRED` | 403 | 토큰 없이 주문 API 진입 시 |
| `QUEUE_TOKEN_EXPIRED` | 403 | 토큰 만료 시 |

---

## 진행 기록

| 항목 | 상태 | 비고 |
|---|---|---|
| | | |

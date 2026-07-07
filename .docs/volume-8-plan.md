# Volume 8 — 대기열 시스템 과제 계획

> Redis Sorted Set 기반 대기열로 처리량을 제어하고, 입장 토큰과 실시간 순번 조회를 통해 주문 API 앞단을 보호한다.
> 설계 원리 및 학습 자료: `waiting-queue-systems.md`

---

## 체크리스트

### Step 1 — 대기열

- [ ] Redis Sorted Set 기반 대기열 진입 API (`POST /api/v1/queue/enter`)
- [ ] 순번 조회 API (`GET /api/v1/queue/position`)
- [ ] userId 기반 중복 진입 방지 (ZADD NX)
- [ ] 전체 대기 인원 조회

### Step 2 — 입장 토큰 & 스케줄러

- [ ] 스케줄러가 주기적으로 대기열에서 N명을 꺼내 입장 토큰 발급
- [ ] 토큰 TTL 설정 (5분)
- [ ] 주문 API 진입 시 토큰 검증 (`QueueTokenInterceptor`)
- [ ] 주문 완료 후 토큰 삭제
- [ ] 처리량 기준으로 스케줄러 배치 크기 산정 근거 문서화

### Step 3 — 실시간 순번 조회

- [ ] 예상 대기 시간 계산 로직 구현
- [ ] Polling 기반 순번 + 예상 대기 시간 응답
- [ ] 토큰 발급 시 순번 조회 응답에 토큰 포함

### 검증

- [ ] 동시 진입 테스트 — 대기열 순서 정확히 보장되는지 확인
- [ ] 토큰 만료 테스트 — TTL 초과 시 토큰 무효화 확인
- [ ] 처리량 초과 테스트 — 배치 크기 이상 요청 시 시스템 안정성 확인

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
- DB 커넥션 풀: HikariCP 기본 10개
- 주문 평균 처리 시간: ~500ms (쿼리 3~4회, PG 연동 포함 추정)

[계산]
- 동시 처리 가능 TPS = 10 / 0.5 = 20 TPS
- 스케줄러 주기 1초 → 배치 크기 20
- 스케줄러 주기 5초 → 배치 크기 100

[채택]
- 주기: 1초 (fixedDelay = 1000ms)
- 배치 크기: 20명
- 근거: 대기열 체감 지연 최소화, 처리량과 균형
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

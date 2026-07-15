# 대기열 시스템 — 설계 원리 및 학습 자료

> 블랙 프라이데이 트래픽 폭증 대응. Redis Sorted Set 기반 대기열로 Back-pressure를 구현한다.

---

## 왜 대기열인가

```
[10,000명 동시 접속]
     └── POST /orders
           ├── 재고 확인 & 차감
           ├── 결제 처리
           └── 주문 저장
           → DB 커넥션 풀 고갈
           → 응답 지연 → 타임아웃
           → 전체 시스템 장애
```

**스케일업/아웃으로 해결 안 되는 이유**
- DB, PG는 스케일이 제한적
- 피크가 극단적으로 짧고 높으면(행사 시작 직후 10초) 오토스케일링이 반응 전에 터짐

**핵심: Back-pressure**
하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 상류(유저 요청)를 흘려보내는 것.

---

## Rate Limiting vs Queuing

| 구분 | Rate Limiting | Queuing (대기열) |
|---|---|---|
| 초과 요청 처리 | 거부 (429) | 보관 (대기열에 적재) |
| 유저 경험 | "나중에 다시 시도" | "잠시만 기다려주세요 (512번째)" |
| 유저 반응 | 새로고침 → 재시도 폭풍 | 기다림 → 순서대로 처리 |
| 적합한 상황 | API 보호, 봇 차단 | 행사 트래픽, 유저가 기다릴 의사 있는 경우 |

> Rate Limiting과 Queuing은 양자택일이 아니다. 대기열 자체에 최대 인원 제한을 두거나, 봇을 Rate Limiting으로 먼저 거른 뒤 정상 유저만 대기열에 진입시킬 수 있다.

---

## Kafka 버퍼링과의 차이 (R7 쿠폰 vs R8 주문)

| 구분 | Kafka 버퍼링 (R7 쿠폰) | 대기열 시스템 (R8 주문) |
|---|---|---|
| 유저 경험 | 요청 후 나중에 결과 확인 (fire & forget) | 화면에서 순번 보며 대기 |
| 결과 전달 | 비동기 (polling으로 결과 조회) | 입장 토큰 발급 → 즉시 주문 가능 |
| 제어 대상 | 처리 순서 | 처리 속도 (throughput) |
| 핵심 관심사 | 메시지 유실 방지, 멱등 처리 | 공정한 순서, 실시간 피드백, 토큰 만료 |

---

## Redis 기반 대기열 구현

### 핵심 자료구조: Sorted Set

```
ZADD  waiting-queue  {timestamp}  {userId}    // 대기열 진입
ZRANK waiting-queue  {userId}                  // 내 순번 조회 (0-based)
ZCARD waiting-queue                            // 전체 대기 인원
ZPOPMIN waiting-queue {N}                      // 앞에서 N명 꺼내기 (스케줄러)
```

- score = 진입 시각(timestamp) → 먼저 들어온 사람이 앞 순번
- member = userId → Set 특성상 중복 진입 자동 방지

### 입장 토큰

```
SET   entry-token:{userId}  {token}  EX 300    // 5분 TTL 토큰 발급
GET   entry-token:{userId}                      // 토큰 검증
DEL   entry-token:{userId}                      // 사용 완료 후 삭제
```

---

## 실시간 피드백 방식 비교

### Polling (클라이언트가 주기적으로 질의)

```
[클라이언트]
   └── setInterval(2000)
         → GET /queue/position
         ← { "position": 128, "estimatedWaitSeconds": 45 }
         ...
         ← { "position": 0, "token": "abc-123-def" }  // 내 차례!
         → POST /orders (with token)
```

- 장점: 구현 단순, 인프라 변경 없음
- 단점: 대기 인원 많으면 Polling 자체가 서버 부하, 최대 1주기 지연

### SSE (Server-Sent Events, 서버가 Push)

```
[클라이언트] → GET /queue/stream (연결 유지)
          ← event: position
          ← data: { "position": 128, "estimatedWaitSeconds": 45 }
          ...
          ← event: enter
          ← data: { "token": "abc-123-def" }
```

- 장점: 변경 시점에만 전송, 불필요한 요청 없음
- 단점: 대기 인원 × 1 커넥션 유지 필요, 로드밸런서 설정 필요

> **권장**: Polling으로 시작 → Polling 부하 문제 발생 시 SSE 전환

### 예상 대기 시간 계산

```
예상 대기 시간(초) = 내 순번 / 초당 처리량

e.g. 순번 300, 초당 50명 처리 → 300 / 50 = 약 6초
```

추정값이므로 "약 N분"으로 표현 권장.

---

## Thundering Herd — 토큰 발급 직후의 함정

```
[스케줄러] → 1초마다 175명 토큰 발급
         → 175명 동시에 POST /orders
         → DB 커넥션 175개 동시 점유
         → 순간 부하 스파이크!
```

### 완화 전략

**발급 간격 분산**
```
AS-IS: 매 1초 → 175명 동시 발급
TO-BE: 매 100ms → ~18명씩 발급 → 부하가 10배 평탄화
```

**Jitter 부여**
토큰에 랜덤 딜레이(0~2초)를 포함해 유저마다 주문 API 진입 시점을 자연스럽게 분산.

**주문 API 자체 Rate Limit**
토큰이 있어도 초당 N건까지만 처리. 대기열이 뚫려도 하류 시스템을 보호하는 최종 안전장치.

> 대기열은 피크를 **평탄화(smoothing)** 하는 것이지, 부하를 없애는 것이 아니다.

---

## 대기열의 리스크

| 리스크 | 설명 | 대응 |
|---|---|---|
| 토큰 미사용 | 토큰 받고 주문 안 하면 자리 차지 | TTL 설정 + 만료된 수만큼 추가 발급 |
| 어뷰징 | 여러 디바이스로 중복 진입 시도 | userId member → Set 특성으로 자동 방지 |
| 스케줄러 장애 | 스케줄러 멈추면 대기열에서 아무도 못 빠짐 | 헬스체크, 미실행 기준 알림 |
| 과도한 Polling 부하 | 10,000명 × 2초 = 초당 5,000건 요청 | 순번 구간별 Polling 주기 동적 조절 |

### Polling 주기 동적 조절

```
순번 1~100:    1초마다 조회 (곧 입장)
순번 100~1000: 3초마다 조회
순번 1000+:    5초마다 조회
```

---

## Graceful Degradation (Redis 장애 시)

| 전략 | 설명 | 트레이드오프 |
|---|---|---|
| 전면 차단 | 대기열 진입 막고 "잠시 후 다시 시도" | 안전하지만 서비스 중단 |
| 대기열 우회 (bypass) | 주문 API 직접 접근 허용 | 서비스 유지, 과부하 위험 |
| Fallback 큐 | 로컬 메모리 큐/Kafka 임시 전환 | 순번 정확성 떨어지지만 서비스 유지 |

> 장애가 발생한 뒤에 판단하면 늦다. **사전에 전략을 정의해두는 것 자체가 핵심.**

---

## 운영 지표

| 지표 | 설명 | 이상 신호 |
|---|---|---|
| Queue Depth | 현재 대기 인원 (ZCARD) | 급격히 증가 → 유입 > 처리량 |
| Avg Wait Time | 진입 → 토큰 발급까지 평균 대기 시간 | 유저 체감 품질의 핵심 |
| P99 Wait Time | 상위 1% 유저의 대기 시간 | 평균 정상인데 P99 높으면 특정 시점 병목 |
| Token Conversion Rate | 토큰 발급 → 주문 완료 비율 | < 50% → TTL 짧거나 주문 UX 문제 |
| Token Expiry Rate | 토큰 만료(이탈) 비율 | > 30% → 유저가 대기 중 포기 |
| Scheduler Health | 스케줄러 마지막 실행 시각 | 1분 이상 미실행 → 대기열 전체 멈춤 |

---

## 처리량 설계 기준 (이 프로젝트 기준)

```
DB 커넥션 풀: 40개
주문 1건 DB 처리 시간: ~100ms (PG 대기 시간 제외, DB 커넥션 점유 구간만)
→ DB TPS = 40 / 0.1 = 400 TPS
→ 주문 API Rate Limit = 400 TPS (DB 보호 최종 안전장치)

동시 입장자 산정:
→ 피크 가정: 1분 안에 전체 입장자의 50%가 주문 시도
→ N × 50% / 60초 ≤ 400 TPS → N ≤ 48,000명

토큰 TTL = 300초 (고객 입력 4분 + 시스템 처리 최악 ~11초)
스케줄러: 1초마다 160명 토큰 발급 (48,000 / 300)
Thundering Herd 완화: 주문 API Rate Limit 400 TPS로 대체 (TTL이 길어 발급 간격 분산 효과 미미)
```

---

## 전체 흐름

```
[유저] → POST /queue/enter
      → Redis Sorted Set에 userId + timestamp 저장
      → 순번 응답 (e.g. 512번째)

[유저] → GET /queue/position (폴링)
      → 현재 순번 + 예상 대기 시간 응답

[스케줄러] → 1초마다 실행
         → ZPOPMIN으로 N명 꺼내기
         → 입장 토큰 발급 (Redis SET + TTL 5분)

[유저] → 순번 0 도달, 토큰 수신
      → POST /orders (Header: X-Queue-Token)
      → 토큰 검증 → 주문 처리
      → 토큰 삭제

[주문 이후] → ApplicationEvent → Kafka → streamer (R7 파이프라인 그대로)
```

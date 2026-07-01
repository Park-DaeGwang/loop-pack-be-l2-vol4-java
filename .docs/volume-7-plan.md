# Volume 7 — 이벤트 기반 아키텍처 과제 계획

> Spring ApplicationEvent로 경계를 나누고, Kafka로 이벤트 파이프라인을 구축한 뒤 선착순 쿠폰 발급에 적용한다.

---

## 체크리스트

### Step 1 — ApplicationEvent

- [x] 주문–결제 플로우에서 부가 로직을 이벤트 기반으로 분리한다.
- [x] 좋아요 처리와 집계를 이벤트 기반으로 분리한다. (집계 실패와 무관하게 좋아요는 성공)
- [x] 유저 행동(조회, 클릭, 좋아요, 주문 등)에 대한 서버 레벨 로깅을 이벤트로 처리한다. (클릭은 대응하는 API 자체가 없어 제외 — 조회/좋아요/주문만)
- [x] 동작의 주체를 적절하게 분리하고, 트랜잭션 간의 연관관계를 고민한다.

### Step 2 — Kafka Producer / Consumer

- [x] Step 1의 ApplicationEvent 중 시스템 간 전파가 필요한 이벤트를 Kafka로 발행한다. (`MetricsEventPublisher`가 직접 발행 — 아래 판단 참고)
- [x] `acks=all`, `idempotence=true` 설정
- [x] Transactional Outbox Pattern 구현 (인프라는 완성, **실사용은 Step3로 이동** — 아래 판단 참고)
- [x] PartitionKey 기반 이벤트 순서 보장 (key=productId/orderId로 파티션 고정)
- [ ] Consumer가 Metrics 집계 처리 (product_metrics upsert)
- [ ] `event_handled` 테이블을 통한 멱등 처리 구현
- [ ] manual Ack + `version`/`updated_at` 기준 최신 이벤트만 반영

### Step 3 — 선착순 쿠폰 발급

- [ ] 쿠폰 발급 요청 API → Kafka 발행 (비동기 처리) — **Outbox 패턴 여기서 실사용** (요청 유실 시 유저가 알 방법이 없는 진짜 중요 케이스)
- [ ] Consumer에서 선착순 수량 제한 + 중복 발급 방지 구현
- [ ] 발급 완료/실패 결과를 유저가 확인할 수 있는 구조 설계 (polling or callback)
- [ ] 동시성 테스트 — 수량 초과 발급이 발생하지 않는지 검증

### Nice-to-have (여유 있으면)

- [ ] Consumer Group 분리를 통한 관심사별 처리
- [ ] Consumer 배치 처리
- [ ] DLQ 구성

---

## 확정된 아키텍처 결정

- Kafka consumer 앱: `commerce-streamer` 재사용 (신규 앱 안 만듦)
- Outbox 테이블 위치: `commerce-api` 내부 `domain/outbox`
- Kafka 전파 대상: `ProductLikedEvent`/`UnlikedEvent` → `catalog-events`(key=productId), `PaymentConfirmedEvent` → `order-events`(key=orderId, "판매"는 결제확정 시점 기준)

## 미정 사항 (진행하면서 결정)

- 쿠폰 선착순 동시성 제어 방식: DB 조건부 UPDATE / Redis INCR / Kafka 파티션 단일화 중 미정

---

## 진행 순서 (가안)

1. **Step1 — ApplicationEvent 경계 분리**
   - 주문/결제 부가 로직(로깅, 알림) 이벤트 분리
   - 좋아요 → likeCount 집계 이벤트 분리 (`ProductLikeService` 동기 결합 제거)
   - 유저 행동 로깅 이벤트 리스너 추가
2. **Step2 — Kafka 파이프라인**
   - Outbox 테이블/발행기 구현
   - Producer 설정(acks=all, idempotence) 적용
   - `commerce-streamer`에 Consumer + `event_handled` + `product_metrics` 구현
3. **Step3 — 선착순 쿠폰 발급**
   - `CouponTemplateModel`에 수량 제한 필드 추가
   - 발급 요청 API → Kafka 발행 구조 전환
   - Consumer 동시성 제어 + 발급 결과 조회 API

---

## 진행 기록

| 항목 | 상태 | 비고 |
|---|---|---|
| 주문-결제 부가로직 분리 (알림톡) | ✅ | 결제확정(`PaymentSyncComponent.confirm`) 커밋 후 `PaymentConfirmedEvent` 발행 → `OrderNotificationEventListener`가 `@Async("notificationExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`로 mock 알림톡(`MockAlimtalkSender`) 발송. 전용 스레드풀(core 5/max 10/queue 100, CallerRunsPolicy) 구성. 재시도 콜백 중복발송 방지 위해 `wasPending` 가드 추가 |
| 좋아요-집계 이벤트 분리 | ✅ | `ProductLikedEvent`/`UnlikedEvent` 발행 → `ProductLikeCountEventListener`(`@Async("likeCountExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`) → `ProductLikeCountUpdater`(`@Transactional`, DB 갱신 전용 별도 빈)가 위임 처리. `LikeInfo`/`LikeV1Dto` 응답에서 `likeCount` 제거(eventual consistency라 응답 시점 정확성 보장 불가 — 프론트 낙관적 갱신 전제). 동시성 테스트에서 lost-update(9/10) 재현 → 원인 조사 끝에 DB/이벤트 레이어는 결백(순수 레이어 단독 테스트 12회 연속 통과, HTTP 상태코드 전부 200 확인) 확정, 실제 원인은 **캐시 무효화 레이스**(evict-then-stale-put — read-then-write 사이 경쟁자의 evict가 늦게 도착한 write를 못 막는 look-aside 캐싱 구조적 한계)로 특정. 좋아요는 저위험 데이터라 `PRODUCT_CACHE` TTL을 1시간→3초로 단축해 완화(근본 해결 아님, 감수). **교훈**: 고위험 데이터(재고/결제/쿠폰 수량)는 이 패턴 자체를 쓰면 안 되고 DB 조건부 UPDATE나 Redis atomic 연산처럼 카운트 자체가 authoritative해야 함 — Step3 쿠폰 발급 동시성 제어에 직접 적용할 원칙 |
| 유저 행동 로깅 이벤트 분리 | ✅ | `ProductViewedEvent`(신규, `ProductFacade.getActive()`에서 발행 — 비로그인 가능한 public API라 userId 없음), `OrderPlacedEvent`(신규, `OrderFacade.create()` 신규 생성 시에만 발행 — 멱등 재조회 경로는 재발행 안 함), 기존 `ProductLikedEvent`/`UnlikedEvent` 재사용. `UserActivityLoggingListener` 하나가 4종 이벤트 모두 구독해 `log.info` 한 줄씩 기록. `@Async` 안 씀 — 로컬 로그 한 줄이라 별도 스레드풀 부담 불필요, `@TransactionalEventListener(AFTER_COMMIT)`만으로 메인 로직과 분리 충분. 클릭은 대응하는 API/도메인 개념 자체가 없어 제외. 같은 이벤트에 Step2에서 Kafka 발행용 리스너가 추가로 붙을 예정(로깅 리스너는 그대로 유지, 구독자만 늘어나는 구조) |
| Outbox 인프라 구축 → Step3용으로 전환 | ✅ | 처음엔 `OutboxEventListener`로 `ProductLikedEvent`/`UnlikedEvent`/`PaymentConfirmedEvent`를 Outbox 테이블에 기록하도록 만들었으나, 재검토 결과 **오버엔지니어링으로 판단해 제거**. 근거: product_metrics는 표시용 집계일 뿐이고 원본 사실(좋아요/결제)은 이미 각자 테이블에 안전 커밋됨 — 유실돼도 "숫자 표시가 잠깐 부정확"한 수준. 반면 Outbox가 막아주는 "커밋했는데 발행 유실"은 **Step3 쿠폰 발급 요청**(발행 자체가 유저 요청의 유일한 기록, 유실 시 유저가 인지도 재시도도 못 함)에서 훨씬 절실함. `OutboxEventModel`/`Repository`/`Service`/`Publisher`(+테스트)는 범용 인프라라 그대로 유지 — Step3에서 재사용 예정 |
| 메트릭 이벤트 직접 발행으로 전환 | ✅ | `OutboxEventListener` 제거하고 `MetricsEventPublisher` 신설 — `ProductLikedEvent`/`UnlikedEvent`/`PaymentConfirmedEvent`를 Outbox 없이 곧바로 `kafkaTemplate.send()`. `@Async("kafkaEventExecutor")` 붙임 — 처음엔 "`kafkaTemplate.send()`는 원래 논블로킹이라 불필요"로 판단했으나, 브로커 지연/버퍼 포화 시 `send()`가 `max.block.ms`(기본 60s)까지 호출 스레드를 블로킹할 수 있어 메인 요청 스레드와 분리해야 한다는 점을 재확인하고 정정. `@Async` 적용 기준 정리: "이 부가로직이 내가 통제 못 하는 이유로 블로킹/실패할 수 있는 외부 I/O인가" — 외부 시스템 호출(알림톡)/DB 갱신(좋아요 카운트)/카프카 발행은 O, 로컬 로그 기록은 X |
| 이벤트 타입 구분(Kafka 헤더) + ProductViewedEvent 발행 추가 | ✅ | `catalog-events` 토픽 하나에 `ProductLikedEvent`/`UnlikedEvent`/`ViewedEvent` 3종이 같이 나가는데, 세 이벤트가 필드 구조가 같거나 겹쳐서 payload JSON만으로는 컨슈머가 타입을 구분 못 하는 문제 발견 (`ProducerRecord`로 바꿔 Kafka **헤더**에 `eventType`(클래스 simpleName)을 실어 해결 — 봉투(envelope)/토픽 분리안도 검토했으나 헤더가 payload 구조를 안 건드리는 쪽이라 채택). `ProductViewedEvent`가 애초에 Kafka로 발행 안 되고 있던 것도 이번에 같이 추가(조회수 집계에 필요) |
| Producer 설정 + 실제 발행 | ✅ | `commerce-api`가 `modules:kafka`에 의존 안 하고 있었음을 발견해 의존성 추가(main+testFixtures). `kafka.yml` producer에 `acks=all`, `enable.idempotence=true` 추가, `value-serializer`를 `JsonSerializer`→`StringSerializer`로 변경(Outbox payload가 이미 JSON 문자열이라 JsonSerializer 쓰면 이중 직렬화됨). `OutboxPublisher`(`@Scheduled(fixedDelay=1000)`)가 미발행 이벤트를 배치(100건)로 조회해 Kafka 전송 후 성공 시에만 `markPublished`. Kafka 전송(네트워크 I/O)은 트랜잭션 밖에서 수행, 성공 확인 후 별도 트랜잭션으로 상태 갱신. `modules/kafka`에 Testcontainers 설정(`KafkaTestContainersConfig`)이 아예 없었어서 Redis/JPA와 동일 패턴으로 신설 — **디버깅**: 생성자에서 `System.setProperty` 하는 방식이 빈 생성 순서 문제로 타이밍에 안 먹혀서, 로컬에 떠있던 진짜 docker-compose Kafka로 잘못 연결되는 문제 발생 → static 블록으로 이동해서 해결. **회귀 발견**: 좋아요-집계 비동기화 이후 전체 스위트를 안 돌려서 놓쳤던 `ProductServiceCacheIntegrationTest`(캐시 evict 동기 가정), `ProductV1ApiE2ETest`(좋아요 정렬 즉시 반영 가정) 2개 테스트 파일을 폴링 방식으로 수정 |

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
- [x] Consumer가 Metrics 집계 처리 (product_metrics upsert)
- [x] `event_handled` 테이블을 통한 멱등 처리 구현
- [x] manual Ack + `version`/`updated_at` 기준 최신 이벤트만 반영 (`lastEventAt` 필드로 staleness 체크)

### Step 3 — 선착순 쿠폰 발급

- [x] 쿠폰 발급 요청 API → Kafka 발행 (비동기 처리) — **Outbox 패턴 여기서 실사용** (요청 유실 시 유저가 알 방법이 없는 진짜 중요 케이스)
- [x] Consumer에서 선착순 수량 제한 + 중복 발급 방지 구현
- [x] 발급 완료/실패 결과를 유저가 확인할 수 있는 구조 설계 (polling)
- [x] 동시성 테스트 — 수량 초과 발급이 발생하지 않는지 검증

### Nice-to-have (여유 있으면)

- [x] Consumer Group 분리를 통한 관심사별 처리
- [x] Consumer 배치 처리 (`KafkaConfig.BATCH_LISTENER` — 3개 컨슈머 전부 배치+수동 ack로 이미 구현돼 있었음)
- [x] DLQ 구성 (재시도 후처리는 범위 밖 — 나중에 별도 결정)

---

## 확정된 아키텍처 결정

- Kafka consumer 앱: `commerce-streamer` 재사용 (신규 앱 안 만듦)
- Outbox 테이블 위치: `commerce-api` 내부 `domain/outbox`
- Kafka 전파 대상: `ProductLikedEvent`/`UnlikedEvent` → `catalog-events`(key=productId), `PaymentConfirmedEvent` → `order-events`(key=orderId, "판매"는 결제확정 시점 기준)
- 이벤트 타입 구분: Kafka 헤더의 `eventType`(클래스 simpleName)
- `event_handled` 멱등키: `topic+partition+offset` 조합 대신 **이벤트 자체에 `eventId`(UUID) 필드**를 갖게 함 — 나중에 추적/동일 이벤트 판단에 더 유리하다는 판단. 기존 발행부 호출부는 안 건드리게 보조 생성자로 자동 생성

## 미정 사항 (진행하면서 결정)

- ~~쿠폰 선착순 동시성 제어 방식~~ → **DB 조건부 UPDATE**로 확정 (좋아요 집계 캐시 레이스 교훈 적용, 고위험 카운트는 DB가 authoritative해야 함)

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
| event_handled 멱등키 — eventId 필드 추가 | ✅ | `topic+partition+offset` 조합안도 검토했으나(producer 무변경 장점) "동일 논리 이벤트 추적"엔 이벤트 자체가 정체성을 갖는 게 낫다고 판단해 각 이벤트 record에 `eventId(UUID)` 필드 추가. 기존 발행부(`ProductLikeService` 등)와 테스트 전부 안 건드리게 보조 생성자(`this(UUID.randomUUID(), ...)`)로 자동 생성 — 컴파일/테스트 전부 그대로 통과 확인 |
| Producer 설정 + 실제 발행 | ✅ | `commerce-api`가 `modules:kafka`에 의존 안 하고 있었음을 발견해 의존성 추가(main+testFixtures). `kafka.yml` producer에 `acks=all`, `enable.idempotence=true` 추가, `value-serializer`를 `JsonSerializer`→`StringSerializer`로 변경(Outbox payload가 이미 JSON 문자열이라 JsonSerializer 쓰면 이중 직렬화됨). `OutboxPublisher`(`@Scheduled(fixedDelay=1000)`)가 미발행 이벤트를 배치(100건)로 조회해 Kafka 전송 후 성공 시에만 `markPublished`. Kafka 전송(네트워크 I/O)은 트랜잭션 밖에서 수행, 성공 확인 후 별도 트랜잭션으로 상태 갱신. `modules/kafka`에 Testcontainers 설정(`KafkaTestContainersConfig`)이 아예 없었어서 Redis/JPA와 동일 패턴으로 신설 — **디버깅**: 생성자에서 `System.setProperty` 하는 방식이 빈 생성 순서 문제로 타이밍에 안 먹혀서, 로컬에 떠있던 진짜 docker-compose Kafka로 잘못 연결되는 문제 발생 → static 블록으로 이동해서 해결. **회귀 발견**: 좋아요-집계 비동기화 이후 전체 스위트를 안 돌려서 놓쳤던 `ProductServiceCacheIntegrationTest`(캐시 evict 동기 가정), `ProductV1ApiE2ETest`(좋아요 정렬 즉시 반영 가정) 2개 테스트 파일을 폴링 방식으로 수정 |
| PaymentConfirmedEvent에 주문 아이템 정보 추가 | ✅ | `product_metrics.salesCount`는 상품별 집계인데 `PaymentConfirmedEvent`엔 orderId만 있고 productId가 없어 컨슈머가 어느 상품 판매량을 올려야 할지 알 수 없는 설계 구멍 발견. `PaymentConfirmedEvent`에 `items(List<OrderItemSummary(productId, quantity)>)` 필드 추가, `PaymentSyncComponent.confirm()`에서 `order.getItems()`로 채워 발행하도록 수정 |
| Consumer 구현 (`commerce-streamer`) | ✅ | `product_metrics`(productId/likeCount/viewCount/salesCount/lastEventAt), `event_handled`(eventId PK, `BaseEntity` 미상속 — 자동생성 id 대신 eventId 자체가 자연키라 의도적 이탈)를 신설. `ProductMetricsService`를 `applyIfNotHandled`(이벤트 단위 멱등체크+커밋)와 `applyToProduct`(상품 단위 staleness체크+갱신, 자체 멱등체크 없음)로 분리 — 주문 하나가 상품 여러 개를 포함할 수 있어 멱등체크를 상품 단위로 하면 다상품 주문에서 첫 상품만 반영되고 나머지가 "이미 처리됨"으로 오판되어 스킵되는 버그가 생기기 때문. `CatalogEventsConsumer`/`OrderEventsConsumer`는 기존 `DemoKafkaConsumer` 패턴(batch listener + manual ack) 그대로, 헤더의 `eventType`으로 분기. DLQ는 nice-to-have라 스킵(사유: 저위험 데이터라 재시도/DLQ 운영비용 대비 실익 낮음). **디버깅**: 실제 Kafka로 붙는 통합테스트가 간헐 실패 — 원인은 `auto.offset.reset=latest`(기본값)가 테스트 프로듀서의 발행보다 컨슈머 구독 완료가 늦을 때 그 메시지를 건너뛰는 레이스. `test` 프로파일에서만 `earliest`로 override해서 해결(local/prod 영향 없음). `commerce-streamer`의 `spring.application.name`이 `commerce-api`로 복붙돼있던 오타도 같이 수정 |
| 선착순 쿠폰 — 수량 제한 필드 + 조건부 UPDATE | ✅ | `CouponTemplateModel`에 `totalQuantity`(무제한이면 null)/`issuedCount` 추가, `isLimited()`로 무제한/제한 구분. `CouponTemplateJpaRepository.tryIssue()`를 `@Modifying(flushAutomatically=true, clearAutomatically=true)` JPQL 조건부 UPDATE(`issuedCount<totalQuantity`일 때만 증가)로 구현 — 좋아요 집계에서 얻은 교훈(look-aside 캐시는 고위험 카운트에 부적합) 그대로 적용해 DB 원자적 UPDATE로 authoritative하게 처리. 기존 `issue()`(즉시발급)엔 `template.isLimited()`면 `CoreException(BAD_REQUEST)` 가드 추가 — 제한 쿠폰은 반드시 비동기 경로로만 발급되게 강제 |
| 선착순 쿠폰 — 비동기 발급 요청/컨슈머/조회 API | ✅ | `CouponIssueRequestModel`(PENDING/SUCCESS/FAILED, `succeed(userCouponId)`/`fail(reason)`)을 요청 시점에 즉시 생성해 `requestId` 반환, 실제 발급 처리는 `CouponIssueRequestedEvent`를 `CouponOutboxEventListener`(같은 트랜잭션의 평범한 `@EventListener` — 진짜 Outbox 사용처)가 `coupon-issue-requests` 토픽(key=templateId, 같은 쿠폰 요청은 항상 같은 파티션→순차 처리 보장)에 기록 → `OutboxPublisher`가 발행 → `CouponIssueConsumer`가 `CouponIssueProcessingService.process()`(eventId 멱등체크, 중복발급 사전체크, `tryIssue()`, 성공 시 `UserCouponModel` 발급) 호출. 유저는 `GET /issue-requests/{requestId}`를 폴링해 결과 확인. commerce-api 자체 `event_handled` 테이블 신설(streamer와 별개) |
| **버그**: `spring.config.import`에 `kafka.yml` 누락 | ✅ | `apps/commerce-api/application.yml`이 `modules:kafka` Gradle 의존성만 추가돼있고 `spring.config.import`엔 `kafka.yml`이 없었음 — 이 세션 내내 `kafka.yml`의 모든 설정(acks/idempotence/group-id 등)이 commerce-api엔 적용된 적이 없었다는 뜻. `CouponIssueConsumer`(commerce-api 최초의 `@KafkaListener`)를 붙이자마자 "No group.id found" 컨텍스트 기동 실패로 표면화. `import` 목록에 `kafka.yml` 추가해서 해결 |
| **버그**: `WebMvcConfig` 인터셉터 경로 누락 | ✅ | 신규 엔드포인트 `/api/v1/coupons/*/issue-requests`, `/api/v1/coupons/issue-requests/*`가 `authInterceptor.addPathPatterns(...)`에 빠져있어 동시성 테스트의 10개 동시 요청이 전부 "Missing request attribute 'authenticatedUser'"로 실패(`requestIdsByUser` 비어있음). 경로 패턴 추가해서 해결 |
| **버그**: `tryIssue()`의 `clearAutomatically`로 인한 상태 갱신 유실 | ✅ | 위 두 버그를 고친 뒤에도 동시성 테스트가 전부 PENDING에서 타임아웃. Hibernate SQL 로그 포렌식(grep)으로 `issued_count` UPDATE/`user_coupons` INSERT/`event_handled` INSERT는 5:5로 정확히 일어났는데 `coupon_issue_requests` UPDATE는 **단 한 번도** 안 나간 걸 확인. 원인: `tryIssue()` 호출 전에 조회해둔 `request` 엔티티가, `tryIssue()`의 `clearAutomatically=true`가 1차 캐시를 통째로 비우면서 detach됨 → 이후 `request.succeed()`/`.fail()`은 dirty checking 대상이 아니라 조용히 유실. `OrderFacade.create()`에 이미 있던 동일 패턴("1LC에서 detach된 order 재조회")을 그대로 적용 — `tryIssue()` 직후 `request`를 `couponIssueRequestService.get(requestId)`로 재조회하도록 수정. 동시성 테스트 3연속 통과로 확인(정확히 5 SUCCESS / 5 FAILED, failReason="매진되었습니다.") |
| 토픽 파티션 명시적 구성 | ✅ | 토픽을 명시적으로 만든 적이 없어서 브로커 auto-create(기본 파티션 1개)에 의존하고 있었음을 발견 — `concurrency=3` 설정이 파티션 1개에서는 사실상 무의미(1개만 배정되고 나머지 idle)했던 문제. `KafkaConfig`에 `NewTopic` 빈 3개(`catalog-events`/`order-events`/`coupon-issue-requests`, 각 파티션 3/복제 1) 추가 — Boot가 자동 구성하는 `KafkaAdmin`이 기동 시 스캔해서 생성. 쿠폰 토픽도 파티션 1개로 가지 않기로 결정 — "순서 보장"은 파티션 개수가 아니라 key(templateId) 기반 해시 라우팅으로 성립하는 성질이라, 같은 템플릿끼리는 파티션 3개여도 항상 같은 파티션에 모이고, 서로 다른 템플릿끼리는 병렬 처리가 가능해짐(1개로 묶으면 무관한 쿠폰끼리도 한 줄로 세워 병렬성만 손해). 겸사겸사 `kafka.yml`의 `auto.create.topics.enable: false`(브로커 전용 설정을 클라이언트 프로퍼티에 넣어서 원래도 무효였음)를 제거하고, `docker/infra-compose.yml`에 `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=false`를 추가해 브로커 레벨에서 실제로 auto-create를 막음(오타/누락 토픽이 조용히 잘못된 파티션 수로 만들어지는 사고 방지) |
| DLQ 구성 (재시도 + Dead Letter) | ✅ | 기존 3개 배치 컨슈머는 레코드별 try/catch로 실패를 삼키고 무조건 ack — 재시도도 DLQ도 없이 실패 메시지가 영구 유실되는 구조였음. `BatchListenerFailedException(cause, index)`를 던지도록 바꾸고, `KafkaConfig`에 `DefaultErrorHandler(DeadLetterPublishingRecoverer, ExponentialBackOffWithMaxRetries)`를 공통 에러 핸들러로 등록 — 실패 레코드는 그 offset부터 seek-back되어 재시도(500ms→1000ms→2000ms, 총 3회) 후 소진되면 원본 레코드가 DLQ 토픽으로 발행됨. `@RetryableTopic`(레코드 단위 리스너 전용, 재시도 전용 토픽 체인 방식)은 배치 리스너(`List<ConsumerRecord<>>>` 시그니처)와 애초에 호환 안 돼서 배제 — 배치는 `BatchListenerFailedException` 기반 blocking retry(해당 파티션만 일시 정지)가 표준 방식. DLQ 토픽은 `NewTopic` 빈으로 3개(`catalog-events-dlt`/`order-events-dlt`/`coupon-issue-requests-dlt`, 파티션 수는 원본과 동일하게 3 — `DeadLetterPublishingRecoverer`가 원본 파티션 번호를 그대로 써서 발행하기 때문) 추가. **디버깅**: 처음엔 DLQ 토픽명을 `.DLT`(대문자, 점)로 잘못 가정해서 만들었으나, `DeadLetterPublishingRecoverer`의 실제 기본 네이밍은 `-dlt`(소문자, 하이픈) — 클래스 바이트코드 상수 풀 확인으로 정정. 테스트에서 실패 재현용 메시지와 정상 케이스 메시지를 같은 토픽의 다른 파티션(0/1)에 명시적으로 고정해 서로 블로킹하지 않게 분리. **관찰**: 재시도 간격이 설정한 애플리케이션 backoff(500ms~2s)가 아니라 `fetch.min.bytes=1MB`+`fetch.max.wait.ms=5s`(처리량 최적화용 배치 설정)에 지배돼 실제로는 ~10초 간격으로 나타남 — 저트래픽/테스트 상황에서 재시도 지연이 커지는 트레이드오프이나, 현재 이 파이프라인에 하드 실시간 요구사항이 없어 수용 가능하다고 판단(쿠폰 상태도 이미 유저가 폴링 15초로 기다리는 구조). DLQ 적재 후 후처리(재처리/알림)는 이번 범위에서 제외 — 사용자가 별도로 결정하기로 함 |
| Consumer Group 관심사별 분리 | ✅ | `catalog-events`/`order-events`/`coupon-issue-requests` 컨슈머 3개가 `kafka.yml`의 `group-id: loopers-default-consumer` 하나를 공유하고 있었음 — 토픽이 달라 당장 기능적 버그는 아니었지만, 한 관심사의 리밸런싱이 다른 관심사까지 영향권에 두고 모니터링(랙 등)도 관심사별로 분리가 안 되는 문제. `@KafkaListener`의 `groupId` 속성으로 팩토리 기본값을 오버라이드 — `catalog-metrics-consumer`/`order-metrics-consumer`/`coupon-issue-consumer` 3개로 분리. `kafka.yml`의 `loopers-default-consumer`는 향후 groupId 미지정 리스너용 기본값으로 유지 |

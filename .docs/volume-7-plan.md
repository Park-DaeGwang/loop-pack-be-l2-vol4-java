# Volume 7 — 이벤트 기반 아키텍처 과제 계획

> Spring ApplicationEvent로 경계를 나누고, Kafka로 이벤트 파이프라인을 구축한 뒤 선착순 쿠폰 발급에 적용한다.

---

## 체크리스트

### Step 1 — ApplicationEvent

- [ ] 주문–결제 플로우에서 부가 로직을 이벤트 기반으로 분리한다.
- [ ] 좋아요 처리와 집계를 이벤트 기반으로 분리한다. (집계 실패와 무관하게 좋아요는 성공)
- [ ] 유저 행동(조회, 클릭, 좋아요, 주문 등)에 대한 서버 레벨 로깅을 이벤트로 처리한다.
- [ ] 동작의 주체를 적절하게 분리하고, 트랜잭션 간의 연관관계를 고민한다.

### Step 2 — Kafka Producer / Consumer

- [ ] Step 1의 ApplicationEvent 중 시스템 간 전파가 필요한 이벤트를 Kafka로 발행한다.
- [ ] `acks=all`, `idempotence=true` 설정
- [ ] Transactional Outbox Pattern 구현
- [ ] PartitionKey 기반 이벤트 순서 보장
- [ ] Consumer가 Metrics 집계 처리 (product_metrics upsert)
- [ ] `event_handled` 테이블을 통한 멱등 처리 구현
- [ ] manual Ack + `version`/`updated_at` 기준 최신 이벤트만 반영

### Step 3 — 선착순 쿠폰 발급

- [ ] 쿠폰 발급 요청 API → Kafka 발행 (비동기 처리)
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

## 미정 사항 (진행하면서 결정)

- Outbox 테이블 위치: `commerce-api` 내부 `domain/outbox` (가안, 미확정)
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

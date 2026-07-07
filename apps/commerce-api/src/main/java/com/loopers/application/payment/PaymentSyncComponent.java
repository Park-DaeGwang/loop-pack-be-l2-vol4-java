package com.loopers.application.payment;

import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional
public class PaymentSyncComponent {

    private final OrderService orderService;
    private final OrderStockService orderStockService;
    private final PaymentService paymentService;
    private final UserCouponService userCouponService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentInfo confirm(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        boolean wasPending = order.isPending(); // confirmOrder는 멱등(이미 CONFIRMED면 스킵) — 재시도 콜백에서 이벤트 중복 발행 방지용 체크
        orderStockService.confirmOrder(order, amount);
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.SUCCESS, amount)
        );
        if (wasPending) {
            List<PaymentConfirmedEvent.OrderItemSummary> items = order.getItems().stream()
                .map(item -> new PaymentConfirmedEvent.OrderItemSummary(item.getProductId(), item.getQuantity()))
                .toList();
            eventPublisher.publishEvent(new PaymentConfirmedEvent(orderId, order.getUserId(), amount, items));
        }
        return PaymentInfo.from(payment);
    }

    public PaymentInfo fail(UUID orderId, String pgTransactionId, Long amount) {
        OrderModel order = orderService.getForUpdate(orderId);
        boolean failed = orderStockService.failOrder(order);
        if (failed) {
            userCouponService.releaseByOrderId(orderId);
        }
        PaymentModel payment = paymentService.saveIfAbsent(
            orderId,
            new PaymentModel(orderId, pgTransactionId, PaymentStatus.FAILED, amount)
        );
        return PaymentInfo.from(payment);
    }
}

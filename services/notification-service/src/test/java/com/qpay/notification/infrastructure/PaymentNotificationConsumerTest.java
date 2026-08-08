package com.qpay.notification.infrastructure;

import com.qpay.notification.application.NotificationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PaymentNotificationConsumerTest {
    @Test
    void malformedEventNeverStartsDelivery() {
        NotificationService service = mock(NotificationService.class);
        var consumer = new PaymentNotificationConsumer(service);
        assertThatIllegalArgumentException().isThrownBy(() -> consumer.consume("{}"))
                .withMessageContaining("missing notification event field");
        verifyNoInteractions(service);
    }
}

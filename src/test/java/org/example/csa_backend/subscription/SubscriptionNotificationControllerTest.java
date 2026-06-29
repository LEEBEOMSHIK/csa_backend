package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionNotificationControllerTest {

    private final SubscriptionNotificationService notificationService =
            mock(SubscriptionNotificationService.class);
    private final GooglePubSubOidcVerifier verifier = mock(GooglePubSubOidcVerifier.class);

    @SuppressWarnings("unchecked")
    private SubscriptionNotificationController controller(GooglePubSubOidcVerifier present) {
        ObjectProvider<GooglePubSubOidcVerifier> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(present);
        return new SubscriptionNotificationController(notificationService, provider);
    }

    private GoogleNotificationRequest request() {
        return new GoogleNotificationRequest(new GooglePubSubMessage("data", "id", "time"), "sub");
    }

    @Test
    void failedOidcVerificationBlocksServiceCall() {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "bad token"))
                .when(verifier).verify(any());
        SubscriptionNotificationController controller = controller(verifier);

        assertThatThrownBy(() -> controller.googleNotification("Bearer bad", request()))
                .isInstanceOf(BusinessException.class);

        verify(verifier).verify("Bearer bad");
        verify(notificationService, never()).handleGoogleNotification(any());
    }

    @Test
    void successfulOidcVerificationDelegatesToService() {
        SubscriptionNotificationController controller = controller(verifier);
        GoogleNotificationRequest request = request();

        controller.googleNotification("Bearer good", request);

        verify(verifier).verify("Bearer good");
        verify(notificationService).handleGoogleNotification(request.message());
    }

    @Test
    void withoutVerifierBeanServiceStillHandlesNotification() {
        SubscriptionNotificationController controller = controller(null);
        GoogleNotificationRequest request = request();

        controller.googleNotification(null, request);

        verify(notificationService).handleGoogleNotification(request.message());
    }
}

package org.example.csa_backend.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SubscriptionSecurityPathTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SubscriptionNotificationService notificationService;

    @MockitoBean
    private SubscriptionService subscriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void appleNotificationPathIsPermittedWithoutJwt() throws Exception {
        mockMvc.perform(post("/subscriptions/notifications/apple")
                        .contentType("application/json")
                        .content("{\"signedPayload\":\"x\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void googleNotificationPathIsPermittedWithoutJwt() throws Exception {
        mockMvc.perform(post("/subscriptions/notifications/google")
                        .contentType("application/json")
                        .content("{\"message\":{\"data\":\"x\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void meEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/subscriptions/me"))
                .andExpect(status().is4xxClientError());
    }
}

package org.example.csa_backend.admin;

import org.example.csa_backend.common.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /admin/** 인가 경계 검증.
 * SecurityConfig의 exceptionHandling 설정에 따라
 * 미인증 요청은 401(UNAUTHORIZED), 인증되었으나 권한 없는 요청은 403(FORBIDDEN)을 반환해야 한다.
 */
@SpringBootTest
class AdminUserAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private AdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        when(adminUserService.getUsers(nullable(String.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRoleCanAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleIsRejectedFromAdminUsersWith403() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unauthenticatedIsRejectedFromAdminUsersWith401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}

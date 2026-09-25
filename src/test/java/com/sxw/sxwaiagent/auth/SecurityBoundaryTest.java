package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real JWT and authorization filters, not addFilters=false controller tests. */
@SpringJUnitWebConfig(SecurityBoundaryTest.Config.class)
class SecurityBoundaryTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @Autowired JwtTokenService jwt;
    @Autowired UserAccountRepository users;
    @Autowired TokenBlacklistService blacklist;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        reset(jwt, users, blacklist);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        for (String role : new String[]{"USER", "EVALUATOR", "ADMIN"}) {
            var account = mock(UserAccount.class);
            when(account.getId()).thenReturn(1L);
            when(account.getUsername()).thenReturn(role);
            when(account.getRole()).thenReturn(role);
            when(account.isEnabled()).thenReturn(true);
            when(jwt.validateToken(role)).thenReturn(true);
            when(jwt.resolveUsername(role)).thenReturn(role);
            when(users.findByUsername(role)).thenReturn(Optional.of(account));
        }
    }

    @Test void anonymousCannotAccessProtectedEndpoints() throws Exception {
        for (String path : new String[]{"/api/hermes/candidates", "/api/eval/runs", "/api/agent/traces/chat"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
        }
    }

    @Test void ordinaryUserCannotReviewHermesEvenWithForgedReviewer() throws Exception {
        mvc.perform(post("/api/hermes/candidates/c/approve").header("Authorization", "Bearer USER")
                .param("reviewedBy", "admin")).andExpect(status().isForbidden());
        mvc.perform(get("/api/hermes/candidates").header("Authorization", "Bearer USER"))
                .andExpect(status().isForbidden());
    }

    @Test void evaluatorCanRunEvaluationsButCannotReviewOrAdminister() throws Exception {
        mvc.perform(post("/api/eval/runs").header("Authorization", "Bearer EVALUATOR"))
                .andExpect(status().isOk());
        for (String path : new String[]{"/api/hermes/candidates", "/api/admin/audit", "/actuator/metrics"}) {
            mvc.perform(get(path).header("Authorization", "Bearer EVALUATOR")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/eval/runs").header("Authorization", "Bearer USER"))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanReviewAndOperateAndUserCanReachOwnedResourceHandlers() throws Exception {
        mvc.perform(post("/api/hermes/candidates/c/approve").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").header("Authorization", "Bearer ADMIN")).andExpect(status().isOk());
        mvc.perform(get("/api/agent/traces/chat").header("Authorization", "Bearer USER"))
                .andExpect(status().isOk()); // Actual resource ownership is tested by TraceOwnershipTest.
    }

    @Test void revokedOrDisabledAccountsCannotUseEvenAdminEndpoints() throws Exception {
        when(blacklist.isBlacklisted("ADMIN")).thenReturn(true);
        mvc.perform(get("/api/hermes/candidates").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isUnauthorized());
        verify(jwt, never()).validateToken("ADMIN");
        when(blacklist.isBlacklisted("ADMIN")).thenReturn(false);
        when(users.findByUsername("ADMIN").orElseThrow().isEnabled()).thenReturn(false);
        mvc.perform(get("/api/hermes/candidates").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtTokenService jwt() { return mock(JwtTokenService.class); }
        @Bean UserAccountRepository users() { return mock(UserAccountRepository.class); }
        @Bean TokenBlacklistService blacklist() { return mock(TokenBlacklistService.class); }
        @Bean ProbeController probe() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @RequestMapping({"/api/hermes/candidates", "/api/hermes/candidates/c/approve", "/api/eval/runs",
                "/api/admin/audit", "/actuator/metrics", "/api/agent/traces/chat"})
        String probe() { return "ok"; }
    }
}

package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.model.RefreshToken;
import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.RefreshTokenRepository;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenConcurrencyTest {
    @Test void sameTokenCanBeConsumedOnlyOnceAcrossTransactions() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_refresh_token(id BIGSERIAL PRIMARY KEY, token_hash VARCHAR(64) UNIQUE, username VARCHAR(64), expires_at TIMESTAMP, created_at TIMESTAMP, revoked_at TIMESTAMP)");
        var tokens = spy(new RefreshTokenRepository(jdbc));
        String hash = JwtTokenService.getTokenHash("shared-refresh");
        tokens.save(RefreshToken.create(hash, "alice", Instant.now().plusSeconds(600)));
        var barrier = new CyclicBarrier(4);
        doAnswer(call -> {
            Object stored = call.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS); // All requests have read the same active token.
            return stored;
        }).when(tokens).findByTokenHash(hash);
        var users = mock(UserAccountRepository.class);
        when(users.findByUsername("alice")).thenReturn(Optional.of(UserAccount.create("alice", "hash", "Alice")));
        var props = new AuthProperties();
        props.setJwtSecret("0123456789abcdef0123456789abcdef");
        var service = new AuthService(users, tokens, new BCryptPasswordEncoder(), new JwtTokenService(props), mock(TokenBlacklistService.class), props);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i=0; i<4; i++) futures.add(pool.submit(() -> {
                try { tx.execute(s -> service.refreshToken("shared-refresh")); return true; }
                catch (IllegalArgumentException expected) { return false; }
            }));
            int successes=0;
            for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_refresh_token WHERE revoked_at IS NULL", Integer.class));
    }
}

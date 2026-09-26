package com.sxw.sxwaiagent.hermes;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class HermesCandidateRepositoryTest {
    @Test
    void postgresNowaitConflictIsTreatedAsBusyCandidate() {
        var jdbc = mock(JdbcTemplate.class);
        var sql = new UncategorizedSQLException("query", "SELECT ... FOR UPDATE NOWAIT",
                new SQLException("row is locked", "55P03"));
        doThrow(sql).when(jdbc).query(anyString(), any(RowMapper.class), eq("locked"));

        var error = assertThrows(CannotAcquireLockException.class,
                () -> new HermesCandidateRepository(jdbc).findByIdForUpdate("locked"));
        assertSame(sql, error.getCause());
    }

    @Test
    void unrelatedDatabaseErrorIsNotMisclassifiedAsLockContention() {
        var jdbc = mock(JdbcTemplate.class);
        var sql = new UncategorizedSQLException("query", "SELECT ... FOR UPDATE NOWAIT",
                new SQLException("undefined table", "42P01"));
        doThrow(sql).when(jdbc).query(anyString(), any(RowMapper.class), eq("broken"));

        assertSame(sql, assertThrows(UncategorizedSQLException.class,
                () -> new HermesCandidateRepository(jdbc).findByIdForUpdate("broken")));
    }
}

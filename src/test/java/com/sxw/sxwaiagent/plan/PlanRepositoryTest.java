package com.sxw.sxwaiagent.plan;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanRepositoryTest {
    @Test
    void listsOnlyRequestedConversationPlansNewestFirstIncludingPlansWithoutSteps() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE ai_plan(plan_id VARCHAR PRIMARY KEY, chat_id VARCHAR, goal VARCHAR, "
                + "status VARCHAR, created_by VARCHAR, reviewed_by VARCHAR, created_at TIMESTAMP, reviewed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE ai_plan_step(plan_id VARCHAR, step_index INT, description VARCHAR, tool_name VARCHAR, status VARCHAR)");
        jdbc.update("INSERT INTO ai_plan(plan_id,chat_id,goal,status,created_at) VALUES "
                + "('old','chat','old goal','PENDING','2026-09-01 10:00:00'),"
                + "('new','chat','new goal','APPROVED','2026-09-02 10:00:00'),"
                + "('other','other-chat','other goal','PENDING','2026-09-03 10:00:00')");
        jdbc.update("INSERT INTO ai_plan_step VALUES ('new',0,'search','searchTool','PENDING'),"
                + "('new',1,'answer',NULL,'PENDING')");

        List<Plan> plans = new PlanRepository(jdbc).findByChatId("chat");

        assertEquals(List.of("new", "old"), plans.stream().map(Plan::planId).toList());
        assertEquals(2, plans.getFirst().steps().size());
        assertEquals(0, plans.getLast().steps().size());
        assertEquals(List.of(), new PlanRepository(jdbc).findByChatId("missing"));
    }
}

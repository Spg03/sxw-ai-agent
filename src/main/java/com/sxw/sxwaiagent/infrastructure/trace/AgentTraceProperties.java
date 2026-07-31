package com.sxw.sxwaiagent.infrastructure.trace;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sxw.agent.trace")
public class AgentTraceProperties {

    @Min(value = 1, message = "maxRuns must be at least 1")
    @Max(value = 100000, message = "maxRuns must not exceed 100000")
    private int maxRuns = 100;

    @Min(value = 10, message = "maxEventsPerRun must be at least 10")
    @Max(value = 10000, message = "maxEventsPerRun must not exceed 10000")
    private int maxEventsPerRun = 200;

    public int getMaxRuns() {
        return maxRuns;
    }

    public void setMaxRuns(int maxRuns) {
        this.maxRuns = maxRuns;
    }

    public int getMaxEventsPerRun() {
        return maxEventsPerRun;
    }

    public void setMaxEventsPerRun(int maxEventsPerRun) {
        this.maxEventsPerRun = maxEventsPerRun;
    }
}

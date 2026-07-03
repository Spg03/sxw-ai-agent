package com.sxw.sxwaiagent.infrastructure.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sxw.agent.trace")
public class AgentTraceProperties {

    private int maxRuns = 100;

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

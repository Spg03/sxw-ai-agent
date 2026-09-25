package com.sxw.sxwaiagent.security;

public enum PromptRiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;
    public boolean blocksRequest() { return this == HIGH || this == CRITICAL; }
    public boolean blocksDangerousTools() { return this.ordinal() >= MEDIUM.ordinal(); }
}

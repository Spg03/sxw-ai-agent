package com.sxw.sxwaiagent.context;

public class ContextBudgetExceededException extends RuntimeException {
    public ContextBudgetExceededException(int usedTokens, int limit) {
        super("Current request and mandatory policy require " + usedTokens
                + " input tokens, exceeding the limit of " + limit);
    }
}

package com.sxw.sxwaiagent.manus;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract agent class implementing ReAct (Reasoning and Acting) pattern.
 *
 * Implements think-act loop pattern.
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * Process current state and decide next action.
     *
     * @return true if action is needed, false if done
     */
    public abstract boolean think();

    /**
     * Execute the decided action.
     *
     * @return Action execution result
     */
    public abstract String act();

    /**
     * Execute single step: think and act.
     *
     * @return Step execution result
     */
    @Override
    public String step() {
        try {
            // Think first
            boolean shouldAct = think();
            if (!shouldAct) {
                return "Think done - no action needed";
            }
            // Then act
            return act();
        } catch (RuntimeException e) {
            log.error("step failed", e);
            return "Step execution failed: " + e.getMessage();
        }
    }

}

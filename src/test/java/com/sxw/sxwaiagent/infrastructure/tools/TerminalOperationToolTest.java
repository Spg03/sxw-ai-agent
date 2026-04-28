package com.sxw.sxwaiagent.infrastructure.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalOperationToolTest {

    private static TerminalOperationProperties props(boolean enabled, List<String> allow) {
        TerminalOperationProperties p = new TerminalOperationProperties();
        p.setEnabled(enabled);
        p.setTimeoutSeconds(5);
        p.setMaxOutputChars(4096);
        p.setAllowedCommands(allow);
        return p;
    }

    @Test
    void disabledByDefault() {
        TerminalOperationTool tool = new TerminalOperationTool(props(false, List.of("echo")));
        String result = tool.executeTerminalCommand("echo hi");
        assertTrue(result.startsWith("refused"), result);
    }

    @Test
    void blocksForbiddenMetacharacters() {
        TerminalOperationTool tool = new TerminalOperationTool(props(true, List.of("echo")));
        assertTrue(tool.executeTerminalCommand("echo hi && rm -rf /").startsWith("refused"));
        assertTrue(tool.executeTerminalCommand("echo `whoami`").startsWith("refused"));
        assertTrue(tool.executeTerminalCommand("echo $PATH").startsWith("refused"));
    }

    @Test
    void blocksNonWhitelistedCommand() {
        TerminalOperationTool tool = new TerminalOperationTool(props(true, List.of("echo")));
        assertTrue(tool.executeTerminalCommand("rm file").startsWith("refused"));
    }

    @Test
    void executeAllowedEcho() {
        TerminalOperationTool tool = new TerminalOperationTool(props(true, List.of("echo")));
        String result = tool.executeTerminalCommand("echo hello-world");
        assertNotNull(result);
        assertTrue(result.contains("hello-world"), result);
    }
}


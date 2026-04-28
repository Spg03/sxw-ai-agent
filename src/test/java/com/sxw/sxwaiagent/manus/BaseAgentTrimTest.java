package com.sxw.sxwaiagent.manus;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentTrimTest {

    /** 一个最小子类用来测试受保护的 trim 方法。*/
    static class TestAgent extends BaseAgent {
        @Override
        public String step() { return ""; }

        public void runTrim() { trimHistoryIfNeeded(); }
    }

    private static TestAgent agent(int max, List<Message> initial) {
        TestAgent a = new TestAgent();
        a.setName("test");
        a.setMaxHistoryMessages(max);
        a.getMessageList().addAll(initial);
        return a;
    }

    @Test
    void noTrimWhenWithinLimit() {
        List<Message> ms = new ArrayList<>();
        ms.add(new SystemMessage("sys"));
        for (int i = 0; i < 5; i++) ms.add(new UserMessage("u" + i));
        TestAgent a = agent(60, ms);
        a.runTrim();
        assertEquals(6, a.getMessageList().size());
    }

    @Test
    void preservesSystemAndTrimsOldest() {
        List<Message> ms = new ArrayList<>();
        ms.add(new SystemMessage("sys"));
        for (int i = 0; i < 100; i++) ms.add(new UserMessage("u" + i));
        TestAgent a = agent(20, ms);
        a.runTrim();
        assertEquals(20, a.getMessageList().size());
        assertTrue(a.getMessageList().get(0) instanceof SystemMessage,
                "first message must remain SystemMessage");
        // 最新的消息保留在尾部
        UserMessage last = (UserMessage) a.getMessageList().get(a.getMessageList().size() - 1);
        assertEquals("u99", last.getText());
    }

    @Test
    void doesNotLeaveDanglingToolResponseAtHead() {
        List<Message> ms = new ArrayList<>();
        ms.add(new SystemMessage("sys"));
        for (int i = 0; i < 5; i++) ms.add(new UserMessage("u" + i));
        // 把若干 ToolResponseMessage 放在裁剪边界附近
        ms.add(new AssistantMessage("a-with-tool"));
        ms.add(new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse("id1", "tool1", "ok"))));
        ms.add(new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse("id2", "tool2", "ok"))));
        ms.add(new UserMessage("after"));
        // 设 max 小到迫使裁剪，且窗口起点会落在 ToolResponseMessage 上
        TestAgent a = agent(2, ms);
        a.runTrim();
        // 裁剪后第一条非 system 的消息一定不是 ToolResponseMessage
        List<Message> remaining = a.getMessageList();
        assertTrue(remaining.get(0) instanceof SystemMessage);
        assertFalse(remaining.get(1) instanceof ToolResponseMessage,
                "head after system must not be a ToolResponseMessage");
    }

    @Test
    void disabledWhenMaxLeZero() {
        List<Message> ms = new ArrayList<>();
        for (int i = 0; i < 50; i++) ms.add(new UserMessage("u" + i));
        TestAgent a = agent(0, ms);
        a.runTrim();
        assertEquals(50, a.getMessageList().size());
    }
}

package com.sxw.sxwaiagent.manus;

import com.sxw.sxwaiagent.infrastructure.skill.SkillRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("eval")
class SxwManusTest {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private SkillRegistry skillRegistry;

    @Test
    public void run() {
        SxwManus sxwManus = new SxwManus(allTools, dashscopeChatModel, skillRegistry);
        String userPrompt = """
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，
                并结合一些网络图片，制定一份详细的约会计划，
                并以 PDF 格式输出""";
        String answer = sxwManus.run(userPrompt);
        Assertions.assertTrue(answer != null && !answer.isBlank());
    }
}
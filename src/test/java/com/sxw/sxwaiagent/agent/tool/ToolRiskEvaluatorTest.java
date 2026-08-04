package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.AgentProfile;
import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.profile.ToolPolicy;
import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ToolRiskEvaluator 单元测试
 * <p>
 * 覆盖工具风险评估的各种场景：
 * - 工具未注册
 * - Profile 未启用
 * - 风险等级超限
 * - 需要审批
 * - 正常通过
 */
class ToolRiskEvaluatorTest {

    private ToolRegistry toolRegistry;
    private ToolRiskEvaluator evaluator;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        evaluator = new ToolRiskEvaluator(toolRegistry);
    }

    @Test
    @DisplayName("工具未注册：拒绝，code=NOT_REGISTERED")
    void evaluate_toolNotRegistered_rejected() {
        AgentProfile profile = mockProfile(ToolPolicy.readOnly());

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("unknownTool", profile);

        assertTrue(result.isRejected());
        assertEquals("NOT_REGISTERED", result.rejectCode());
        assertFalse(result.allowed());
    }

    @Test
    @DisplayName("Profile 未启用该工具：拒绝，code=PROFILE_DISABLED")
    void evaluate_profileDisabled_rejected() {
        // 注册一个只对 GENERAL 启用的工具
        ToolDefinition def = new ToolDefinition(
                "fileOp", "文件操作", ToolRiskLevel.LOCAL_WRITE,
                false, false, true, false,
                List.of(AgentProfileCode.GENERAL)
        );
        toolRegistry.register(def);

        // 用 LOVE profile 去调用
        AgentProfile loveProfile = mockProfile(ToolPolicy.localWrite());
        when(loveProfile.code()).thenReturn(AgentProfileCode.LOVE);

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("fileOp", loveProfile);

        assertTrue(result.isRejected());
        assertEquals("PROFILE_DISABLED", result.rejectCode());
    }

    @Test
    @DisplayName("风险等级超限：拒绝，code=RISK_EXCEEDED")
    void evaluate_riskExceeded_rejected() {
        // 注册一个 DESTRUCTIVE 工具
        toolRegistry.register(ToolDefinition.destructive("deleteAll", "删除所有数据"));

        // Profile 只允许 READ_ONLY
        AgentProfile profile = mockProfile(ToolPolicy.readOnly());

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("deleteAll", profile);

        assertTrue(result.isRejected());
        assertEquals("RISK_EXCEEDED", result.rejectCode());
    }

    @Test
    @DisplayName("READ_ONLY 工具 + readOnly 策略：允许，无需审批")
    void evaluate_readOnlyTool_allowed() {
        toolRegistry.register(ToolDefinition.readOnly("webSearch", "网络搜索"));

        AgentProfile profile = mockProfile(ToolPolicy.readOnly());

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("webSearch", profile);

        assertTrue(result.allowed());
        assertFalse(result.needsApproval());
        assertTrue(result.needsAudit());
        assertFalse(result.isRejected());
    }

    @Test
    @DisplayName("LOCAL_WRITE 工具 + localWrite 策略：允许")
    void evaluate_localWriteTool_allowed() {
        toolRegistry.register(ToolDefinition.localWrite("noteWrite", "笔记写入"));

        AgentProfile profile = mockProfile(ToolPolicy.localWrite());

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("noteWrite", profile);

        assertTrue(result.allowed());
        assertFalse(result.needsApproval());
    }

    @Test
    @DisplayName("需要审批的工具：allowed=true, needsApproval=true")
    void evaluate_requiresApproval_allowedButNeedsApproval() {
        toolRegistry.register(ToolDefinition.externalWrite("sendEmail", "发送邮件"));

        AgentProfile profile = mockProfile(
                new ToolPolicy(ToolRiskLevel.EXTERNAL_WRITE, false, true)
        );

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("sendEmail", profile);

        assertTrue(result.allowed());
        assertTrue(result.needsApproval());
    }

    @Test
    @DisplayName("strict 策略：所有工具都需要审批")
    void evaluate_strictPolicy_alwaysNeedsApproval() {
        toolRegistry.register(ToolDefinition.readOnly("search", "搜索"));

        AgentProfile profile = mockProfile(ToolPolicy.strict());

        ToolRiskEvaluator.EvaluationResult result = evaluator.evaluate("search", profile);

        assertTrue(result.allowed());
        assertTrue(result.needsApproval());
        assertTrue(result.needsAudit());
    }

    @Test
    @DisplayName("enabledProfiles 为空表示所有 Profile 可用")
    void evaluate_emptyEnabledProfiles_allProfilesAllowed() {
        toolRegistry.register(ToolDefinition.readOnly("globalSearch", "全局搜索"));

        AgentProfile loveProfile = mockProfile(ToolPolicy.readOnly());
        when(loveProfile.code()).thenReturn(AgentProfileCode.LOVE);

        AgentProfile generalProfile = mockProfile(ToolPolicy.readOnly());
        when(generalProfile.code()).thenReturn(AgentProfileCode.GENERAL);

        assertTrue(evaluator.evaluate("globalSearch", loveProfile).allowed());
        assertTrue(evaluator.evaluate("globalSearch", generalProfile).allowed());
    }

    // ───────────────────── helpers ─────────────────────

    private AgentProfile mockProfile(ToolPolicy toolPolicy) {
        AgentProfile profile = mock(AgentProfile.class);
        when(profile.code()).thenReturn(AgentProfileCode.GENERAL);
        when(profile.toolPolicy()).thenReturn(toolPolicy);
        return profile;
    }
}

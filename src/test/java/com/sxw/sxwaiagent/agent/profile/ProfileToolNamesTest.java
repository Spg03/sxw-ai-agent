package com.sxw.sxwaiagent.agent.profile;

import com.sxw.sxwaiagent.agent.tool.ToolDefinition;
import com.sxw.sxwaiagent.agent.tool.ToolGovernanceConfig;
import com.sxw.sxwaiagent.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证所有 AgentProfile 的 enabledToolNames 与 ToolGovernanceConfig 注册表的一致性。
 * <p>
 * 确保：
 * <ul>
 *   <li>每个 Profile 声明的工具名都在 ToolGovernanceConfig 中实际注册</li>
 *   <li>每个 Profile 的工具风险等级不超过其 ToolPolicy 上限</li>
 *   <li>OutputPolicy.maxTokens 合理</li>
 * </ul>
 */
class ProfileToolNamesTest {

    private static ToolRegistry toolRegistry;

    @BeforeAll
    static void setUpRegistry() {
        toolRegistry = new ToolRegistry();
        ToolGovernanceConfig config = new ToolGovernanceConfig(toolRegistry);
        config.registerAllToolDefinitions();
    }

    // ── LoveProfile ─────────────────────────────────────────────────

    @Nested
    @DisplayName("LoveProfile")
    class LoveProfileTests {

        private final LoveProfile profile = new LoveProfile();

        @Test
        @DisplayName("所有 enabledToolNames 都在 ToolGovernanceConfig 中注册")
        void toolNamesExistInRegistry() {
            for (String toolName : profile.enabledToolNames()) {
                assertTrue(toolRegistry.exists(toolName),
                        "LoveProfile tool '" + toolName + "' is NOT registered in ToolGovernanceConfig");
            }
        }

        @Test
        @DisplayName("所有工具风险等级不超过 readOnly 策略")
        void toolRiskWithinPolicy() {
            ToolPolicy policy = profile.toolPolicy();
            for (String toolName : profile.enabledToolNames()) {
                ToolDefinition def = toolRegistry.get(toolName).orElseThrow();
                assertTrue(def.isWithinRiskLimit(policy.maxRiskLevel()),
                        "LoveProfile tool '" + toolName + "' risk " + def.riskLevel()
                                + " exceeds policy max " + policy.maxRiskLevel());
            }
        }

        @Test
        @DisplayName("只包含只读工具（READ_ONLY）")
        void onlyReadOnlyTools() {
            for (String toolName : profile.enabledToolNames()) {
                ToolDefinition def = toolRegistry.get(toolName).orElseThrow();
                assertTrue(def.readOnly(),
                        "LoveProfile should only include readOnly tools, but '"
                                + toolName + "' has readOnly=false");
            }
        }

        @Test
        @DisplayName("OutputPolicy.maxTokens 合理 (>= 512)")
        void outputPolicyReasonable() {
            assertTrue(profile.outputPolicy().maxTokens() >= 512,
                    "LoveProfile maxTokens too low: " + profile.outputPolicy().maxTokens());
        }
    }

    // ── GeneralProfile ──────────────────────────────────────────────

    @Nested
    @DisplayName("GeneralProfile")
    class GeneralProfileTests {

        private final GeneralProfile profile = new GeneralProfile();

        @Test
        @DisplayName("所有 enabledToolNames 都在 ToolGovernanceConfig 中注册")
        void toolNamesExistInRegistry() {
            for (String toolName : profile.enabledToolNames()) {
                assertTrue(toolRegistry.exists(toolName),
                        "GeneralProfile tool '" + toolName + "' is NOT registered in ToolGovernanceConfig");
            }
        }

        @Test
        @DisplayName("所有工具风险等级不超过 localWrite 策略")
        void toolRiskWithinPolicy() {
            ToolPolicy policy = profile.toolPolicy();
            for (String toolName : profile.enabledToolNames()) {
                ToolDefinition def = toolRegistry.get(toolName).orElseThrow();
                assertTrue(def.isWithinRiskLimit(policy.maxRiskLevel()),
                        "GeneralProfile tool '" + toolName + "' risk " + def.riskLevel()
                                + " exceeds policy max " + policy.maxRiskLevel());
            }
        }

        @Test
        @DisplayName("不包含 SHELL 级别工具（executeTerminalCommand）")
        void noShellTools() {
            for (String toolName : profile.enabledToolNames()) {
                ToolDefinition def = toolRegistry.get(toolName).orElseThrow();
                assertNotEquals(ToolRiskLevel.SHELL, def.riskLevel(),
                        "GeneralProfile should not include SHELL tool '" + toolName + "'");
            }
        }

        @Test
        @DisplayName("不包含 DESTRUCTIVE 级别工具")
        void noDestructiveTools() {
            for (String toolName : profile.enabledToolNames()) {
                ToolDefinition def = toolRegistry.get(toolName).orElseThrow();
                assertNotEquals(ToolRiskLevel.DESTRUCTIVE, def.riskLevel(),
                        "GeneralProfile should not include DESTRUCTIVE tool '" + toolName + "'");
            }
        }

        @Test
        @DisplayName("包含核心读写工具")
        void containsCoreTools() {
            List<String> names = profile.enabledToolNames();
            assertTrue(names.contains("searchRagFlow"), "should contain searchRagFlow");
            assertTrue(names.contains("readFile"), "should contain readFile");
            assertTrue(names.contains("writeFile"), "should contain writeFile");
            assertTrue(names.contains("searchNotes"), "should contain searchNotes");
        }

        @Test
        @DisplayName("OutputPolicy.maxTokens 合理 (>= 1024)")
        void outputPolicyReasonable() {
            assertTrue(profile.outputPolicy().maxTokens() >= 1024,
                    "GeneralProfile maxTokens too low: " + profile.outputPolicy().maxTokens());
        }
    }

    // ── HermesProfile ───────────────────────────────────────────────

    @Nested
    @DisplayName("HermesProfile")
    class HermesProfileTests {

        private final HermesProfile profile = new HermesProfile();

        @Test
        @DisplayName("不启用任何工具")
        void noTools() {
            assertTrue(profile.enabledToolNames().isEmpty(),
                    "HermesProfile should have no tools, but has: " + profile.enabledToolNames());
        }

        @Test
        @DisplayName("OutputPolicy.maxTokens 合理 (>= 512)")
        void outputPolicyReasonable() {
            assertTrue(profile.outputPolicy().maxTokens() >= 512,
                    "HermesProfile maxTokens too low: " + profile.outputPolicy().maxTokens());
        }
    }

    // ── 跨 Profile 一致性 ──────────────────────────────────────────

    @Test
    @DisplayName("所有 Profile 的 enabledToolNames 都在 ToolGovernanceConfig 注册表中")
    void allProfilesToolNamesMatchRegistry() {
        List<AgentProfile> profiles = List.of(
                new LoveProfile(), new GeneralProfile(), new HermesProfile());

        for (AgentProfile profile : profiles) {
            for (String toolName : profile.enabledToolNames()) {
                assertTrue(toolRegistry.exists(toolName),
                        "Profile " + profile.code() + " references tool '"
                                + toolName + "' which is NOT in ToolGovernanceConfig");
            }
        }
    }
}

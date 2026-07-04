package com.sxw.sxwaiagent.agent.tool;

import com.sxw.sxwaiagent.agent.profile.AgentProfileCode;
import com.sxw.sxwaiagent.agent.profile.ToolRiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 * <p>
 * 管理所有工具的 ToolDefinition，提供查询、验证和过滤功能。
 * 工具在初始化时通过 ToolRegistration 注册到此处。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具定义
     */
    public void register(ToolDefinition definition) {
        if (definition == null || definition.name() == null) {
            throw new IllegalArgumentException("ToolDefinition and name must not be null");
        }

        if (tools.containsKey(definition.name())) {
            log.warn("Tool already registered: {}, overwriting", definition.name());
        }

        tools.put(definition.name(), definition);
        log.debug("Registered tool: {} (riskLevel={})", definition.name(), definition.riskLevel());
    }

    /**
     * 批量注册工具定义
     */
    public void registerAll(List<ToolDefinition> definitions) {
        if (definitions == null) {
            return;
        }
        definitions.forEach(this::register);
    }

    /**
     * 获取工具定义
     */
    public Optional<ToolDefinition> get(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * 检查工具是否存在
     */
    public boolean exists(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * 检查工具是否对指定 Profile 启用
     */
    public boolean isEnabledFor(String toolName, AgentProfileCode profileCode) {
        ToolDefinition def = tools.get(toolName);
        if (def == null) {
            return false;
        }
        return def.isEnabledFor(profileCode);
    }

    /**
     * 检查工具风险等级是否在允许范围内
     */
    public boolean isWithinRiskLimit(String toolName, ToolRiskLevel maxAllowed) {
        ToolDefinition def = tools.get(toolName);
        if (def == null) {
            return false;
        }
        return def.isWithinRiskLimit(maxAllowed);
    }

    /**
     * 获取指定 Profile 可用的所有工具
     */
    public List<ToolDefinition> getEnabledTools(AgentProfileCode profileCode) {
        return tools.values().stream()
                .filter(def -> def.isEnabledFor(profileCode))
                .toList();
    }

    /**
     * 获取指定风险等级范围内的所有工具
     */
    public List<ToolDefinition> getToolsWithinRiskLimit(ToolRiskLevel maxRiskLevel) {
        return tools.values().stream()
                .filter(def -> def.isWithinRiskLimit(maxRiskLevel))
                .toList();
    }

    /**
     * 获取所有已注册的工具
     */
    public List<ToolDefinition> getAll() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取已注册工具数量
     */
    public int size() {
        return tools.size();
    }
}

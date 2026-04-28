package com.sxw.sxwaiagent.infrastructure.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";
    private static final int MAX_RESULTS = 5;
    private static final int MAX_OUTPUT_CHARS = 20_000;

    private final String apiKey;
    private final String searchApiUrl;

    public WebSearchTool(String apiKey) {
        this(apiKey, SEARCH_API_URL);
    }

    WebSearchTool(String apiKey, String searchApiUrl) {
        this.apiKey = apiKey;
        this.searchApiUrl = searchApiUrl;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(searchApiUrl, paramMap);
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "[]";
            }
            int end = Math.min(MAX_RESULTS, organicResults.size());
            List<Object> objects = organicResults.subList(0, end);
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return ToolSandboxSupport.limitOutput(result, MAX_OUTPUT_CHARS);
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}

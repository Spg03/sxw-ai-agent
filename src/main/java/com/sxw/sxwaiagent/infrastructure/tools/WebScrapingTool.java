package com.sxw.sxwaiagent.infrastructure.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_OUTPUT_CHARS = 20_000;

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            java.net.URI uri = ToolSandboxSupport.validateHttpUrl(url);
            Document document = Jsoup.connect(uri.toString())
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .get();
            return ToolSandboxSupport.limitOutput(document.outerHtml(), MAX_OUTPUT_CHARS);
        } catch (IllegalArgumentException e) {
            return "refused: " + e.getMessage();
        } catch (Exception e) {
            return "failed: " + e.getMessage();
        }
    }
}

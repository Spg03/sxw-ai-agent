package com.sxw.sxwaiagent.infrastructure.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void scrapeWebPageFromLocalServer() throws Exception {
        String longText = "x".repeat(30_000);
        String html = "<html><head><title>demo</title></head><body>" + longText + "</body></html>";
        byte[] payload = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/page", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String result = webScrapingTool.scrapeWebPage("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/page");

        assertTrue(result.contains("<title>demo</title>"), result);
        assertTrue(result.contains("[truncated") || result.length() <= 20_100, "output should be capped");
    }

    @Test
    void rejectInvalidUrlScheme() {
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        String result = webScrapingTool.scrapeWebPage("ftp://example.com/a");
        assertTrue(result.startsWith("refused:"), result);
    }
}

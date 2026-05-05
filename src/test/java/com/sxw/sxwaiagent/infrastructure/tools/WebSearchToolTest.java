package com.sxw.sxwaiagent.infrastructure.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void searchWebWithLocalStub() throws Exception {
        String json = """
                {
                  "organic_results": [
                    {"title":"A","link":"https://a.example"},
                    {"title":"B","link":"https://b.example"}
                  ]
                }
                """;

        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        String endpoint = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/search";
        WebSearchTool webSearchTool = new WebSearchTool("fake-key", endpoint);
        String query = "programming";
        String result = webSearchTool.searchWeb(query);

        assertTrue(result.contains("\"title\":\"A\""), result);
        assertTrue(result.contains("\"title\":\"B\""), result);
    }

    @Test
    void emptyOrganicResultsReturnsEmptyArrayString() throws Exception {
        String json = "{\"organic_results\": []}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/search", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        String endpoint = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/search";
        WebSearchTool webSearchTool = new WebSearchTool("fake-key", endpoint);
        String result = webSearchTool.searchWeb("anything");
        assertTrue(result.equals("[]"), result);
    }
}

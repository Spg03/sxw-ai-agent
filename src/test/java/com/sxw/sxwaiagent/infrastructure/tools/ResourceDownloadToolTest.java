package com.sxw.sxwaiagent.infrastructure.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceDownloadToolTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void downloadResourceFromLocalServer() throws Exception {
        byte[] payload = "hello-download".getBytes(StandardCharsets.UTF_8);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/logo.png", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        String fileName = "logo-" + UUID.randomUUID() + ".png";
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String result = tool.downloadResource("http://127.0.0.1:" + port + "/logo.png", fileName);

        assertTrue(result.startsWith("ok:"), result);
        Path target = Path.of(System.getProperty("user.dir"), "tmp", "download", fileName);
        assertTrue(Files.exists(target), "downloaded file should exist");
        assertEquals("hello-download", Files.readString(target, StandardCharsets.UTF_8));
        Files.deleteIfExists(target);
    }

    @Test
    void rejectInvalidUrlScheme() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String result = tool.downloadResource("file:///etc/passwd", "x.txt");
        assertTrue(result.startsWith("refused:"), result);
    }
}
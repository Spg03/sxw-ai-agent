package com.sxw.sxwaiagent.infrastructure.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 资源下载工具
 */
public class ResourceDownloadTool {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final long MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024;
    private static final int MAX_OUTPUT_CHARS = 2048;

    private final ToolSandboxSupport sandbox = new ToolSandboxSupport("download");

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url,
                                   @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        try {
            java.net.URI uri = ToolSandboxSupport.validateHttpUrl(url);
            Path target = sandbox.resolveFile(fileName);

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status >= 400) {
                return "failed: http status " + status;
            }
            long total = 0;
            boolean exceeded = false;
            byte[] buffer = new byte[8192];
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(target,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        exceeded = true;
                        break;
                    }
                    out.write(buffer, 0, n);
                }
            }
            if (exceeded) {
                Files.deleteIfExists(target);
                return "refused: downloaded content exceeds max size " + MAX_DOWNLOAD_BYTES + " bytes";
            }
            String result = "ok: resource downloaded to " + target + " (bytes=" + total + ")";
            return ToolSandboxSupport.limitOutput(result, MAX_OUTPUT_CHARS);
        } catch (IllegalArgumentException e) {
            return "refused: " + e.getMessage();
        } catch (Exception e) {
            return "failed: " + e.getMessage();
        }
    }
}

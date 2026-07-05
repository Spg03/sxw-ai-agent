package com.sxw.sxwaiagent.common.web;

import java.util.Locale;

/**
 * Detect "client abort" exceptions (common in SSE/streaming scenarios).
 */
public final class ClientAbortDetector {

    private ClientAbortDetector() {
    }

    public static boolean isClientAbort(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String className = cur.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)
                    || "org.springframework.web.context.request.async.AsyncRequestNotUsableException".equals(className)
                    || cur.getClass().getSimpleName().contains("ClientAbort")) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase(Locale.ROOT);
                if (m.contains("broken pipe")
                        || m.contains("connection reset")
                        || m.contains("servletoutputstream failed to flush")
                        || m.contains("failed to flush")
                        || m.contains("forcibly closed by the remote host")
                        || m.contains("software caused connection abort")
                        || m.contains("你的主机中的软件中止了一个已建立的连接")
                        || m.contains("远程主机强迫关闭了一个现有的连接")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }
}

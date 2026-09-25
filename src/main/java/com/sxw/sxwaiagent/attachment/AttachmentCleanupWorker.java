package com.sxw.sxwaiagent.attachment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="sxw.agent.attachment.cleanup-enabled", havingValue="true", matchIfMissing=true)
public class AttachmentCleanupWorker {
    private final AttachmentCleanupService cleanup;
    public AttachmentCleanupWorker(AttachmentCleanupService cleanup) { this.cleanup = cleanup; }
    @Scheduled(fixedDelayString="${sxw.agent.attachment.cleanup-delay-ms:30000}", initialDelay=30000)
    public void poll() { cleanup.cleanDue(); }
}

package com.sxw.sxwaiagent.attachment;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;

@Service
public class AttachmentCleanupService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupService.class);
    private final JdbcTemplate jdbc;
    private final MinioClient minio;
    private final AttachmentProperties properties;

    public AttachmentCleanupService(JdbcTemplate jdbc, MinioClient minio, AttachmentProperties properties) {
        this.jdbc = jdbc;
        this.minio = minio;
        this.properties = properties;
    }

    public void enqueue(String key, Instant availableAt) {
        jdbc.update("""
            INSERT INTO ai_attachment_cleanup(object_key,available_at) VALUES (?,?)
            ON CONFLICT(object_key) DO UPDATE SET available_at=EXCLUDED.available_at
            """, key, Timestamp.from(availableAt));
    }

    public void cancel(String key) {
        jdbc.update("DELETE FROM ai_attachment_cleanup WHERE object_key=?", key);
    }

    public void cleanDue() {
        var keys = jdbc.queryForList("""
            SELECT object_key FROM ai_attachment_cleanup
            WHERE available_at <= CURRENT_TIMESTAMP ORDER BY available_at LIMIT 50
            """, String.class);
        for (String key : keys) {
            try {
                // A commit acknowledgement can be lost after upload succeeds. Never delete a referenced object.
                Integer references = jdbc.queryForObject("SELECT COUNT(*) FROM ai_chat_attachment WHERE object_key=? AND status='READY'", Integer.class, key);
                if (references != null && references > 0) { cancel(key); continue; }
                minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
                cancel(key);
            } catch (Exception e) {
                jdbc.update("""
                    UPDATE ai_attachment_cleanup SET attempts=attempts+1, available_at=?, last_error=?
                    WHERE object_key=?
                    """, Timestamp.from(Instant.now().plusSeconds(300)), e.getClass().getSimpleName(), key);
                log.warn("Attachment cleanup deferred; retry scheduled ({})", e.getClass().getSimpleName());
            }
        }
    }
}

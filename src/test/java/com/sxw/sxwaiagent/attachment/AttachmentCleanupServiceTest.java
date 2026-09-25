package com.sxw.sxwaiagent.attachment;

import io.minio.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttachmentCleanupServiceTest {
    @Test void cleanupNeverRemovesAnObjectReferencedBySuccessfulUpload() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var minio=mock(MinioClient.class);
        when(jdbc.queryForList(anyString(),eq(String.class))).thenReturn(List.of("ready-object"));
        when(jdbc.queryForObject(contains("ai_chat_attachment"),eq(Integer.class),eq("ready-object"))).thenReturn(1);
        var service=new AttachmentCleanupService(jdbc,minio,new AttachmentProperties(true,"http://localhost:9000","key","secret","attachments",1024));
        service.cleanDue();
        verify(minio,never()).removeObject(any());
        verify(jdbc).update("DELETE FROM ai_attachment_cleanup WHERE object_key=?","ready-object");
    }

    @Test void failedRemovalKeepsJobAndSchedulesRetry() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        var minio=mock(MinioClient.class);
        when(jdbc.queryForList(anyString(),eq(String.class))).thenReturn(List.of("object"));
        doThrow(new java.io.IOException("offline")).when(minio).removeObject(any());
        var service=new AttachmentCleanupService(jdbc,minio,new AttachmentProperties(true,"http://localhost:9000","key","secret","attachments",1024));
        service.cleanDue();
        verify(jdbc,never()).update(startsWith("DELETE"),anyString());
        verify(jdbc).update(contains("attempts=attempts+1"),any(),eq("IOException"),eq("object"));
    }
}

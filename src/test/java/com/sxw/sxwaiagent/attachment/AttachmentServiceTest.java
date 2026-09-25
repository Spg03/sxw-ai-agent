package com.sxw.sxwaiagent.attachment;

import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttachmentServiceTest {
    private JdbcTemplate jdbc;
    private MinioClient minio;
    private AttachmentCleanupService cleanup;
    private AttachmentService service;

    @BeforeEach void setUp() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_conversation(conversation_id VARCHAR(64) PRIMARY KEY,user_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_chat_attachment(attachment_id VARCHAR(64) PRIMARY KEY,user_id BIGINT,conversation_id VARCHAR(64),original_name VARCHAR(255),content_type VARCHAR(128),size_bytes BIGINT,sha256 VARCHAR(64),object_key VARCHAR(512),extracted_text TEXT,status VARCHAR(32),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ai_conversation VALUES ('chat',1)");
        minio = mock(MinioClient.class);
        cleanup = mock(AttachmentCleanupService.class);
        service = new AttachmentService(jdbc,minio,new AttachmentProperties(true,"http://localhost:9000","key","secret","attachments",1024*1024),cleanup,new DataSourceTransactionManager(ds));
        lenient().when(minio.bucketExists(any())).thenReturn(true);
    }

    private MockMultipartFile file() { return new MockMultipartFile("file","resume.txt","text/plain","resume text".getBytes(StandardCharsets.UTF_8)); }

    @Test void uploadsAndReadsOnlyOwnersReadyAttachments() throws Exception {
        var result=service.upload(1,"chat",file());
        String id=(String)result.get("attachmentId");
        assertEquals("READY",result.get("status"));
        assertEquals(1,service.list(1,"chat").size());
        assertTrue(service.list(2,"chat").isEmpty());
        assertTrue(service.contextText(1,"chat",List.of(id)).contains("resume text"));
        assertThrows(IllegalArgumentException.class,()->service.contextText(2,"chat",List.of(id)));
        verify(cleanup).cancel(anyString());
    }

    @Test void uploadFailureRetainsDurableCleanupJob() throws Exception {
        when(minio.putObject(any())).thenThrow(new java.io.IOException("connection lost"));
        assertThrows(java.io.IOException.class,()->service.upload(1,"chat",file()));
        verify(cleanup,times(2)).enqueue(anyString(),any());
        verify(cleanup,never()).cancel(anyString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_chat_attachment",Integer.class));
    }

    @Test void conversationDeletedDuringUploadDoesNotLeaveReadyRow() throws Exception {
        when(minio.putObject(any())).thenAnswer(call->{jdbc.update("DELETE FROM ai_conversation");return null;});
        assertThrows(IllegalArgumentException.class,()->service.upload(1,"chat",file()));
        verify(cleanup,never()).cancel(anyString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_chat_attachment",Integer.class));
    }

    @Test void invalidDocumentIsRejectedBeforeStorageWrites() throws Exception {
        assertThrows(Exception.class,()->service.upload(1,"chat",new MockMultipartFile("file","bad.pdf","application/pdf",new byte[]{1,2,3})));
        verifyNoInteractions(cleanup);
        verify(minio,never()).putObject(any());
    }

    @Test void deletesQueueCleanupAndRejectsWrongOwner() throws Exception {
        String id=(String)service.upload(1,"chat",file()).get("attachmentId");
        assertThrows(IllegalArgumentException.class,()->service.delete(2,"chat",id));
        service.delete(1,"chat",id);
        assertTrue(service.list(1,"chat").isEmpty());
        verify(cleanup,times(2)).enqueue(anyString(),any());
        verify(minio,never()).removeObject(any()); // Worker owns physical deletion and retries.
    }

    @Test void conversationDeletionQueuesEveryObject() throws Exception {
        service.upload(1,"chat",file());
        service.upload(1,"chat",file());
        assertEquals(2,service.deleteConversationAttachments(1,"chat"));
        assertTrue(service.list(1,"chat").isEmpty());
        verify(cleanup,times(4)).enqueue(anyString(),any());
    }

    @Test void extractedTextIsBounded() throws Exception {
        assertEquals(30000,AttachmentService.extract("txt","a".repeat(40000).getBytes(StandardCharsets.UTF_8)).length());
        try (var document=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()) {
            for(int i=0;i<201;i++)document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(bytes);
            assertThrows(IllegalArgumentException.class,()->AttachmentService.extract("pdf",bytes.toByteArray()));
        }
    }

    @Test void unauthorizedUploadDoesNotParseOrWriteObjects() {
        assertThrows(IllegalArgumentException.class,()->service.upload(2,"chat",file()));
        assertThrows(IllegalArgumentException.class,()->service.upload(1,"missing",file()));
        verifyNoInteractions(minio, cleanup);
    }

    @Test void unavailableSelectionsFailClosedWithoutDisclosingTheirOwner() throws Exception {
        String id=(String)service.upload(1,"chat",file()).get("attachmentId");
        String missing = assertThrows(IllegalArgumentException.class,
                ()->service.contextText(1,"chat",List.of("missing"))).getMessage();
        assertEquals(missing,assertThrows(IllegalArgumentException.class,
                ()->service.contextText(2,"chat",List.of(id))).getMessage());
        assertEquals(missing,assertThrows(IllegalArgumentException.class,
                ()->service.contextText(1,"other-chat",List.of(id))).getMessage());
        assertThrows(IllegalArgumentException.class,()->service.contextText(1,"chat",List.of(id,"missing")));
        jdbc.update("UPDATE ai_chat_attachment SET status='PROCESSING' WHERE attachment_id=?",id);
        assertEquals(missing,assertThrows(IllegalArgumentException.class,
                ()->service.contextText(1,"chat",List.of(id))).getMessage());
    }

    @Test void selectedOrderIsStableAndDuplicateIdsAreNotRepeated() throws Exception {
        String first=(String)service.upload(1,"chat",new MockMultipartFile("file","first.txt","text/plain","first content".getBytes(StandardCharsets.UTF_8))).get("attachmentId");
        String second=(String)service.upload(1,"chat",new MockMultipartFile("file","second.txt","text/plain","second content".getBytes(StandardCharsets.UTF_8))).get("attachmentId");
        assertEquals("FILE: second.txt\nsecond content\n\nFILE: first.txt\nfirst content",
                service.contextText(1,"chat",List.of(second,first,second)));
        assertEquals("",service.contextText(1,"chat",List.of()));
        assertThrows(IllegalArgumentException.class,()->service.contextText(1,"chat",java.util.Arrays.asList(first,null)));
        assertThrows(IllegalArgumentException.class,()->service.contextText(1,"chat",List.of(" ")));
    }

    @Test void emptyTextAndImageOnlyPdfAreRejectedBeforeObjectStorage() throws Exception {
        assertThrows(IllegalArgumentException.class,()->service.upload(1,"chat",
                new MockMultipartFile("file","empty.txt","text/plain"," \n\t".getBytes(StandardCharsets.UTF_8))));
        try (var document=new org.apache.pdfbox.pdmodel.PDDocument(); var bytes=new java.io.ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(bytes);
            assertThrows(IllegalArgumentException.class,()->service.upload(1,"chat",
                    new MockMultipartFile("file","scan.pdf","application/pdf",bytes.toByteArray())));
        }
        verifyNoInteractions(minio, cleanup);
    }

    @Test void legacyReadyAttachmentWithoutExtractedTextIsRejected() throws Exception {
        String id=(String)service.upload(1,"chat",file()).get("attachmentId");
        jdbc.update("UPDATE ai_chat_attachment SET extracted_text='' WHERE attachment_id=?",id);
        assertThrows(IllegalArgumentException.class,()->service.contextText(1,"chat",List.of(id)));
    }

    @Test void docxIncludesResumeTablesInDocumentOrderAndRespectsTextLimit() throws Exception {
        try (var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var bytes=new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("before");
            document.createTable(1,2).getRow(0).getCell(0).setText("table resume");
            document.getTables().getFirst().getRow(0).getCell(1).setText("project experience");
            document.createParagraph().createRun().setText("after");
            document.write(bytes);
            String text=AttachmentService.extract("docx",bytes.toByteArray());
            assertTrue(text.indexOf("before") < text.indexOf("table resume"));
            assertTrue(text.indexOf("table resume") < text.indexOf("project experience"));
            assertTrue(text.indexOf("project experience") < text.indexOf("after"));
        }
        try (var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var bytes=new java.io.ByteArrayOutputStream()) {
            document.createTable().getRow(0).getCell(0).setText("a".repeat(40000));
            document.write(bytes);
            assertEquals(30000,AttachmentService.extract("docx",bytes.toByteArray()).length());
        }
    }
}

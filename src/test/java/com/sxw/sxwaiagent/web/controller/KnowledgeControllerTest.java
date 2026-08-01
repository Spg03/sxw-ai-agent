package com.sxw.sxwaiagent.web.controller;

import com.sxw.sxwaiagent.knowledge.DocumentIngestService;
import com.sxw.sxwaiagent.knowledge.IngestStatus;
import com.sxw.sxwaiagent.knowledge.KnowledgeRepository;
import com.sxw.sxwaiagent.knowledge.KnowledgeRetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KnowledgeController Web 层测试
 * <p>
 * 使用 @WebMvcTest 隔离 Controller 层，mock DocumentIngestService。
 */
@WebMvcTest(KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DocumentIngestService ingestService;
    @MockBean private KnowledgeRetrievalService retrievalService;

    // ── 文档上传 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/knowledge/documents 上传成功返回 docId")
    void uploadDocumentSuccess() throws Exception {
        var result = new DocumentIngestService.IngestResult("doc-123", 5, "OK", IngestStatus.CREATED);
        when(ingestService.ingestFromFile(any(), anyBoolean(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
            "file", "test.md", MediaType.TEXT_PLAIN_VALUE, "# Hello".getBytes());

        mockMvc.perform(multipart("/api/knowledge/documents").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.docId").value("doc-123"))
            .andExpect(jsonPath("$.data.chunkCount").value(5));
    }

    @Test
    @DisplayName("POST /api/knowledge/documents 上传失败返回错误")
    void uploadDocumentFailure() throws Exception {
        var result = new DocumentIngestService.IngestResult(null, 0, "文件为空", null);
        when(ingestService.ingestFromFile(any(), anyBoolean(), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.md", MediaType.TEXT_PLAIN_VALUE, "".getBytes());

        mockMvc.perform(multipart("/api/knowledge/documents").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }

    // ── 文本入库 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/knowledge/documents/text 文本入库成功")
    void ingestTextSuccess() throws Exception {
        var result = new DocumentIngestService.IngestResult("doc-txt", 3, "OK", IngestStatus.CREATED);
        when(ingestService.ingest(anyString(), anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(result);

        mockMvc.perform(post("/api/knowledge/documents/text")
                .param("title", "测试文档")
                .contentType(MediaType.TEXT_PLAIN)
                .content("这是测试内容"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.docId").value("doc-txt"));
    }

    // ── 列出文档 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/knowledge/documents 返回文档列表")
    void listDocuments() throws Exception {
        var docs = List.of(
            new KnowledgeRepository.KnowledgeDocumentRecord(
                "doc-1", "文档A", "/path/a.md", 10, "ACTIVE", "hash1", "fp1", Instant.now()),
            new KnowledgeRepository.KnowledgeDocumentRecord(
                "doc-2", "文档B", "/path/b.md", 5, "ACTIVE", "hash2", "fp2", Instant.now())
        );
        when(ingestService.listDocuments()).thenReturn(docs);

        mockMvc.perform(get("/api/knowledge/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].docId").value("doc-1"));
    }

    // ── 删除文档 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/knowledge/documents/{docId} 删除成功")
    void deleteDocument() throws Exception {
        mockMvc.perform(delete("/api/knowledge/documents/doc-del"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    // ── 重新索引 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/knowledge/documents/{docId}/content 重新索引成功")
    void reindexDocumentSuccess() throws Exception {
        var result = new DocumentIngestService.IngestResult("doc-re", 8, "OK", IngestStatus.REINDEXED);
        when(ingestService.reindex(eq("doc-re"), anyString(), any())).thenReturn(result);

        mockMvc.perform(put("/api/knowledge/documents/doc-re/content")
                .contentType(MediaType.TEXT_PLAIN)
                .content("新内容"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.docId").value("doc-re"));
    }

    @Test
    @DisplayName("PUT /api/knowledge/documents/{docId}/content 文档不存在返回错误")
    void reindexDocumentNotFound() throws Exception {
        when(ingestService.reindex(eq("nope"), anyString(), any()))
            .thenThrow(new IllegalArgumentException("Document not found: nope"));

        mockMvc.perform(put("/api/knowledge/documents/nope/content")
                .contentType(MediaType.TEXT_PLAIN)
                .content("content"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }
}

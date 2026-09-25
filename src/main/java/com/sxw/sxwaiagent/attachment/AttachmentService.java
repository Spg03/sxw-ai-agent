package com.sxw.sxwaiagent.attachment;

import io.minio.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class AttachmentService {
    private static final Set<String> EXT = Set.of("txt", "md", "pdf", "docx");
    private final JdbcTemplate jdbc;
    private final MinioClient minio;
    private final AttachmentProperties properties;
    private final AttachmentCleanupService cleanup;
    private final TransactionTemplate transactions;

    public AttachmentService(JdbcTemplate jdbc, MinioClient minio, AttachmentProperties properties,
                             AttachmentCleanupService cleanup, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.minio = minio;
        this.properties = properties;
        this.cleanup = cleanup;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> upload(long userId, String conversationId, MultipartFile file) throws Exception {
        if (!properties.enabled()) throw new IllegalStateException("附件服务未启用");
        if (file.isEmpty() || file.getSize() > properties.maxBytes()) throw new IllegalArgumentException("附件为空或超过大小限制");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("attachment");
        if (name.length() > 255) throw new IllegalArgumentException("文件名过长");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!EXT.contains(ext)) throw new IllegalArgumentException("仅支持 TXT、MD、PDF、DOCX");
        // Reject unauthorized requests before parsing or writing to object storage.
        // The transaction below rechecks ownership under a lock against concurrent deletion.
        if (jdbc.queryForList("SELECT user_id FROM ai_conversation WHERE conversation_id=? AND user_id=?",
                Long.class, conversationId, userId).isEmpty()) {
            throw new IllegalArgumentException("Conversation not found");
        }
        byte[] bytes = file.getBytes();
        // Parse before external writes. Reject malformed/oversized documents without creating objects.
        String text = extract(ext, bytes);
        if (text.isBlank()) throw new IllegalArgumentException("附件未提取到可读文字，请上传文本型 PDF、DOCX、TXT 或 MD；扫描件需先进行 OCR");
        String id = "att_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String key = "chat/" + userId + "/" + conversationId + "/" + id;
        String contentType = switch (ext) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "text/plain";
        };
        // Register cleanup BEFORE upload. Even a crash or ambiguous upload timeout leaves a durable job.
        cleanup.enqueue(key, Instant.now().plusSeconds(86400));
        try {
            ensureBucket();
            minio.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType(contentType).build());
            transactions.executeWithoutResult(status -> {
                // Serialize against conversation deletion, otherwise a concurrent delete can orphan this object.
                var owners = jdbc.queryForList("SELECT user_id FROM ai_conversation WHERE conversation_id=? AND user_id=? FOR UPDATE",
                        Long.class, conversationId, userId);
                if (owners.isEmpty()) throw new IllegalArgumentException("Conversation not found");
                jdbc.update("""
                    INSERT INTO ai_chat_attachment(attachment_id,user_id,conversation_id,original_name,
                        content_type,size_bytes,sha256,object_key,extracted_text,status)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, id, userId, conversationId, name, contentType, bytes.length, sha256(bytes), key, text, "READY");
                cleanup.cancel(key);
            });
        } catch (Exception e) {
            try { cleanup.enqueue(key, Instant.now()); } catch (Exception deferred) { e.addSuppressed(deferred); }
            throw e;
        }
        return Map.of("attachmentId", id, "name", name, "sizeBytes", bytes.length, "status", "READY");
    }

    private void ensureBucket() throws Exception {
        if (minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) return;
        try { minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build()); }
        catch (Exception e) {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) throw e;
        }
    }

    public List<Map<String, Object>> list(long user, String conversation) {
        return jdbc.query("""
            SELECT attachment_id,original_name,content_type,size_bytes,status,created_at
            FROM ai_chat_attachment WHERE user_id=? AND conversation_id=? ORDER BY created_at DESC
            """, (rs, n) -> Map.<String, Object>of("attachmentId", rs.getString(1), "name", rs.getString(2),
                "contentType", Optional.ofNullable(rs.getString(3)).orElse("application/octet-stream"),
                "sizeBytes", rs.getLong(4), "status", rs.getString(5), "createdAt", rs.getTimestamp(6).toInstant().toString()),
                user, conversation);
    }

    public String contextText(long user, String conversation, List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        if (ids.size() > 20) throw new IllegalArgumentException("每次最多选择 20 个附件");
        if (ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 64)) {
            throw new IllegalArgumentException("附件标识无效，请重新选择附件");
        }
        List<String> selected = new ArrayList<>(new LinkedHashSet<>(ids));
        String placeholders = String.join(",", Collections.nCopies(selected.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(user); args.add(conversation); args.addAll(selected);
        var rows = jdbc.query("SELECT attachment_id,original_name,extracted_text FROM ai_chat_attachment WHERE user_id=? AND conversation_id=? AND status='READY' AND attachment_id IN (" + placeholders + ")",
                (rs, n) -> new AttachmentText(rs.getString(1), rs.getString(2),
                        Optional.ofNullable(rs.getString(3)).orElse("")), args.toArray());
        // Never silently answer without part of the user's selected evidence. Use the same
        // error for absent, foreign, and non-ready attachments to avoid disclosing ownership.
        if (rows.size() != selected.size()) {
            throw new IllegalArgumentException("所选附件不可用或不属于当前会话，请重新选择或上传");
        }
        Map<String, AttachmentText> byId = new HashMap<>();
        for (var row : rows) {
            if (row.text().isBlank()) throw new IllegalArgumentException("所选附件没有可读文字，请重新上传文本文件");
            byId.put(row.id(), row);
        }
        String result = String.join("\n\n", selected.stream().map(id -> {
            var row = byId.get(id);
            return "FILE: " + row.name() + "\n" + row.text();
        }).toList());
        return result.substring(0, Math.min(result.length(), 24000));
    }

    private record AttachmentText(String id, String name, String text) { }

    @Transactional
    public void delete(long user, String conversation, String id) {
        var keys = jdbc.queryForList("SELECT object_key FROM ai_chat_attachment WHERE attachment_id=? AND user_id=? AND conversation_id=? FOR UPDATE",
                String.class, id, user, conversation);
        if (keys.isEmpty()) throw new IllegalArgumentException("Attachment not found");
        keys.stream().filter(key -> key != null && !key.isBlank()).forEach(key -> cleanup.enqueue(key, Instant.now()));
        jdbc.update("DELETE FROM ai_chat_attachment WHERE attachment_id=? AND user_id=? AND conversation_id=?", id, user, conversation);
    }

    @Transactional
    public int deleteConversationAttachments(long user, String conversation) {
        jdbc.queryForList("SELECT object_key FROM ai_chat_attachment WHERE user_id=? AND conversation_id=?",
                String.class, user, conversation).stream().filter(key -> key != null && !key.isBlank())
                .forEach(key -> cleanup.enqueue(key, Instant.now()));
        return jdbc.update("DELETE FROM ai_chat_attachment WHERE user_id=? AND conversation_id=?", user, conversation);
    }

    static String extract(String ext, byte[] bytes) throws IOException {
        if ("txt".equals(ext) || "md".equals(ext)) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            return text.substring(0, Math.min(text.length(), 30000));
        }
        if ("pdf".equals(ext)) {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.getNumberOfPages() > 200) throw new IllegalArgumentException("PDF 最多支持 200 页");
                var stripper = new PDFTextStripper();
                var writer = new LimitedWriter(30000);
                stripper.writeText(document, writer);
                return writer.toString();
            }
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            appendDocxText(document.getBodyElements(), text);
            return text.toString();
        }
    }

    private static void appendDocxText(List<IBodyElement> elements, StringBuilder text) {
        for (var element : elements) {
            if (text.length() >= 30000) return;
            if (element instanceof XWPFParagraph paragraph) {
                String value = paragraph.getText();
                if (value == null) continue;
                text.append(value, 0, Math.min(value.length(), 30000 - text.length()));
                if (text.length() < 30000) text.append('\n');
            } else if (element instanceof XWPFTable table) {
                // Many resumes keep all their content in table cells, including nested tables.
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        if (text.length() >= 30000) return;
                        appendDocxText(cell.getBodyElements(), text);
                    }
                }
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static class LimitedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        private final int limit;
        LimitedWriter(int limit) { this.limit = limit; }
        @Override public void write(char[] c, int off, int len) { if (text.length() < limit) text.append(c, off, Math.min(len, limit - text.length())); }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}

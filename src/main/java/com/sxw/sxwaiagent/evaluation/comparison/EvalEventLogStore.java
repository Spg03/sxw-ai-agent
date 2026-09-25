package com.sxw.sxwaiagent.evaluation.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sxw.sxwaiagent.evaluation.harness.EvalHarnessProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class EvalEventLogStore {
    private static final Logger log = LoggerFactory.getLogger(EvalEventLogStore.class);
    private final MinioClient minio;
    private final ObjectMapper objectMapper;
    private final EvalHarnessProperties properties;

    public EvalEventLogStore(MinioClient minio, ObjectMapper objectMapper, EvalHarnessProperties properties) {
        this.minio = minio;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String store(String comparisonId, String caseId, String target, int repeat, List<JsonNode> events) {
        if (events == null || events.isEmpty()) return null;
        String bucket = properties.dsh().eventLogBucket();
        String key = "comparisons/" + safe(comparisonId) + "/" + safe(caseId) + "/"
            + safe(target) + "-" + repeat + ".json";
        try {
            String json = objectMapper.writeValueAsString(events)
                .replaceAll("sk-[A-Za-z0-9_-]{8,}", "sk-***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]{8,}", "Bearer ***")
                .replaceAll("(?i)(\\\"(?:authorization|api[_-]?key|access[_-]?token|password|cookie)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType("application/json").build());
            return key;
        } catch (Exception e) {
            log.warn("Unable to persist evaluation event log {}/{}: {}", comparisonId, caseId, e.getMessage());
            return null;
        }
    }

    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9_.-]", "_"); }
}

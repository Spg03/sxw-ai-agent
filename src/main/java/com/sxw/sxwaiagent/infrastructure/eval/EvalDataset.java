package com.sxw.sxwaiagent.infrastructure.eval;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code classpath:eval/*.yaml} 加载评测数据集。
 * <p>
 * YAML 顶层是用例数组，每条对应一个 {@link EvalCase}。字段名与 record 完全一致；
 * 缺失字段会以默认值填充（空列表 / 空字符串 / 0）。
 */
public final class EvalDataset {

    private EvalDataset() {}

    public static List<EvalCase> load(String classpathPattern) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(classpathPattern);
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan eval datasets at " + classpathPattern, e);
        }
        Yaml yaml = new Yaml();
        List<EvalCase> all = new ArrayList<>();
        for (Resource res : resources) {
            try (InputStream in = res.getInputStream()) {
                Object parsed = yaml.load(in);
                if (!(parsed instanceof List<?> list)) continue;
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        all.add(toCase(m));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("failed to load " + res.getDescription(), e);
            }
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static EvalCase toCase(Map<?, ?> m) {
        return new EvalCase(
                str(m, "id"),
                str(m, "category"),
                str(m, "input"),
                listOf((List<Object>) m.get("expectKeywordsAny")),
                listOf((List<Object>) m.get("expectKeywordsAll")),
                str(m, "judgeCriteria"),
                m.get("maxLatencyMs") instanceof Number n ? n.longValue() : 0L
        );
    }

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static List<String> listOf(List<Object> raw) {
        if (raw == null) return Collections.emptyList();
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) if (o != null) out.add(o.toString());
        return out;
    }
}

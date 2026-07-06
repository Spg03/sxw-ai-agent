package com.sxw.sxwaiagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 文本分割器
 * 
 * 将 Markdown 文档按语义块分割，保留标题层级关系。
 * 分割策略：
 * 1. 按标题（# ## ###）分割为独立块
 * 2. 每个块包含其标题路径（面包屑）
 * 3. 超长块按段落进一步分割
 */
@Component
public class MarkdownTextSplitter {
    
    private static final Logger log = LoggerFactory.getLogger(MarkdownTextSplitter.class);
    
    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 100;
    
    public List<DocumentChunk> split(String markdown, String docId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        if (markdown == null || markdown.isBlank()) {
            return chunks;
        }
        
        String[] lines = markdown.split("\n");
        List<String> currentHeadingPath = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        int chunkIndex = 0;
        
        for (String line : lines) {
            // 检测标题
            if (line.startsWith("#")) {
                // 保存当前块
                if (!currentContent.toString().isBlank()) {
                    chunks.add(createChunk(docId, chunkIndex++, currentHeadingPath, currentContent.toString()));
                    currentContent = new StringBuilder();
                }
                
                // 更新标题路径
                updateHeadingPath(currentHeadingPath, line);
            } else {
                currentContent.append(line).append("\n");
                
                // 检查块大小
                if (currentContent.length() > MAX_CHUNK_SIZE) {
                    chunks.add(createChunk(docId, chunkIndex++, currentHeadingPath, currentContent.toString()));
                    currentContent = new StringBuilder();
                }
            }
        }
        
        // 保存最后一块
        if (!currentContent.toString().isBlank()) {
            chunks.add(createChunk(docId, chunkIndex++, currentHeadingPath, currentContent.toString()));
        }
        
        log.debug("Split document {} into {} chunks", docId, chunks.size());
        return chunks;
    }
    
    private void updateHeadingPath(List<String> path, String headingLine) {
        int level = 0;
        while (level < headingLine.length() && headingLine.charAt(level) == '#') {
            level++;
        }
        
        String heading = headingLine.substring(level).trim();
        
        // 移除同级或更低级的标题
        while (path.size() >= level) {
            path.remove(path.size() - 1);
        }
        
        // 添加当前标题
        path.add(heading);
    }
    
    private DocumentChunk createChunk(String docId, int index, List<String> headingPath, String content) {
        String breadcrumb = String.join(" > ", headingPath);
        
        return new DocumentChunk(
            docId + "_chunk_" + index,
            docId,
            index,
            breadcrumb,
            content.trim(),
            content.length()
        );
    }
    
    public record DocumentChunk(
        String chunkId,
        String docId,
        int chunkIndex,
        String breadcrumb,
        String content,
        int tokenCount
    ) {}
}

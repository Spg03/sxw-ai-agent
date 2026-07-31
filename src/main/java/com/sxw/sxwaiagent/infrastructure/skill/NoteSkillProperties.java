package com.sxw.sxwaiagent.infrastructure.skill;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 笔记技能配置。
 * 通过 {@code sxw.skill.note.*} 进行配置，支持容器/环境变量覆盖。
 */
@Validated
@ConfigurationProperties(prefix = "sxw.skill.note")
public class NoteSkillProperties {

    /**
     * 笔记根目录（绝对路径）。所有笔记读写均限制在该目录内。
     */
    @NotBlank(message = "baseDir must not be blank")
    private String baseDir;

    /**
     * 单个笔记文件最大字节数，默认 1MB。
     */
    @Min(value = 1024, message = "maxFileSizeBytes must be at least 1024")
    private long maxFileSizeBytes = 1024 * 1024;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}

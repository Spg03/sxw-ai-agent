package com.sxw.sxwaiagent.infrastructure.tools;

import cn.hutool.core.io.FileUtil;
import com.sxw.sxwaiagent.common.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 文件操作工具（受限沙箱）。
 * <p>
 * 所有读写均被限制在 {@link FileConstant#FILE_SAVE_DIR} + "/file" 目录内，防止路径穿越。
 */
public class FileOperationTool {

    private static final Pattern SAFE_NAME =
            Pattern.compile("^[\\w\\-. \\u4e00-\\u9fa5]{1,128}$");

    private static final long MAX_WRITE_BYTES = 5L * 1024 * 1024;

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";
    private final Path baseDir = Path.of(FILE_DIR).toAbsolutePath().normalize();

    @Tool(description = "Read content from a file under the sandboxed file directory")
    public String readFile(@ToolParam(description = "File name (no path separators)") String fileName) {
        try {
            Path target = resolve(fileName);
            return FileUtil.readUtf8String(target.toString());
        } catch (IllegalArgumentException e) {
            return "refused: " + e.getMessage();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file under the sandboxed file directory")
    public String writeFile(@ToolParam(description = "File name (no path separators)") String fileName,
                            @ToolParam(description = "Content to write to the file") String content) {
        try {
            Path target = resolve(fileName);
            String safe = content == null ? "" : content;
            if (safe.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_WRITE_BYTES) {
                return "refused: content exceeds max size " + MAX_WRITE_BYTES + " bytes";
            }
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(safe, target.toString());
            return "File written successfully to: " + target;
        } catch (IllegalArgumentException e) {
            return "refused: " + e.getMessage();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }

    private Path resolve(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("invalid file name: " + fileName);
        }
        Path target = baseDir.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("path traversal blocked: " + fileName);
        }
        return target;
    }
}

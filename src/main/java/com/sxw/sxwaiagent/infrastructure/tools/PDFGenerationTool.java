package com.sxw.sxwaiagent.infrastructure.tools;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * PDF 生成工具
 */
public class PDFGenerationTool {

    private static final long MAX_CONTENT_BYTES = 512L * 1024;
    private static final long MAX_PDF_BYTES = 5L * 1024 * 1024;
    private static final int MAX_OUTPUT_CHARS = 1024;

    private final ToolSandboxSupport sandbox = new ToolSandboxSupport("pdf");

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        try {
            String safeName = fileName == null ? "" : fileName.trim();
            if (safeName.isEmpty() || !safeName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                return "refused: file name must end with .pdf";
            }
            Path target = sandbox.resolveFile(safeName);
            String safeContent = content == null ? "" : content;
            if (ToolSandboxSupport.utf8Length(safeContent) > MAX_CONTENT_BYTES) {
                return "refused: content exceeds max size " + MAX_CONTENT_BYTES + " bytes";
            }
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(target.toString());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 自定义字体（需要人工下载字体文件到特定目录）
//                String fontPath = Paths.get("src/main/resources/static/fonts/simsun.ttf")
//                        .toAbsolutePath().toString();
//                PdfFont font = PdfFontFactory.createFont(fontPath,
//                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                // 使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                // 创建段落
                Paragraph paragraph = new Paragraph(safeContent);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            long fileSize = Files.size(target);
            if (fileSize > MAX_PDF_BYTES) {
                Files.deleteIfExists(target);
                return "refused: generated pdf exceeds max size " + MAX_PDF_BYTES + " bytes";
            }
            String result = "ok: pdf generated to " + target + " (bytes=" + fileSize + ")";
            return ToolSandboxSupport.limitOutput(result, MAX_OUTPUT_CHARS);
        } catch (IllegalArgumentException e) {
            return "refused: " + e.getMessage();
        } catch (IOException e) {
            return "failed: " + e.getMessage();
        }
    }
}

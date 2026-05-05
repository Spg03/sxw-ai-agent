package com.sxw.sxwaiagent.infrastructure.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "pdf-" + UUID.randomUUID() + ".pdf";
        String content = "编程导航原创项目 https://www.codefather.cn";
        String result = tool.generatePDF(fileName, content);
        assertTrue(result.startsWith("ok:"), result);
        Path target = Path.of(System.getProperty("user.dir"), "tmp", "pdf", fileName);
        assertTrue(Files.exists(target), "generated pdf should exist");
    }

    @Test
    void rejectInvalidExtension() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String result = tool.generatePDF("not-pdf.txt", "hello");
        assertTrue(result.startsWith("refused:"), result);
    }
}
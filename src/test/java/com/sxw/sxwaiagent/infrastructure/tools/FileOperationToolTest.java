package com.sxw.sxwaiagent.infrastructure.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileOperationToolTest {

    @Test
    void writeThenReadFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String fileName = "file-op-test.txt";
        String content = "https://www.codefather.cn 程序员编程学习交流社区";
        String writeResult = fileOperationTool.writeFile(fileName, content);
        Assertions.assertTrue(writeResult.contains("successfully"), writeResult);

        String readResult = fileOperationTool.readFile(fileName);
        Assertions.assertEquals(content, readResult);
    }

    @Test
    void rejectPathTraversalName() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String result = fileOperationTool.writeFile("../bad.txt", "x");
        Assertions.assertTrue(result.startsWith("refused:"), result);
    }
}

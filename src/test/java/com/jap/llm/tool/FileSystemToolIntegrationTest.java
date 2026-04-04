package com.jap.llm.tool;

import com.jap.sandbox.exception.SandboxViolationException;
import com.jap.sandbox.filesystem.DefaultPathValidator;
import com.jap.sandbox.filesystem.PathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileSystem Tool Security Integration Tests")
class FileSystemToolIntegrationTest {

    @TempDir
    Path tempDir;

    private CodeFileSystemTools fileSystemTools;
    private PathValidator pathValidator;

    @BeforeEach
    void setUp() {
        pathValidator = DefaultPathValidator.forTest(tempDir);
        fileSystemTools = new DefaultCodeFileSystemTools(pathValidator);
    }

    @Nested
    @DisplayName("Path Traversal Attack Prevention")
    class PathTraversalAttackTests {

        @Test
        @DisplayName("SEC-01: Should block reading '../../../../../../windows/win.ini'")
        void shouldBlockWindowsSystemFileRead() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("../../../../../../windows/win.ini")
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().toLowerCase().contains("traversal"));
        }

        @Test
        @DisplayName("SEC-02: Should block reading '../../../../../../etc/passwd'")
        void shouldBlockUnixPasswdFileRead() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("../../../../../../etc/passwd")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-03: Should block reading '../../../secret/data.txt'")
        void shouldBlockRelativePathEscape() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("../../../secret/data.txt")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-04: Should block writing to escaped path")
        void shouldBlockWriteToEscapedPath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("../../../malicious.exe", "payload", true)
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-05: Should block delete on escaped path")
        void shouldBlockDeleteOnEscapedPath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.deleteFile("../../../important.txt")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-06: Should block list on escaped path")
        void shouldBlockListOnEscapedPath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.listFiles("../../secret")
            );

            assertEquals("E05", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Absolute Path Attack Prevention")
    class AbsolutePathAttackTests {

        @Test
        @DisplayName("SEC-07: Should block writing to 'D:/data.txt'")
        void shouldBlockAbsoluteWindowsPathWrite() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("D:/data.txt", "hacked", true)
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().toLowerCase().contains("traversal")
                    || ex.getMessage().toLowerCase().contains("absolute"));
        }

        @Test
        @DisplayName("SEC-08: Should block writing to 'C:\\Windows\\System32\\hack.dll'")
        void shouldBlockWindowsSystemPathWrite() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("C:\\Windows\\System32\\hack.dll", "malware", true)
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-09: Should block reading '/etc/shadow'")
        void shouldBlockUnixShadowFileRead() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("/etc/shadow")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-10: Should block reading '/proc/self/environ'")
        void shouldBlockProcFilesystemRead() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("/proc/self/environ")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-11: Should block createDirectory on absolute path")
        void shouldBlockCreateDirectoryOnAbsolutePath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.createDirectory("C:\\malware")
            );

            assertEquals("E05", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Symlink Attack Prevention")
    class SymlinkAttackTests {

        @Test
        @DisplayName("SEC-12: Should block symlink pointing outside sandbox")
        void shouldBlockExternalSymlinkRead() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            org.junit.jupiter.api.Assumptions.assumeFalse(isWindows,
                "Symlink test skipped on Windows (requires admin privileges)");

            Path externalDir = tempDir.resolve("..").normalize().resolve("external-symlink-test");
            Files.createDirectories(externalDir);

            Path secretFile = externalDir.resolve("secret.txt");
            Files.writeString(secretFile, "TOP SECRET DATA");

            Path symlink = tempDir.resolve("malicious-link");
            Files.createSymbolicLink(symlink, secretFile);

            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("malicious-link")
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().toLowerCase().contains("symlink")
                    || ex.getMessage().toLowerCase().contains("escapes"));
        }

        @Test
        @DisplayName("SEC-13: Should allow symlink pointing inside sandbox")
        void shouldAllowInternalSymlinkRead() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            org.junit.jupiter.api.Assumptions.assumeFalse(isWindows,
                "Symlink test skipped on Windows (requires admin privileges)");

            Path targetFile = tempDir.resolve("target.java");
            Files.writeString(targetFile, "public class Target {}");

            Path symlink = tempDir.resolve("link.java");
            Files.createSymbolicLink(symlink, targetFile);

            String content = fileSystemTools.readFile("link.java");

            assertTrue(content.contains("public class Target"));
        }

        @Test
        @DisplayName("SEC-14: Should block symlink chain escaping sandbox")
        void shouldBlockSymlinkChainEscape() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            org.junit.jupiter.api.Assumptions.assumeFalse(isWindows,
                "Symlink test skipped on Windows (requires admin privileges)");

            Path externalDir = tempDir.resolve("..").normalize().resolve("external-chain-test");
            Files.createDirectories(externalDir);

            Path secretFile = externalDir.resolve("secret.txt");
            Files.writeString(secretFile, "SECRET");

            Path link1 = tempDir.resolve("link1");
            Files.createSymbolicLink(link1, secretFile);

            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("link1")
            );

            assertEquals("E05", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Valid Operations")
    class ValidOperationTests {

        @Test
        @DisplayName("Should allow reading file inside sandbox")
        void shouldAllowReadInsideSandbox() throws IOException {
            Path testFile = tempDir.resolve("test.java");
            Files.writeString(testFile, "public class Test {}");

            String content = fileSystemTools.readFile("test.java");

            assertTrue(content.contains("public class Test"));
        }

        @Test
        @DisplayName("Should allow writing file inside sandbox")
        void shouldAllowWriteInsideSandbox() {
            String result = fileSystemTools.writeFile("src/main/java/App.java", 
                "public class App {}", true);

            assertTrue(result.contains("successfully"));
            assertTrue(Files.exists(tempDir.resolve("src/main/java/App.java")));
        }

        @Test
        @DisplayName("Should allow listing directory inside sandbox")
        void shouldAllowListInsideSandbox() throws IOException {
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.writeString(tempDir.resolve("src/main/java/App.java"), "code");

            String result = fileSystemTools.listFiles("src/main/java");

            assertTrue(result.contains("App.java"));
        }

        @Test
        @DisplayName("Should allow creating directory inside sandbox")
        void shouldAllowCreateDirectoryInsideSandbox() {
            String result = fileSystemTools.createDirectory("src/main/resources");

            assertTrue(result.contains("successfully"));
            assertTrue(Files.isDirectory(tempDir.resolve("src/main/resources")));
        }

        @Test
        @DisplayName("Should allow deleting file inside sandbox")
        void shouldAllowDeleteInsideSandbox() throws IOException {
            Path testFile = tempDir.resolve("to-delete.java");
            Files.writeString(testFile, "delete me");

            String result = fileSystemTools.deleteFile("to-delete.java");

            assertTrue(result.contains("successfully"));
            assertFalse(Files.exists(testFile));
        }

        @Test
        @DisplayName("Should correctly check existence")
        void shouldCorrectlyCheckExistence() throws IOException {
            Path existingFile = tempDir.resolve("existing.java");
            Files.writeString(existingFile, "exists");

            assertTrue(fileSystemTools.exists("existing.java"));
            assertFalse(fileSystemTools.exists("nonexistent.java"));
        }
    }

    @Nested
    @DisplayName("File Extension Security")
    class FileExtensionTests {

        @Test
        @DisplayName("SEC-15: Should block writing .exe file")
        void shouldBlockExeFileWrite() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("malware.exe", "binary", true)
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().toLowerCase().contains("extension"));
        }

        @Test
        @DisplayName("SEC-16: Should block writing .bat file")
        void shouldBlockBatFileWrite() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("script.bat", "@echo off", true)
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-17: Should block writing .sh file")
        void shouldBlockShFileWrite() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("script.sh", "#!/bin/bash", true)
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("Should allow writing .java file")
        void shouldAllowJavaFileWrite() {
            String result = fileSystemTools.writeFile("App.java", "public class App {}", true);
            assertTrue(result.contains("successfully"));
        }

        @Test
        @DisplayName("Should allow writing .xml file")
        void shouldAllowXmlFileWrite() {
            String result = fileSystemTools.writeFile("pom.xml", "<project></project>", true);
            assertTrue(result.contains("successfully"));
        }

        @Test
        @DisplayName("Should allow writing .yml file")
        void shouldAllowYmlFileWrite() {
            String result = fileSystemTools.writeFile("application.yml", "server:\n  port: 8080", true);
            assertTrue(result.contains("successfully"));
        }
    }

    @Nested
    @DisplayName("Size Limit Security")
    class SizeLimitTests {

        @Test
        @DisplayName("SEC-18: Should reject writing oversized content")
        void shouldRejectOversizedContent() {
            StringBuilder hugeContent = new StringBuilder();
            for (int i = 0; i < 600000; i++) {
                hugeContent.append("x");
            }

            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.writeFile("huge.java", hugeContent.toString(), true)
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().toLowerCase().contains("large")
                    || ex.getMessage().toLowerCase().contains("size"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("SEC-19: Should reject null bytes in path")
        void shouldRejectNullBytesInPath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("src\u0000/App.java")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("SEC-20: Should reject control characters in path")
        void shouldRejectControlCharactersInPath() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> fileSystemTools.readFile("src/\u001f/App.java")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("Should handle non-existent file gracefully")
        void shouldHandleNonExistentFileGracefully() {
            String result = fileSystemTools.readFile("nonexistent.java");
            assertTrue(result.toLowerCase().contains("not found"));
        }

        @Test
        @DisplayName("Should handle reading directory as file")
        void shouldHandleReadingDirectoryAsFile() throws IOException {
            Files.createDirectories(tempDir.resolve("src"));

            String result = fileSystemTools.readFile("src");
            assertTrue(result.toLowerCase().contains("not a regular file"));
        }
    }
}

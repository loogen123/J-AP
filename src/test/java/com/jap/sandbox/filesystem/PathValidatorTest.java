package com.jap.sandbox.filesystem;

import com.jap.sandbox.exception.SandboxViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PathValidator Security Tests")
class PathValidatorTest {

    @TempDir
    Path tempDir;

    private PathValidator validator;

    @BeforeEach
    void setUp() {
        validator = DefaultPathValidator.forTest(tempDir);
    }

    @Nested
    @DisplayName("Path Traversal Attack Prevention")
    class PathTraversalTests {

        @Test
        @DisplayName("E05-01: Should block '../etc/passwd' traversal attempt")
        void shouldBlockParentDirectoryTraversal() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> validator.validateAndResolve("../etc/passwd")
            );

            assertEquals("E05", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("traversal"));
        }

        @Test
        @DisplayName("E05-02: Should block '../../windows/system32' traversal on Windows")
        void shouldBlockWindowsSystemTraversal() {
            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> validator.validateAndResolve("../../windows/system32/config")
            );

            assertEquals("E05", ex.getErrorCode());
        }

        @Test
        @DisplayName("E05-03: Should block multiple '../' sequences")
        void shouldBlockMultipleParentTraversal() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("../../../etc/passwd"));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("../../../.."));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("subdir/../../../etc/passwd"));
        }

        @Test
        @DisplayName("E05-04: Should block absolute path '/etc/passwd'")
        void shouldBlockAbsolutePathLinux() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("/etc/passwd"));
        }

        @Test
        @DisplayName("E05-05: Should block absolute path 'C:\\Windows\\System32'")
        void shouldBlockAbsolutePathWindows() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("C:\\Windows\\System32"));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("D:\\secret\\data.txt"));
        }

        @Test
        @DisplayName("E05-06: Should block mixed traversal patterns")
        void shouldBlockMixedTraversalPatterns() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("..\\..\\windows\\system32"));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("src/../../../etc"));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("./../../etc/passwd"));
        }
    }

    @Nested
    @DisplayName("Symlink Attack Prevention")
    class SymlinkAttackTests {

        @Test
        @DisplayName("E05-07: Should block symlink pointing outside sandbox")
        void shouldBlockExternalSymlink() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            org.junit.jupiter.api.Assumptions.assumeFalse(isWindows, 
                "Symlink test skipped on Windows (requires admin privileges)");

            Path externalDir = tempDir.resolve("..").normalize().resolve("external-sandbox-test-dir");
            Files.createDirectories(externalDir);

            Path symlinkTarget = externalDir.resolve("secret.txt");
            Files.writeString(symlinkTarget, "secret data");

            Path symlink = tempDir.resolve("malicious-link");
            try {
                Files.createSymbolicLink(symlink, symlinkTarget);
            } catch (UnsupportedOperationException e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symlinks not supported");
                return;
            }

            SandboxViolationException ex = assertThrows(
                SandboxViolationException.class,
                () -> validator.validateAndResolve("malicious-link")
            );

            assertTrue(ex.getMessage().toLowerCase().contains("symlink") 
                    || ex.getMessage().toLowerCase().contains("escapes"));
        }

        @Test
        @DisplayName("E05-08: Should allow symlink pointing inside sandbox")
        void shouldAllowInternalSymlink() throws IOException {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            org.junit.jupiter.api.Assumptions.assumeFalse(isWindows, 
                "Symlink test skipped on Windows (requires admin privileges)");

            Path targetFile = tempDir.resolve("target.txt");
            Files.writeString(targetFile, "target content");

            Path symlink = tempDir.resolve("link-to-target");
            try {
                Files.createSymbolicLink(symlink, targetFile);
            } catch (UnsupportedOperationException e) {
                return; // Skip if symlinks not supported
            }

            Path resolved = validator.validateAndResolve("link-to-target");
            assertTrue(resolved.startsWith(tempDir));
        }
    }

    @Nested
    @DisplayName("Valid Path Handling")
    class ValidPathTests {

        @Test
        @DisplayName("Should resolve valid relative path")
        void shouldResolveValidRelativePath() {
            Path resolved = validator.validateAndResolve("src/main/java/App.java");
            
            assertTrue(resolved.startsWith(tempDir));
            assertTrue(resolved.toString().contains("src"));
            assertTrue(resolved.toString().contains("App.java"));
        }

        @Test
        @DisplayName("Should normalize path with '.' segments")
        void shouldNormalizeDotSegments() {
            Path resolved = validator.validateAndResolve("./src/./main/./App.java");
            
            assertTrue(resolved.startsWith(tempDir));
            assertFalse(resolved.toString().contains("./"));
        }

        @Test
        @DisplayName("Should handle nested directory paths")
        void shouldHandleNestedDirectories() {
            Path resolved = validator.validateAndResolve(
                "com/example/project/service/UserService.java"
            );
            
            assertTrue(resolved.startsWith(tempDir));
            assertTrue(resolved.toString().endsWith("UserService.java"));
        }

        @Test
        @DisplayName("Should reject empty filename as invalid path")
        void shouldRejectEmptyFilename() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve(""));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Special Characters")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should reject null path")
        void shouldRejectNullPath() {
            assertThrows(NullPointerException.class,
                () -> validator.validateAndResolve(null));
        }

        @Test
        @DisplayName("Should reject path with null bytes")
        void shouldRejectNullBytes() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("src\u0000/App.java"));
        }

        @Test
        @DisplayName("Should reject path with control characters")
        void shouldRejectControlCharacters() {
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("src/\u001f/App.java"));
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAndResolve("src/\t/App.java"));
        }

        @Test
        @DisplayName("Should handle double slashes as normalized")
        void shouldHandleDoubleSlashes() {
            Path resolved = validator.validateAndResolve("src//main//App.java");
            assertTrue(resolved.startsWith(tempDir));
        }

        @Test
        @DisplayName("Should handle Windows-style backslashes")
        void shouldHandleWindowsBackslashes() {
            Path resolved = validator.validateAndResolve("src\\main\\java\\App.java");
            assertTrue(resolved.startsWith(tempDir));
        }
    }

    @Nested
    @DisplayName("isTraversalAttempt Detection Tests")
    class TraversalDetectionTests {

        @Test
        @DisplayName("Should detect '..' in path")
        void shouldDetectParentReference() {
            assertTrue(validator.isTraversalAttempt("../secret"));
            assertTrue(validator.isTraversalAttempt("src/../etc"));
            assertTrue(validator.isTraversalAttempt("..\\windows"));
        }

        @Test
        @DisplayName("Should detect absolute paths")
        void shouldDetectAbsolutePaths() {
            assertTrue(validator.isTraversalAttempt("/etc/passwd"));
            assertTrue(validator.isTraversalAttempt("C:\\Windows"));
            assertTrue(validator.isTraversalAttempt("D:/data"));
        }

        @Test
        @DisplayName("Should allow valid relative paths")
        void shouldAllowValidRelativePaths() {
            assertFalse(validator.isTraversalAttempt("src/main/java"));
            assertFalse(validator.isTraversalAttempt("com/example/App.java"));
            assertFalse(validator.isTraversalAttempt("./config.yml"));
        }

        @Test
        @DisplayName("Should reject null or blank paths")
        void shouldRejectNullOrBlank() {
            assertTrue(validator.isTraversalAttempt(null));
            assertTrue(validator.isTraversalAttempt(""));
            assertTrue(validator.isTraversalAttempt("   "));
        }
    }

    @Nested
    @DisplayName("validateAbsolute Tests")
    class ValidateAbsoluteTests {

        @Test
        @DisplayName("Should accept path inside sandbox")
        void shouldAcceptPathInsideSandbox() throws IOException {
            Path insidePath = tempDir.resolve("src/App.java");
            Files.createDirectories(insidePath.getParent());
            
            assertDoesNotThrow(() -> validator.validateAbsolute(insidePath));
        }

        @Test
        @DisplayName("Should reject path outside sandbox")
        void shouldRejectPathOutsideSandbox() {
            Path outsidePath = Path.of("/etc/passwd");
            
            assertThrows(SandboxViolationException.class,
                () -> validator.validateAbsolute(outsidePath));
        }

        @Test
        @DisplayName("Should reject null absolute path")
        void shouldRejectNullAbsolutePath() {
            assertThrows(NullPointerException.class,
                () -> validator.validateAbsolute(null));
        }
    }

    @Nested
    @DisplayName("getSandboxRoot Tests")
    class SandboxRootTests {

        @Test
        @DisplayName("Should return normalized absolute sandbox root")
        void shouldReturnNormalizedSandboxRoot() {
            Path root = validator.getSandboxRoot();
            
            assertTrue(root.isAbsolute());
            assertEquals(tempDir.normalize().toAbsolutePath(), root);
        }
    }
}

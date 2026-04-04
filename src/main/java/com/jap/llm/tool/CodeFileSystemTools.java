package com.jap.llm.tool;

import com.jap.sandbox.exception.SandboxViolationException;

public interface CodeFileSystemTools {

    String readFile(String relativePath) throws SandboxViolationException;

    String writeFile(String relativePath, String content, boolean overwrite) throws SandboxViolationException;

    String listFiles(String relativePath) throws SandboxViolationException;

    String deleteFile(String relativePath) throws SandboxViolationException;

    String createDirectory(String relativePath) throws SandboxViolationException;

    boolean exists(String relativePath);
}

package com.jap.sandbox.filesystem;

import com.jap.sandbox.exception.SandboxViolationException;
import java.nio.file.Path;

public interface PathValidator {

    Path validateAndResolve(String relativePath) throws SandboxViolationException;

    void validateAbsolute(Path absolutePath) throws SandboxViolationException;

    boolean isTraversalAttempt(String pathInput);

    Path getSandboxRoot();
}

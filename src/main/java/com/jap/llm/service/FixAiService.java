package com.jap.llm.service;

import com.jap.llm.dto.CodePatch;
import com.jap.llm.dto.CodePatchResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FixAiService {

    @SystemMessage("""
        You are J-AP's Self-Healing Agent, an expert Java debugger specializing in fixing compilation errors.
        
        Your mission is to analyze compilation errors and generate precise code patches to fix them.
        
        ## Error Categories You Handle
        - E02-01: Symbol not found (missing import, undefined variable)
        - E02-02: Package not found (missing dependency)
        - E02-03: Type mismatch (incompatible types)
        - E02-04: Method signature mismatch (wrong parameters)
        - E02-05: Missing return statement
        - E02-06: Unhandled exception (needs try-catch or throws)
        - E02-07: Static context error
        - E02-08: Abstract method not implemented
        - E02-09: Inheritance error
        - E02-10: Duplicate definition
        - E02-11: Variable not initialized
        
        ## Fix Strategies
        
        ### For Symbol Not Found (E02-01):
        - Check if import is missing → Add appropriate import
        - Check if variable is undefined → Declare variable or fix typo
        - Check if method doesn't exist → Fix method name or add method
        
        ### For Package Not Found (E02-02):
        - Check if import path is correct
        - Suggest alternative packages if available
        
        ### For Type Mismatch (E02-03):
        - Add type conversion/casting
        - Fix method return type
        - Change variable type declaration
        
        ### For Method Signature (E02-04):
        - Adjust method parameters
        - Check method overloading
        
        ### For Missing Return (E02-05):
        - Add return statement
        - Ensure all code paths return a value
        
        ### For Unhandled Exception (E02-06):
        - Wrap in try-catch block
        - Add throws declaration
        - Use Optional for nullable values
        
        ## Output Format
        Return a valid JSON object matching CodePatchResult structure:
        {
            "success": true,
            "patches": [
                {
                    "filePath": "src/main/java/com/example/User.java",
                    "originalContent": "...",
                    "patchedContent": "...",
                    "description": "Added missing import for LocalDateTime",
                    "lineStart": 1,
                    "lineEnd": 10
                }
            ],
            "summary": "Fixed 2 errors: missing import and type mismatch",
            "confidence": 0.95
        }
        
        ## Important Rules
        - Return ONLY valid JSON, no markdown code blocks
        - Preserve the original code structure and formatting
        - Only modify what's necessary to fix the error
        - Include ALL imports in patched content if modifying imports
        - Maintain proper indentation
        - If multiple errors in same file, fix all at once
        - If uncertain, set confidence lower and explain why
        - Never add TODO comments or placeholders
        """)
    @UserMessage("""
        Analyze the following compilation errors and generate code patches to fix them.
        
        ## Original Code Files
        {{originalFiles}}
        
        ## Compilation Errors
        {{errors}}
        
        ## Error Context
        {{errorContext}}
        
        ## Previous Fix Attempts
        {{previousAttempts}}
        
        Generate the minimal patches needed to fix all errors. Focus on the root cause.
        """)
    CodePatchResult generatePatches(
        @V("originalFiles") String originalFiles,
        @V("errors") String errors,
        @V("errorContext") String errorContext,
        @V("previousAttempts") String previousAttempts
    );

    @SystemMessage("""
        You are J-AP's Import Fixer, specialized in resolving missing import errors.
        
        Analyze the missing symbol and determine the correct import statement.
        
        ## Common Java Imports
        - java.time.* - Date/time types (LocalDate, LocalDateTime, Instant)
        - java.util.* - Collections (List, Map, Set, Optional)
        - java.math.* - BigDecimal, BigInteger
        - jakarta.persistence.* - JPA annotations
        - jakarta.validation.* - Bean validation
        - org.springframework.* - Spring Framework
        - lombok.* - Lombok annotations
        - org.slf4j.* - Logging
        
        ## Output Format
        Return the complete corrected file content with proper imports added.
        """)
    @UserMessage("""
        Fix the missing import in this file.
        
        ## File Path
        {{filePath}}
        
        ## Current Content
        {{currentContent}}
        
        ## Missing Symbol
        {{missingSymbol}}
        
        ## Symbol Type
        {{symbolType}}
        
        Return the complete file with the correct import added.
        """)
    CodePatchResult fixMissingImport(
        @V("filePath") String filePath,
        @V("currentContent") String currentContent,
        @V("missingSymbol") String missingSymbol,
        @V("symbolType") String symbolType
    );
}

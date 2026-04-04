package com.jap.healing;

import com.jap.sandbox.process.BuildResult;
import com.jap.sandbox.process.CompileError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ErrorClassifier {

    private static final Logger log = LoggerFactory.getLogger(ErrorClassifier.class);

    private static final Pattern SYMBOL_NOT_FOUND_PATTERN = Pattern.compile(
        "cannot find symbol\\s*\\n\\s*symbol:\\s*(\\w+)\\s+(\\w+)\\s*\\n\\s*location:\\s+(.+)",
        Pattern.MULTILINE
    );
    
    private static final Pattern PACKAGE_NOT_EXIST_PATTERN = Pattern.compile(
        "package\\s+(.+)\\s+does not exist"
    );
    
    private static final Pattern INCOMPATIBLE_TYPES_PATTERN = Pattern.compile(
        "incompatible types:\\s*(.+?)\\s+cannot be converted to\\s+(.+)"
    );
    
    private static final Pattern METHOD_CANNOT_BE_APPLIED_PATTERN = Pattern.compile(
        "method\\s+(\\w+)\\s+in class\\s+(.+?)\\s+cannot be applied to given types"
    );
    
    private static final Pattern MISSING_RETURN_PATTERN = Pattern.compile(
        "missing return statement"
    );
    
    private static final Pattern UNREPORTED_EXCEPTION_PATTERN = Pattern.compile(
        "unreported exception\\s+(.+?);\\s+must be caught or declared to be thrown"
    );

    private static final Pattern FILE_LINE_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+(.+?)\\[(\\d+),(\\d+)\\]"
    );

    public ClassifiedErrors classify(BuildResult buildResult) {
        if (buildResult.success()) {
            return ClassifiedErrors.empty();
        }

        List<ClassifiedError> errors = new ArrayList<>();
        String output = buildResult.output() + "\n" + buildResult.errorOutput();

        for (CompileError compileError : buildResult.errors()) {
            ClassifiedError classified = classifySingleError(compileError, output);
            errors.add(classified);
        }

        errors.addAll(extractAdditionalErrors(output));

        log.info("Classified {} errors from build output", errors.size());
        return new ClassifiedErrors(errors, buildResult.duration());
    }

    private ClassifiedError classifySingleError(CompileError compileError, String fullOutput) {
        String message = compileError.message();
        ErrorCategory category = determineCategory(message);
        ErrorSeverity severity = determineSeverity(message, category);
        
        String context = extractContext(fullOutput, compileError.filePath(), compileError.lineNumber());
        
        return new ClassifiedError(
            compileError.filePath(),
            compileError.lineNumber(),
            compileError.columnNumber(),
            category,
            severity,
            compileError.errorCode(),
            message,
            context
        );
    }

    private ErrorCategory determineCategory(String message) {
        if (message == null || message.isEmpty()) {
            return ErrorCategory.UNKNOWN;
        }

        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("cannot find symbol")) {
            return ErrorCategory.E02_SYMBOL_NOT_FOUND;
        }
        if (lowerMessage.contains("package") && lowerMessage.contains("does not exist")) {
            return ErrorCategory.E02_PACKAGE_NOT_FOUND;
        }
        if (lowerMessage.contains("incompatible types")) {
            return ErrorCategory.E02_TYPE_MISMATCH;
        }
        if (lowerMessage.contains("cannot be applied to")) {
            return ErrorCategory.E02_METHOD_SIGNATURE;
        }
        if (lowerMessage.contains("missing return")) {
            return ErrorCategory.E02_MISSING_RETURN;
        }
        if (lowerMessage.contains("must be caught") || lowerMessage.contains("unreported exception")) {
            return ErrorCategory.E02_UNHANDLED_EXCEPTION;
        }
        if (lowerMessage.contains("cannot be referenced from a static context")) {
            return ErrorCategory.E02_STATIC_CONTEXT;
        }
        if (lowerMessage.contains("is not abstract and does not override abstract method")) {
            return ErrorCategory.E02_ABSTRACT_METHOD;
        }
        if (lowerMessage.contains("cannot be inherited")) {
            return ErrorCategory.E02_INHERITANCE;
        }
        if (lowerMessage.contains("already defined")) {
            return ErrorCategory.E02_DUPLICATE_DEFINITION;
        }
        if (lowerMessage.contains("might not have been initialized")) {
            return ErrorCategory.E02_UNINITIALIZED;
        }

        return ErrorCategory.E02_COMPILATION_ERROR;
    }

    private ErrorSeverity determineSeverity(String message, ErrorCategory category) {
        if (category == ErrorCategory.E02_SYMBOL_NOT_FOUND || 
            category == ErrorCategory.E02_PACKAGE_NOT_FOUND) {
            return ErrorSeverity.HIGH;
        }
        if (category == ErrorCategory.E02_TYPE_MISMATCH ||
            category == ErrorCategory.E02_METHOD_SIGNATURE) {
            return ErrorSeverity.HIGH;
        }
        return ErrorSeverity.MEDIUM;
    }

    private String extractContext(String output, String filePath, int lineNumber) {
        if (filePath == null || filePath.equals("unknown")) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.contains(filePath) || 
                (lineNumber > 0 && line.contains("[" + lineNumber + ","))) {
                context.append(line).append("\n");
                if (context.length() > 500) {
                    break;
                }
            }
        }

        return context.toString().trim();
    }

    private List<ClassifiedError> extractAdditionalErrors(String output) {
        List<ClassifiedError> errors = new ArrayList<>();

        Matcher symbolMatcher = SYMBOL_NOT_FOUND_PATTERN.matcher(output);
        while (symbolMatcher.find()) {
            String symbolType = symbolMatcher.group(1);
            String symbolName = symbolMatcher.group(2);
            String location = symbolMatcher.group(3);
            
            errors.add(new ClassifiedError(
                extractFilePath(location),
                0, 0,
                ErrorCategory.E02_SYMBOL_NOT_FOUND,
                ErrorSeverity.HIGH,
                "SYMBOL_NOT_FOUND",
                String.format("Cannot find %s '%s' in %s", symbolType, symbolName, location),
                location
            ));
        }

        Matcher packageMatcher = PACKAGE_NOT_EXIST_PATTERN.matcher(output);
        while (packageMatcher.find()) {
            String packageName = packageMatcher.group(1);
            errors.add(new ClassifiedError(
                "unknown", 0, 0,
                ErrorCategory.E02_PACKAGE_NOT_FOUND,
                ErrorSeverity.HIGH,
                "PACKAGE_NOT_FOUND",
                "Package '" + packageName + "' does not exist",
                packageName
            ));
        }

        Matcher typeMatcher = INCOMPATIBLE_TYPES_PATTERN.matcher(output);
        while (typeMatcher.find()) {
            String fromType = typeMatcher.group(1);
            String toType = typeMatcher.group(2);
            errors.add(new ClassifiedError(
                "unknown", 0, 0,
                ErrorCategory.E02_TYPE_MISMATCH,
                ErrorSeverity.HIGH,
                "TYPE_MISMATCH",
                String.format("Incompatible types: %s cannot be converted to %s", fromType, toType),
                fromType + " -> " + toType
            ));
        }

        return errors;
    }

    private String extractFilePath(String location) {
        if (location == null) return "unknown";
        
        int classIndex = location.indexOf("class ");
        if (classIndex >= 0) {
            return location.substring(classIndex + 6).trim();
        }
        return location;
    }

    public record ClassifiedErrors(
        List<ClassifiedError> errors,
        java.time.Duration buildDuration
    ) {
        public static ClassifiedErrors empty() {
            return new ClassifiedErrors(List.of(), java.time.Duration.ZERO);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getErrorCount() {
            return errors.size();
        }

        public String getFormattedSummary() {
            if (errors.isEmpty()) {
                return "No errors";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(errors.size()).append(" error(s):\n");
            
            for (int i = 0; i < errors.size(); i++) {
                ClassifiedError error = errors.get(i);
                sb.append(String.format("[%d] %s:%d - %s: %s\n",
                    i + 1,
                    error.filePath(),
                    error.lineNumber(),
                    error.category().getCode(),
                    error.message()
                ));
            }
            
            return sb.toString();
        }
    }

    public record ClassifiedError(
        String filePath,
        int lineNumber,
        int columnNumber,
        ErrorCategory category,
        ErrorSeverity severity,
        String errorCode,
        String message,
        String context
    ) {
        public String toPromptContext() {
            return String.format("""
                Error in file: %s
                Location: line %d, column %d
                Category: %s (%s)
                Code: %s
                Message: %s
                Context: %s
                """,
                filePath, lineNumber, columnNumber,
                category.getCode(), category.getDescription(),
                errorCode, message,
                context != null ? context : "N/A"
            );
        }
    }

    public enum ErrorCategory {
        E02_SYMBOL_NOT_FOUND("E02-01", "Symbol not found - missing import or undefined variable"),
        E02_PACKAGE_NOT_FOUND("E02-02", "Package not found - missing dependency or import"),
        E02_TYPE_MISMATCH("E02-03", "Type mismatch - incompatible types"),
        E02_METHOD_SIGNATURE("E02-04", "Method signature mismatch - wrong parameters"),
        E02_MISSING_RETURN("E02-05", "Missing return statement"),
        E02_UNHANDLED_EXCEPTION("E02-06", "Unhandled exception - needs try-catch or throws"),
        E02_STATIC_CONTEXT("E02-07", "Static context error - non-static reference"),
        E02_ABSTRACT_METHOD("E02-08", "Abstract method not implemented"),
        E02_INHERITANCE("E02-09", "Inheritance error"),
        E02_DUPLICATE_DEFINITION("E02-10", "Duplicate definition"),
        E02_UNINITIALIZED("E02-11", "Variable might not be initialized"),
        E02_COMPILATION_ERROR("E02-00", "General compilation error"),
        UNKNOWN("E02-??", "Unknown error type");

        private final String code;
        private final String description;

        ErrorCategory(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ErrorSeverity {
        HIGH,
        MEDIUM,
        LOW
    }
}

package com.jap.llm.service;

import com.jap.llm.dto.CodeGenerationResult;
import com.jap.llm.dto.RequirementSpec;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CodeGeneratorAiService {

    @SystemMessage("""
        You are J-AP's Code Generator Agent, an expert Java developer specializing in Spring Boot 3.x applications.
        
        Your mission is to generate production-quality Java code based on the RequirementSpec provided.
        
        ## Code Quality Standards
        - Use Spring Boot 3.4.x conventions
        - Use Jakarta EE 11 (jakarta.* packages)
        - Use Lombok annotations for DTOs (@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor)
        - Include proper validation annotations (@NotNull, @NotBlank, @Email, @Size, etc.)
        - Use constructor injection with @RequiredArgsConstructor
        - Follow Java naming conventions (camelCase for methods/fields, PascalCase for classes)
        - Add meaningful Javadoc comments for public methods
        - Use Optional for nullable return types
        - Include proper exception handling
        
        ## Entity Generation Rules
        - Use @Entity, @Table, @Id, @GeneratedValue, @Column annotations
        - Use @CreationTimestamp and @UpdateTimestamp for audit fields
        - Implement equals/hashCode using business key (avoid using id)
        - Use LocalDateTime for timestamps
        - Use BigDecimal for monetary values
        
        ## Repository Generation Rules
        - Extend JpaRepository<Entity, ID>
        - Use @Repository annotation
        - Add custom query methods with @Query when needed
        - Use Optional for single result queries
        
        ## Service Generation Rules
        - Use @Service and @Transactional annotations
        - Use @RequiredArgsConstructor for constructor injection
        - Throw meaningful exceptions (EntityNotFoundException, etc.)
        - Return DTOs instead of entities for API layer
        
        ## Controller Generation Rules
        - Use @RestController and @RequestMapping
        - Use @Valid for request body validation
        - Return ResponseEntity with proper status codes
        - Use @Operation for OpenAPI documentation
        - Implement proper error handling with @ExceptionHandler
        
        ## Output Format
        Return a valid JSON object matching CodeGenerationResult structure:
        {
            "files": [
                {
                    "path": "src/main/java/com/example/user/User.java",
                    "content": "package com.example.user;\\n\\nimport ...",
                    "language": "JAVA",
                    "description": "User entity with email validation"
                }
            ],
            "summary": "Generated 5 files for user registration module",
            "notes": ["Consider adding index on email column for performance"]
        }
        
        ## Important Rules
        - Return ONLY valid JSON, no markdown code blocks
        - Each file must have complete, compilable content
        - Package structure must match the path
        - All imports must be included
        - No placeholder comments like "// TODO"
        - Generate working, production-ready code
        """)
    @UserMessage("""
        Generate code for the following module from the requirement specification.
        
        ## Requirement Specification
        {{requirementSpec}}
        
        ## Module to Generate
        Module Name: {{moduleName}}
        Module Type: {{moduleType}}
        Module Description: {{moduleDescription}}
        
        ## Target Package
        {{packageName}}
        
        ## Previously Generated Files (for context)
        {{previousFiles}}
        
        Generate the complete, production-ready code for this module.
        """)
    CodeGenerationResult generateModule(
        @V("requirementSpec") String requirementSpec,
        @V("moduleName") String moduleName,
        @V("moduleType") String moduleType,
        @V("moduleDescription") String moduleDescription,
        @V("packageName") String packageName,
        @V("previousFiles") String previousFiles
    );

    @SystemMessage("""
        You are J-AP's Project Structure Generator.
        
        Generate the standard Maven project structure for a Spring Boot application.
        
        ## Required Directories
        - src/main/java/{packagePath} - Java source files
        - src/main/resources - Configuration files
        - src/test/java/{packagePath} - Test files
        
        ## Required Configuration Files
        - src/main/resources/application.yml - Spring Boot configuration
        - pom.xml - Maven build configuration
        
        ## Output Format
        Return a valid JSON object with the directory structure and configuration files.
        """)
    @UserMessage("""
        Generate the project structure for:
        
        Package: {{packageName}}
        Database Type: {{databaseType}}
        Project Name: {{projectName}}
        
        Create the directory structure and configuration files.
        """)
    CodeGenerationResult generateProjectStructure(
        @V("packageName") String packageName,
        @V("databaseType") String databaseType,
        @V("projectName") String projectName
    );
}

package com.jap.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record RequirementSpec(
    @JsonProperty(required = true)
    @JsonPropertyDescription("Brief summary of the requirement in one sentence")
    String summary,

    @JsonProperty(required = true)
    @JsonPropertyDescription("List of modules to be generated")
    List<ModuleSpec> modules,

    @JsonProperty(required = true)
    @JsonPropertyDescription("Target package name for generated code")
    String packageName,

    @JsonPropertyDescription("Database type to use (MYSQL, H2, POSTGRESQL)")
    String databaseType,

    @JsonPropertyDescription("Optional API endpoint summary list; can be empty")
    List<ApiEndpoint> apiEndpoints,

    @JsonPropertyDescription("Additional technical requirements or constraints")
    List<String> technicalRequirements
) {
    public record ModuleSpec(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Module name (e.g., User, Order, Product)")
        String name,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Module type: ENTITY, REPOSITORY, SERVICE, CONTROLLER, DTO, CONFIG")
        String type,

        @JsonPropertyDescription("Brief description of the module's responsibility")
        String description,

        @JsonPropertyDescription("List of fields for entity modules")
        List<FieldSpec> fields,

        @JsonPropertyDescription("List of methods for service/controller modules")
        List<MethodSpec> methods
    ) {}

    public record FieldSpec(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Field name in camelCase")
        String name,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Field type (String, Integer, Long, Boolean, LocalDateTime, etc.)")
        String type,

        @JsonPropertyDescription("Whether this field is required")
        boolean required,

        @JsonPropertyDescription("JPA annotations to apply (e.g., @Id, @Column, @ManyToOne)")
        List<String> annotations
    ) {}

    public record MethodSpec(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Method name")
        String name,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Return type")
        String returnType,

        @JsonPropertyDescription("Method parameters")
        List<ParameterSpec> parameters,

        @JsonPropertyDescription("HTTP method for controller endpoints (GET, POST, PUT, DELETE)")
        String httpMethod,

        @JsonPropertyDescription("URL path for controller endpoints")
        String path
    ) {}

    public record ParameterSpec(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Parameter name")
        String name,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Parameter type")
        String type,

        @JsonPropertyDescription("Parameter source: PATH, QUERY, BODY, HEADER")
        String source
    ) {}

    public record ApiEndpoint(
        @JsonProperty(required = true)
        @JsonPropertyDescription("HTTP method (GET, POST, PUT, DELETE, PATCH)")
        String method,

        @JsonProperty(required = true)
        @JsonPropertyDescription("URL path (e.g., /api/users/{id})")
        String path,

        @JsonPropertyDescription("Brief endpoint purpose")
        String description,

        @JsonPropertyDescription("Request body DTO name if applicable")
        String requestBody,

        @JsonPropertyDescription("Response DTO name")
        String responseBody
    ) {}

    public static RequirementSpec empty() {
        return new RequirementSpec(
            "Empty requirement",
            List.of(),
            "com.example.generated",
            "MYSQL",
            List.of(),
            List.of()
        );
    }
}

package org.doc.util.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.doc.util.model.ConversionResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PostmanToSwaggerService {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Set<String> SKIP_HEADERS =
            Set.of("content-type", "accept", "authorization", "content-length");

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public PostmanToSwaggerService() {
        this.jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.yamlMapper = new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public ConversionResult convert(String postmanJson) throws Exception {
        JsonNode root = jsonMapper.readTree(postmanJson);

        String collectionName = root.path("info").path("name").asText("API");

        // Extract baseUrl from collection variables if available
        String baseUrl = extractBaseUrl(root);

        // Build OpenAPI spec
        Map<String, Object> openApi = new LinkedHashMap<>();
        openApi.put("openapi", "3.0.3");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", collectionName);
        info.put("description", root.path("info").path("description").asText(null));
        info.put("version", "1.0.0");
        openApi.put("info", info);

        if (baseUrl != null) {
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("url", baseUrl);
            openApi.put("servers", List.of(server));
        }

        // Collect all requests recursively (preserving folder as tags)
        List<RequestEntry> requests = new ArrayList<>();
        collectRequests(root.path("item"), null, requests);

        // Build paths
        Map<String, Object> paths = new LinkedHashMap<>();
        for (RequestEntry entry : requests) {
            processRequest(entry, paths);
        }
        openApi.put("paths", paths.isEmpty() ? Map.of("/", Map.of()) : paths);

        // Serialize
        String swaggerYaml = yamlMapper.writeValueAsString(openApi);
        String prettyPostman = jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonMapper.readTree(postmanJson));

        int postmanTokens = estimateTokens(prettyPostman);
        int swaggerTokens = estimateTokens(swaggerYaml);

        return ConversionResult.of(collectionName, prettyPostman, swaggerYaml, postmanTokens, swaggerTokens);
    }

    // ── Recursive request collector ──────────────────────────────────────────

    private void collectRequests(JsonNode items, String tag, List<RequestEntry> out) {
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            if (item.has("request")) {
                out.add(new RequestEntry(item, tag));
            } else if (item.has("item")) {
                // Folder — use folder name as tag
                String folderTag = item.path("name").asText(tag);
                collectRequests(item.path("item"), folderTag, out);
            }
        }
    }

    // ── Request → OpenAPI operation ──────────────────────────────────────────

    private void processRequest(RequestEntry entry, Map<String, Object> paths) {
        JsonNode item = entry.item();
        JsonNode request = item.path("request");

        String name = item.path("name").asText("Unknown");
        String method = request.path("method").asText("GET").toLowerCase();

        String rawUrl = extractRawUrl(request.path("url"));
        String path = normalizePath(rawUrl);

        @SuppressWarnings("unchecked")
        Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(path, k -> new LinkedHashMap<>());

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", name);
        if (entry.tag() != null) {
            operation.put("tags", List.of(entry.tag()));
        }
        operation.put("operationId", toOperationId(method, path, name));

        String desc = request.path("description").asText(null);
        if (desc != null && !desc.isBlank()) {
            operation.put("description", desc);
        }

        // Parameters
        List<Map<String, Object>> parameters = new ArrayList<>();
        addPathParams(path, parameters);
        addQueryParams(request.path("url"), parameters);
        addHeaderParams(request, parameters);
        if (!parameters.isEmpty()) operation.put("parameters", parameters);

        // Request body
        Map<String, Object> requestBody = buildRequestBody(request);
        if (requestBody != null) operation.put("requestBody", requestBody);

        // Responses
        operation.put("responses", buildResponses(item));

        pathItem.put(method, operation);
    }

    // ── Parameter builders ────────────────────────────────────────────────────

    private void addPathParams(String path, List<Map<String, Object>> out) {
        Matcher m = PATH_PARAM_PATTERN.matcher(path);
        while (m.find()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", m.group(1));
            p.put("in", "path");
            p.put("required", true);
            p.put("schema", Map.of("type", "string"));
            out.add(p);
        }
    }

    private void addQueryParams(JsonNode url, List<Map<String, Object>> out) {
        if (!url.has("query")) return;
        for (JsonNode q : url.path("query")) {
            if (q.path("disabled").asBoolean(false)) continue;
            String key = q.path("key").asText("");
            if (key.isBlank()) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", key);
            p.put("in", "query");
            p.put("required", false);
            String d = q.path("description").asText(null);
            if (d != null && !d.isBlank()) p.put("description", d);
            p.put("schema", Map.of("type", "string"));
            out.add(p);
        }
    }

    private void addHeaderParams(JsonNode request, List<Map<String, Object>> out) {
        if (!request.has("header")) return;
        for (JsonNode h : request.path("header")) {
            if (h.path("disabled").asBoolean(false)) continue;
            String key = h.path("key").asText("");
            if (key.isBlank() || SKIP_HEADERS.contains(key.toLowerCase())) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", key);
            p.put("in", "header");
            p.put("required", false);
            p.put("schema", Map.of("type", "string"));
            out.add(p);
        }
    }

    // ── Request body ──────────────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(JsonNode request) {
        JsonNode body = request.path("body");
        if (body.isMissingNode() || body.isEmpty()) return null;

        String mode = body.path("mode").asText("");
        Map<String, Object> content = new LinkedHashMap<>();

        switch (mode) {
            case "raw" -> {
                String raw = body.path("raw").asText("");
                String lang = body.path("options").path("raw").path("language").asText("json");
                String ct = detectContentType(request, raw, lang);

                Map<String, Object> mediaType = new LinkedHashMap<>();
                if (ct.contains("json")) {
                    Map<String, Object> schema = buildSchemaFromJson(raw);
                    if (schema != null) mediaType.put("schema", schema);
                }
                content.put(ct, mediaType);
            }
            case "formdata" -> {
                Map<String, Object> mediaType = new LinkedHashMap<>();
                mediaType.put("schema", buildFormSchema(body.path("formdata")));
                content.put("multipart/form-data", mediaType);
            }
            case "urlencoded" -> {
                Map<String, Object> mediaType = new LinkedHashMap<>();
                mediaType.put("schema", buildFormSchema(body.path("urlencoded")));
                content.put("application/x-www-form-urlencoded", mediaType);
            }
            case "graphql" -> {
                Map<String, Object> mediaType = new LinkedHashMap<>();
                mediaType.put("schema", Map.of("type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string"),
                                "variables", Map.of("type", "object"))));
                content.put("application/json", mediaType);
            }
            default -> {
                return null;
            }
        }

        Map<String, Object> rb = new LinkedHashMap<>();
        rb.put("required", true);
        rb.put("content", content);
        return rb;
    }

    private Map<String, Object> buildFormSchema(JsonNode fields) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        if (fields.isArray()) {
            for (JsonNode f : fields) {
                if (f.path("disabled").asBoolean(false)) continue;
                String key = f.path("key").asText("");
                if (key.isBlank()) continue;
                String fType = "file".equals(f.path("type").asText("text")) ? "string" : "string";
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", fType);
                if ("file".equals(f.path("type").asText())) prop.put("format", "binary");
                properties.put(key, prop);
            }
        }
        if (!properties.isEmpty()) schema.put("properties", properties);
        return schema;
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildResponses(JsonNode item) {
        Map<String, Object> responses = new LinkedHashMap<>();
        JsonNode examples = item.path("response");

        if (examples.isArray() && !examples.isEmpty()) {
            for (JsonNode resp : examples) {
                String code = String.valueOf(resp.path("code").asInt(200));
                String name = resp.path("name").asText("Response");
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("description", name);

                // Try to add response body schema from example
                String body = resp.path("body").asText("");
                if (!body.isBlank() && body.trim().startsWith("{")) {
                    Map<String, Object> schema = buildSchemaFromJson(body);
                    if (schema != null) {
                        Map<String, Object> content = new LinkedHashMap<>();
                        content.put("application/json", Map.of("schema", schema));
                        response.put("content", content);
                    }
                }
                responses.put(code, response);
            }
        } else {
            responses.put("200", Map.of("description", "Successful response"));
        }
        return responses;
    }

    // ── Schema inference ──────────────────────────────────────────────────────

    private Map<String, Object> buildSchemaFromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = jsonMapper.readTree(json);
            return inferSchema(node);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> inferSchema(JsonNode node) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (node.isObject()) {
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> props.put(e.getKey(), inferSchema(e.getValue())));
            if (!props.isEmpty()) schema.put("properties", props);
        } else if (node.isArray()) {
            schema.put("type", "array");
            schema.put("items", node.isEmpty() ? Map.of("type", "object") : inferSchema(node.get(0)));
        } else if (node.isBoolean()) {
            schema.put("type", "boolean");
        } else if (node.isIntegralNumber()) {
            schema.put("type", "integer");
        } else if (node.isFloatingPointNumber()) {
            schema.put("type", "number");
        } else {
            schema.put("type", "string");
        }
        return schema;
    }

    // ── URL & path helpers ────────────────────────────────────────────────────

    private String extractRawUrl(JsonNode url) {
        if (url.isTextual()) return url.asText();
        return url.path("raw").asText("/unknown");
    }

    private String normalizePath(String rawUrl) {
        String path = rawUrl;
        // Remove {{baseUrl}} or similar Postman variables at the start
        path = path.replaceAll("^\\{\\{[^}]+\\}\\}", "");
        // Remove protocol://host:port
        path = path.replaceAll("^https?://[^/]*", "");
        // Remove query string and fragment
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int f = path.indexOf('#');
        if (f >= 0) path = path.substring(0, f);
        // Convert :param path segments to {param}
        path = path.replaceAll("/:([a-zA-Z_][a-zA-Z0-9_]*)", "/{$1}");
        // Inline {{variable}} → {variable}
        path = path.replaceAll("\\{\\{([^}]+)\\}\\}", "{$1}");
        // Ensure leading slash
        if (!path.startsWith("/")) path = "/" + path;
        // Collapse multiple slashes
        path = path.replaceAll("/+", "/");
        return path.isBlank() ? "/" : path;
    }

    private String extractBaseUrl(JsonNode root) {
        JsonNode variables = root.path("variable");
        if (variables.isArray()) {
            for (JsonNode v : variables) {
                String key = v.path("key").asText("");
                if ("baseUrl".equalsIgnoreCase(key) || "base_url".equalsIgnoreCase(key)) {
                    String val = v.path("value").asText("");
                    if (!val.isBlank()) return val;
                }
            }
        }
        return null;
    }

    private String detectContentType(JsonNode request, String body, String lang) {
        // Check explicit Content-Type header first
        if (request.has("header")) {
            for (JsonNode h : request.path("header")) {
                if ("content-type".equalsIgnoreCase(h.path("key").asText())) {
                    return h.path("value").asText("application/json").split(";")[0].trim();
                }
            }
        }
        return switch (lang.toLowerCase()) {
            case "xml" -> "application/xml";
            case "html" -> "text/html";
            case "text" -> "text/plain";
            default -> "application/json";
        };
    }

    private String toOperationId(String method, String path, String name) {
        String clean = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", "_");
        return clean.isBlank() ? method + path.replace("/", "_").replaceAll("[^a-z0-9_]", "") : clean;
    }

    // ── Token estimation ──────────────────────────────────────────────────────

    /**
     * Approximates Claude/GPT BPE token count.
     * Rule of thumb: ~4 characters per token for mixed text/code content.
     * Structured formats (JSON/YAML) skew slightly higher due to punctuation tokens,
     * so we use 3.5 chars/token for a more realistic estimate.
     */
    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 3.5));
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private record RequestEntry(JsonNode item, String tag) {}
}

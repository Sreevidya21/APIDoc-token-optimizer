# APIDoc Token Optimizer

A Spring Boot web application that converts **Postman Collection JSON** to **OpenAPI 3.0 YAML** and estimates the token count difference between the two formats — useful for optimising LLM context usage when providing API documentation to AI assistants like Claude Code.

## Why?

When you feed API documentation to an LLM, the format matters. Postman collections may carry a lot of metadata (test scripts, auth details, example responses, folder structures) that inflates the token count significantly. Converting to a clean OpenAPI 3.0 YAML typically reduces tokens by 30–50%, letting you stay within context limits and spend your token budget on what matters.

## Features

- Convert Postman Collection **v2.0 / v2.1** JSON to **OpenAPI 3.0 YAML**
- Supports input via **paste** or **file upload**
- Preserves:
  - Folder hierarchy → OpenAPI **tags**
  - Path parameters (`:param` and `{{variable}}` styles)
  - Query parameters (enabled only)
  - Custom request headers (standard headers like `Authorization`, `Content-Type` are skipped)
  - Request bodies: `raw` (JSON/XML/text), `formdata`, `urlencoded`, `graphql`
  - Response examples → response schemas (inferred from JSON body)
  - Collection-level `baseUrl` variable → `servers` block
- **Token estimation** for both formats (approximated at ~3.5 chars/token for structured JSON/YAML)
- Side-by-side diff view with copy buttons
- Visual savings bar showing relative token reduction

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Templating | Thymeleaf |
| JSON/YAML | Jackson + `jackson-dataformat-yaml` |
| Utilities | Lombok |
| Frontend | Bootstrap 5.3 + Bootstrap Icons |
| Build | Maven (Maven Wrapper included) |

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.x (or use the included `./mvnw` wrapper)

### Run locally

```bash
./mvnw spring-boot:run
```

Then open [http://localhost:10001](http://localhost:10001) in your browser.

### Build a JAR

```bash
./mvnw clean package
java -jar target/util-0.0.1-SNAPSHOT.jar
```

## Usage

1. Open the app in your browser.
2. Paste your Postman collection JSON into the text area, **or** switch to the _Upload File_ tab and select a `.json` file.
3. Click **Convert**.
4. The results page shows:
   - Token counts for both formats with character counts
   - Token difference and percentage savings/overhead
   - The formatted Postman JSON and generated OpenAPI YAML side-by-side
5. Use the **Copy** button to grab the OpenAPI YAML and paste it directly into your AI assistant's context.

## Project Structure

```
src/main/java/org/doc/util/
├── UtilApplication.java                  # Spring Boot entry point
├── controller/
│   └── ApiConverterController.java       # GET / and POST /convert endpoints
├── model/
│   └── ConversionResult.java             # Result DTO with pre-computed display fields
└── service/
    └── PostmanToSwaggerService.java       # Core conversion + token estimation logic

src/main/resources/
├── application.properties                # Server port (10001)
└── templates/
    └── index.html                        # Thymeleaf UI
```

## Token Estimation

Token counts are approximated using a **3.5 characters per token** heuristic, which is a realistic estimate for structured JSON/YAML content with Claude's BPE tokenizer. Actual token counts may vary by ±15% depending on the tokenizer and content.

For precise counts, pass the output through the tokenizer of your specific model.

## Limitations

- Schema inference from example JSON bodies is shallow — it detects types (`string`, `integer`, `number`, `boolean`, `object`, `array`) but does not merge schemas across multiple examples.
- `$ref` components are not generated; all schemas are inlined.
- Only the first array item is used to infer array item schema.
- Token estimation is an approximation, not a direct tokenizer call.
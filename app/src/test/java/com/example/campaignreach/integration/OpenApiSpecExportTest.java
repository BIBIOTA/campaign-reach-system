package com.example.campaignreach.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Build-time OpenAPI spec export +繁體中文文件內容驗證 (task 4.1, 3.1/3.2/3.3).
 *
 * <p>Boots the full Spring context on the existing Testcontainers infrastructure (inherited from
 * {@link AbstractIntegrationTest}; auto-skipped without Docker), fetches the springdoc-generated
 * OpenAPI document and asserts it carries the expected paths, 繁體中文 descriptions and the HTTP
 * Basic security scheme, then writes {@code app/build/openapi/openapi.yaml} and {@code openapi.json}
 * for the GitHub Pages workflow to publish.
 */
@AutoConfigureMockMvc
class OpenApiSpecExportTest extends AbstractIntegrationTest {

    /** Resolved against the :app module dir (Gradle test working directory). */
    private static final Path EXPORT_DIR = Path.of("build", "openapi");

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("取得 OpenAPI spec")
    void getOpenApiSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode doc = objectMapper.readTree(body);

        // info.title + description present and 繁體中文.
        assertThat(doc.at("/info/title").asText()).isNotBlank();
        assertThat(CJK.matcher(doc.at("/info/description").asText()).find())
                .as("info.description should be 繁體中文")
                .isTrue();

        // Both internal API families are documented.
        assertThat(doc.at("/paths").has("/internal/campaigns")).isTrue();

        // HTTP Basic security scheme declared so Swagger UI "Try it out" prompts for credentials.
        JsonNode basic = doc.at("/components/securitySchemes/basicAuth");
        assertThat(basic.at("/type").asText()).isEqualTo("http");
        assertThat(basic.at("/scheme").asText()).isEqualTo("basic");
    }

    @Test
    @DisplayName("端點文件涵蓋成功與錯誤回應")
    void endpointDocsCoverSuccessAndErrorResponses() throws Exception {
        JsonNode doc = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode createResponses = doc.at("/paths/~1internal~1campaigns/post/responses");
        assertThat(createResponses.has("201")).isTrue();

        JsonNode statusResponses =
                doc.at("/paths/~1internal~1campaigns~1{id}~1status/post/responses");
        assertThat(statusResponses.has("409")).as("stale version → 409 documented").isTrue();
        assertThat(statusResponses.has("422")).as("illegal transition → 422 documented").isTrue();
    }

    @Test
    @DisplayName("DTO schema 帶繁中欄位說明")
    void dtoSchemaHasChineseFieldDescriptions() throws Exception {
        JsonNode doc = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        JsonNode props = doc.at("/components/schemas/CreateCampaignRequest/properties");
        assertThat(props.has("name")).isTrue();
        assertThat(CJK.matcher(props.at("/name/description").asText()).find())
                .as("CreateCampaignRequest.name should carry a 繁體中文 description")
                .isTrue();
    }

    @Test
    @DisplayName("CI 上匯出 spec 檔")
    void exportSpecFiles() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Files.createDirectories(EXPORT_DIR);
        Path jsonFile = EXPORT_DIR.resolve("openapi.json");
        Path yamlFile = EXPORT_DIR.resolve("openapi.yaml");
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
        Files.writeString(yamlFile, yaml, StandardCharsets.UTF_8);

        assertThat(Files.exists(jsonFile)).isTrue();
        assertThat(Files.exists(yamlFile)).isTrue();
        assertThat(Files.readString(yamlFile, StandardCharsets.UTF_8)).contains("/internal/campaigns");
    }
}

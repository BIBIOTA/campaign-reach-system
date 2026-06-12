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

        JsonNode statusResponses = doc.at("/paths/~1internal~1campaigns~1{id}~1status/post/responses");
        assertThat(statusResponses.has("409"))
                .as("stale version → 409 documented")
                .isTrue();
        assertThat(statusResponses.has("422"))
                .as("illegal transition → 422 documented")
                .isTrue();
    }

    @Test
    @DisplayName("4xx 錯誤回應使用 ApiError schema 而非成功型別")
    void errorResponsesUseApiErrorSchemaNotSuccessType() throws Exception {
        JsonNode doc = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        // POST /internal/campaigns: 400 must document the error body (ApiError), not the success type.
        JsonNode createResponses = doc.at("/paths/~1internal~1campaigns/post/responses");
        assertThat(firstSchemaRef(createResponses.at("/400")))
                .as("create 400 should reference ApiError, not the success CreateCampaignResponse")
                .endsWith("/ApiError");

        // GET .../{id}: 404 is an error body, not the CampaignView success type.
        JsonNode getResponses = doc.at("/paths/~1internal~1campaigns~1{id}/get/responses");
        assertThat(firstSchemaRef(getResponses.at("/404")))
                .as("get 404 should reference ApiError")
                .endsWith("/ApiError");

        // PATCH .../{id}: 400 / 404 / 409 are all error bodies.
        JsonNode updateResponses = doc.at("/paths/~1internal~1campaigns~1{id}/patch/responses");
        for (String code : new String[] {"400", "404", "409"}) {
            assertThat(firstSchemaRef(updateResponses.at("/" + code)))
                    .as("update %s should reference ApiError", code)
                    .endsWith("/ApiError");
        }

        // POST .../{id}/status: @Valid on the body can fail with 400, plus 404 / 409 / 422 — all error
        // bodies, not the CampaignView success type.
        JsonNode statusResponses = doc.at("/paths/~1internal~1campaigns~1{id}~1status/post/responses");
        for (String code : new String[] {"400", "404", "409", "422"}) {
            assertThat(firstSchemaRef(statusResponses.at("/" + code)))
                    .as("status %s should reference ApiError", code)
                    .endsWith("/ApiError");
        }

        // 401 is produced by Spring Security with no typed body: it must NOT advertise the success schema.
        assertThat(firstSchemaRef(createResponses.at("/401")))
                .as("401 must not advertise a success-type body")
                .isEmpty();
    }

    /** Returns the $ref of the response's first content media-type schema, or "" when none is declared. */
    private static String firstSchemaRef(JsonNode response) {
        var media = response.at("/content").elements();
        return media.hasNext() ? media.next().at("/schema/$ref").asText("") : "";
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
        // Decode the response bytes as UTF-8 explicitly: springdoc's yaml endpoint does not always
        // tag a charset, so MockMvc's default getContentAsString() would mis-decode CJK as ISO-8859-1
        // and the on-disk spec would carry mojibake 繁體中文.
        byte[] jsonBytes = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        byte[] yamlBytes = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Files.createDirectories(EXPORT_DIR);
        Path jsonFile = EXPORT_DIR.resolve("openapi.json");
        Path yamlFile = EXPORT_DIR.resolve("openapi.yaml");
        Files.write(jsonFile, jsonBytes);
        Files.write(yamlFile, yamlBytes);

        assertThat(Files.exists(jsonFile)).isTrue();
        assertThat(Files.exists(yamlFile)).isTrue();
        String yamlOnDisk = Files.readString(yamlFile, StandardCharsets.UTF_8);
        assertThat(yamlOnDisk).contains("/internal/campaigns");
        // Guard the encoding: the exported spec must carry intact 繁體中文, not mojibake.
        assertThat(yamlOnDisk).contains("活動");
    }
}

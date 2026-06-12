package com.example.campaignreach.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Documentation-path access guard (task 2.2, 4.2), booting the full Spring context so the production
 * {@code SwaggerDocsSecurityConfig} (@Order(1) permit chain) and {@code CampaignSecurityConfig}
 * (/internal OPERATOR chain) are both in play. Verifies the documentation is publicly viewable while
 * the protected {@code /internal/**} API still demands authentication.
 *
 * <p>Auto-skipped without Docker via the inherited {@link RequiresDocker} condition.
 */
@AutoConfigureMockMvc
class SwaggerDocsSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("瀏覽 Swagger UI")
    void browseSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("文件路徑免認證可存取")
    void documentationPathsAreAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("內部 API 仍需認證")
    void internalApiStillRequiresAuth() throws Exception {
        mockMvc.perform(get("/internal/campaigns/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}

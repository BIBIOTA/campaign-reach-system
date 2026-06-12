package com.example.campaignreach.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全域 OpenAPI 文件設定（task 2.1）。
 *
 * <p>提供 springdoc 產生 {@code /v3/api-docs} 與 {@code /swagger-ui} 時使用的繁體中文 {@code Info}
 * （標題、說明、版本），並宣告一個名為 {@code basicAuth} 的 HTTP Basic security scheme，讓 Swagger UI
 * 的「Try it out」會提示輸入後台 {@code OPERATOR} 帳號密碼（實際授權仍由 {@code CampaignSecurityConfig}
 * 的 {@code /internal/**} 鏈執行）。此設定只放在可部署的 {@code :app}，campaign / reach 僅標註註解。
 */
@Configuration
public class OpenApiConfig {

    /** springdoc 偵測此 {@code OpenAPI} bean 並套用為文件的全域 metadata 與安全需求。 */
    @Bean
    public OpenAPI campaignReachOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("活動觸達系統 API")
                        .description("campaign-reach-system 內部後台 REST API 文件。涵蓋行銷活動的建立、查詢、修改與生命週期"
                                + "狀態轉換（/internal/campaigns），以及觸達成效查詢（/internal/reach）。所有 /internal "
                                + "端點皆需後台 OPERATOR 以 HTTP Basic 認證；請於下方 Authorize 填入帳號密碼後再試打。")
                        .version("v1"))
                // Declare an explicit server so the exported spec carries a concrete, port-qualified
                // base URL. The generated Postman collection derives its {{baseUrl}} variable from this,
                // so an imported collection points at the local app (port 8080) out of the box instead
                // of a portless "http://localhost" that would silently hit port 80.
                .servers(
                        List.of(new Server().url("http://localhost:8080").description("本機開發 (./gradlew :app:bootRun)")))
                .schemaRequirement(
                        "basicAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("後台 OPERATOR 帳號密碼（HTTP Basic）"))
                // Apply basicAuth globally: every /internal endpoint requires it, so Swagger UI marks
                // each operation as secured and the generated Postman collection inherits Basic auth.
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}

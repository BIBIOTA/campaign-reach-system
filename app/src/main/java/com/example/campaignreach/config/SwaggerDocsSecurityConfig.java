package com.example.campaignreach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 放行 API 文件路徑的安全鏈（task 2.2）。
 *
 * <p>以 {@code @Order(1)} 優先於 {@code CampaignSecurityConfig}（其鏈未指定 order，預設為最低優先序）的
 * {@code SecurityFilterChain}，其 {@code securityMatcher} 只涵蓋 springdoc 的文件路徑（{@code
 * /swagger-ui/**}、{@code /swagger-ui.html}、{@code /v3/api-docs} 及其子路徑），並全部 {@code permitAll}，
 * 使文件可公開瀏覽。
 *
 * <p>這些 matcher 與 {@code /internal/**} <em>無重疊</em>，因此 {@code /internal/**} 仍完全由
 * {@code CampaignSecurityConfig} 的 OPERATOR HTTP Basic 鏈保護——本設定不修改、也不削弱該鏈，避免
 * {@code ReachMetricsController} javadoc 所警告的模糊匹配問題。文件為公開靜態合約，不含任何密鑰或帳號。
 */
@Configuration
public class SwaggerDocsSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerDocsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}

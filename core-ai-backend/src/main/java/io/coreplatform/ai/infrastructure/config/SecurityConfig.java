package io.coreplatform.ai.infrastructure.config;

import io.coreplatform.ai.infrastructure.security.RequestIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/",
            "/providers",
            "/models",
            "/scenes",
            "/prompts",
            "/tools",
            "/gateway",
            "/conversations",
            "/knowledge",
            "/agents",
            "/analytics",
            "/index.html",
            "/assets/**",
            "/favicon.ico",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui"
    };

    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "core.security", name = "mode", havingValue = "jwt")
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/providers/**")
                        .hasAuthority("SCOPE_ai.provider.read")
                        .requestMatchers("/api/v1/ai/admin/providers/**")
                        .hasAuthority("SCOPE_ai.provider.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/models/**")
                        .hasAuthority("SCOPE_ai.model.read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/model-aliases/**")
                        .hasAuthority("SCOPE_ai.model.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/admin/models/compare")
                        .hasAuthority("SCOPE_ai.model.read")
                        .requestMatchers("/api/v1/ai/admin/models/**")
                        .hasAuthority("SCOPE_ai.model.manage")
                        .requestMatchers("/api/v1/ai/admin/model-aliases/**")
                        .hasAuthority("SCOPE_ai.model.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/scenes/**")
                        .hasAuthority("SCOPE_ai.scene.read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/scene-templates/**")
                        .hasAuthority("SCOPE_ai.scene.read")
                        .requestMatchers("/api/v1/ai/admin/scenes/**")
                        .hasAuthority("SCOPE_ai.scene.manage")
                        .requestMatchers("/api/v1/ai/admin/scene-templates/**")
                        .hasAuthority("SCOPE_ai.scene.manage")
                        .requestMatchers("/api/v1/ai/scenes/**")
                        .hasAuthority("SCOPE_ai.scene.execute")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/prompts/**")
                        .hasAuthority("SCOPE_ai.prompt.read")
                        .requestMatchers("/api/v1/ai/admin/prompts/**")
                        .hasAuthority("SCOPE_ai.prompt.manage")
                        .requestMatchers("/api/v1/ai/prompts/**")
                        .hasAuthority("SCOPE_ai.prompt.render")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/tools/**", "/api/v1/ai/admin/tool-market/**")
                        .hasAuthority("SCOPE_ai.tool.read")
                        .requestMatchers("/api/v1/ai/admin/tools/**", "/api/v1/ai/admin/tool-market/**")
                        .hasAuthority("SCOPE_ai.tool.manage")
                        .requestMatchers("/api/v1/ai/tools/executions/*/approve")
                        .hasAuthority("SCOPE_ai.tool.approve")
                        .requestMatchers("/api/v1/ai/tools/**")
                        .hasAuthority("SCOPE_ai.tool.execute")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/gateways/**")
                        .hasAuthority("SCOPE_ai.gateway.read")
                        .requestMatchers("/api/v1/ai/admin/gateways/**")
                        .hasAuthority("SCOPE_ai.gateway.manage")
                        .requestMatchers("/api/v1/ai/gateway/**")
                        .hasAuthority("SCOPE_ai.gateway.invoke")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/conversations/**", "/api/v1/ai/conversation-shares/**")
                        .hasAuthority("SCOPE_ai.conversation.read")
                        .requestMatchers("/api/v1/ai/conversations/**", "/api/v1/ai/messages/**")
                        .hasAuthority("SCOPE_ai.conversation.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/memories/**")
                        .hasAuthority("SCOPE_ai.memory.read")
                        .requestMatchers("/api/v1/ai/memories/**")
                        .hasAuthority("SCOPE_ai.memory.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/knowledge/**")
                        .hasAuthority("SCOPE_ai.knowledge.read")
                        .requestMatchers("/api/v1/ai/admin/knowledge/**")
                        .hasAuthority("SCOPE_ai.knowledge.manage")
                        .requestMatchers("/api/v1/ai/knowledge/**")
                        .hasAuthority("SCOPE_ai.knowledge.search")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/agents/**")
                        .hasAuthority("SCOPE_ai.agent.read")
                        .requestMatchers("/api/v1/ai/admin/agents/**")
                        .hasAuthority("SCOPE_ai.agent.manage")
                        .requestMatchers("/api/v1/ai/agent-executions/*/approval")
                        .hasAuthority("SCOPE_ai.agent.approve")
                        .requestMatchers("/api/v1/ai/agents/**", "/api/v1/ai/agent-executions/**")
                        .hasAuthority("SCOPE_ai.agent.execute")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/admin/analytics/**")
                        .hasAuthority("SCOPE_ai.analytics.read")
                        .requestMatchers("/api/v1/ai/admin/analytics/**")
                        .hasAuthority("SCOPE_ai.analytics.manage")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(
            prefix = "core.security",
            name = "mode",
            havingValue = "local",
            matchIfMissing = true
    )
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

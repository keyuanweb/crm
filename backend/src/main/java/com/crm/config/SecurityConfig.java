package com.crm.config;

import com.crm.security.ApiKeyAuthFilter;
import com.crm.security.JwtAuthFilter;
import com.crm.service.ApiKeyService;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Spring Security 配置：无状态 JWT、方法级授权、环境变量驱动 CORS（research.md R1）。 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final String allowedOrigins;

  public SecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      ApiKeyService apiKeyService,
      @Value("${cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.apiKeyAuthFilter = new ApiKeyAuthFilter(apiKeyService);
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/captcha",
                        // 082：二次验证。它进场时用户还没有令牌（凭密码阶段的一次性 mfaToken），
                        // 故必须放行 —— 但**只能是这一条精确路径**，绝不能写成 /api/v1/auth/2fa/**：
                        // 通配会把 setup/enable/status/regenerate/disable 一起放出去，而那五个端点靠
                        // SecurityUtil.currentUserId() 取主体、放行后拿到 null，症状从"401 被拒"
                        // 变成控制器里抛出的另一个 401/500，且失败方向由"少了授权"变成"少了认证"。
                        // 护栏是 AuthMfaIT：它断言未带令牌调 /2fa/status 得到的是**空体** 401
                        // （HttpStatusEntryPoint 不写 body），而通配化会让响应变成带 code 的 JSON。
                        "/api/v1/auth/2fa/verify",
                        "/error")
                    .permitAll()
                    .requestMatchers("/api/v1/public/**")
                    .permitAll()
                    .requestMatchers("/api/v1/open/**")
                    .hasRole("OPEN_API")
                    .requestMatchers("/ws/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // 055：API Key 鉴权先于 JWT（/api/v1/open/** 走 X-API-Key）
        .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}

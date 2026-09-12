package com.crm.config;

import com.crm.security.JwtAuthFilter;
import com.crm.security.JwtUtil;
import com.crm.ws.NotificationWebSocketHandler;
import io.jsonwebtoken.Claims;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 配置（026-realtime-notify）：注册通知端点 + JWT 握手认证。
 *
 * <p><b>FR-G12（083）</b>：握手校验必须与 HTTP 认证路径一致——除签名与有效期外，还要校验账号 {@code enabled} 与令牌版本 {@code
 * tokenVersion}；允许的来源必须复用既有的跨域允许来源配置，不为通配，也不新增第二个来源配置项。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
  public static final String ATTR_USER_ID = "userId";

  private final NotificationWebSocketHandler notificationHandler;
  private final JwtUtil jwtUtil;
  private final JwtAuthFilter jwtAuthFilter;
  private final List<String> allowedOriginPatterns;

  public WebSocketConfig(
      NotificationWebSocketHandler notificationHandler,
      JwtUtil jwtUtil,
      JwtAuthFilter jwtAuthFilter,
      // 复用既有跨域允许来源配置（与 SecurityConfig.corsConfigurationSource 同一项，默认值亦一致）：
      // 第二个来源配置项会与前者分歧，而"两套来源判定谁生效"取决于请求先撞上哪个过滤器，
      // 这正是不该出现的形态（FR-G12）。
      @Value("${cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
    this.notificationHandler = notificationHandler;
    this.jwtUtil = jwtUtil;
    this.jwtAuthFilter = jwtAuthFilter;
    this.allowedOriginPatterns =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(notificationHandler, "/ws/notifications")
        .addInterceptors(jwtHandshakeInterceptor())
        .setAllowedOrigins(allowedOriginPatterns.toArray(new String[0]));
  }

  private HandshakeInterceptor jwtHandshakeInterceptor() {
    return new HandshakeInterceptor() {
      @Override
      public boolean beforeHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler wsHandler,
          Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null || !query.contains("token=")) {
          log.debug("WS handshake rejected: missing token");
          return false;
        }
        String token = null;
        for (String pair : query.split("&")) {
          if (pair.startsWith("token=")) {
            token = pair.substring(6);
            break;
          }
        }
        if (token == null || token.isBlank()) {
          return false;
        }
        try {
          Claims claims = jwtUtil.parse(token);
          Object userIdObj = claims.get("userId");
          Object tvObj = claims.get("tv");
          if (userIdObj instanceof Number n) {
            Long userId = n.longValue();
            // 与 HTTP 路径**共用同一处**状态校验（FR-G12）：账号停用、密码重置／管理员重置后
            // tokenVersion 递增，旧令牌必须在握手阶段就被拒。证书签名字面有效但账号已停用的令牌，
            // 是握手与 HTTP 两条路径最容易分歧的地方——HTTP 走 JwtAuthFilter.validateUserState，
            // 握手原先只验签名，于是同一条令牌在 REST 上被拒、在 WS 上仍能建立长连接。
            int claimTv = tvObj instanceof Number t ? t.intValue() : -1;
            if (!jwtAuthFilter.validateUserState(userId, claimTv)) {
              log.debug("WS handshake rejected: user {} disabled or token version stale", userId);
              return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            return true;
          }
        } catch (Exception ex) {
          log.debug("WS handshake rejected: invalid token {}", ex.getMessage());
        }
        return false;
      }

      @Override
      public void afterHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler wsHandler,
          Exception exception) {
        // no-op
      }
    };
  }
}

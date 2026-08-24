package com.crm.config;

import com.crm.security.JwtUtil;
import com.crm.ws.NotificationWebSocketHandler;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket 配置（026-realtime-notify）：注册通知端点 + JWT 握手认证。 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
  public static final String ATTR_USER_ID = "userId";

  private final NotificationWebSocketHandler notificationHandler;
  private final JwtUtil jwtUtil;

  public WebSocketConfig(NotificationWebSocketHandler notificationHandler, JwtUtil jwtUtil) {
    this.notificationHandler = notificationHandler;
    this.jwtUtil = jwtUtil;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(notificationHandler, "/ws/notifications")
        .addInterceptors(jwtHandshakeInterceptor())
        .setAllowedOrigins("*");
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
          if (userIdObj instanceof Number n) {
            attributes.put(ATTR_USER_ID, n.longValue());
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

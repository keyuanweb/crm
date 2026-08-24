package com.crm.ws;

import com.crm.config.WebSocketConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知 WebSocket 处理器（026-realtime-notify，FR-002/003/007）：维护 userId→会话映射， notifyUser
 * 向目标用户全部会话推送轻量消息。会话映射存内存（单实例）。
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Object userIdObj = session.getAttributes().get(WebSocketConfig.ATTR_USER_ID);
    if (userIdObj instanceof Long userId) {
      register(userId, session);
    } else {
      log.warn("WS session without userId attribute, closing");
      try {
        session.close(CloseStatus.NOT_ACCEPTABLE);
      } catch (Exception ignored) {
        // ignore
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    unregister(session);
  }

  /** 注册会话（握手成功后调用）。 */
  public void register(Long userId, WebSocketSession session) {
    sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    log.debug("WS session registered for user {}", userId);
  }

  /** 注销会话（连接关闭后调用）。 */
  public void unregister(WebSocketSession session) {
    sessionsByUser.forEach(
        (userId, sessions) -> {
          sessions.remove(session);
          if (sessions.isEmpty()) {
            sessionsByUser.remove(userId);
          }
        });
  }

  /** 向目标用户所有在线会话推送通知。 */
  public void notifyUser(Long userId, NotificationPushPayload payload) {
    Set<WebSocketSession> sessions = sessionsByUser.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }
    try {
      String json = MAPPER.writeValueAsString(payload);
      TextMessage message = new TextMessage(json);
      java.util.Iterator<WebSocketSession> it = sessions.iterator();
      while (it.hasNext()) {
        WebSocketSession s = it.next();
        if (s.isOpen()) {
          try {
            s.sendMessage(message);
          } catch (Exception ex) {
            log.warn("WS send failed, removing session: {}", ex.getMessage());
            it.remove();
          }
        } else {
          it.remove();
        }
      }
      if (sessions.isEmpty()) {
        sessionsByUser.remove(userId);
      }
    } catch (Exception ex) {
      log.warn("Failed to serialize notification push payload: {}", ex.getMessage());
    }
  }
}

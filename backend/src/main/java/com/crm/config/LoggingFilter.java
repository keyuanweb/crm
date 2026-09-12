package com.crm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 结构化请求日志（SLF4J，章程原则五）。
 *
 * <p><b>为什么不再包装响应</b>（FR-G22）：改造前本过滤器用 {@code ContentCachingResponseWrapper} 包住响应，并在结束时 {@code
 * copyBodyToResponse()} —— 该组合会把**整个响应体**读进堆后再写回。而本类全部日志字段只有 {@code
 * method/uri/status/elapsedMs}，**从不读取响应体**：每个请求都为此付出一次全量响应体的堆占用与一次额外拷贝（导出类端点的 响应体可达数 MB），换来的信息量为零。
 *
 * <p>不记录响应体也不损失可观测性：错误响应体已由 {@code GlobalExceptionHandler} 按其自身策略记录，此处再抄一份
 * 既重复、又会把凭据类内容写进日志（章程的"凭据不入日志"约束）。
 */
@Component
public class LoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      // 仍放在 finally：异常路径同样要留下 method/uri/status/elapsedMs，
      // 且异常时往往正是最需要这几项的时候（改造前日志也在 finally 里）。
      long elapsed = System.currentTimeMillis() - start;
      log.info(
          "method={} uri={} status={} elapsedMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          elapsed);
    }
  }
}

package com.crm.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 模型客户端的构造处（104-ai-content-generation）。
 *
 * <p><b>为什么单独一个类，而不是把客户端建在 {@code AiContentService} 里</b>：{@code AiContentService} 是写审计的那一处， 而本仓用
 * grep 断言"密钥不落进日志／审计／异常"（FR-004 / SC-004）。让<b>取用 api-key 的代码与写审计的代码分居两个文件</b>，
 * 那条断言才是一句结构性的事实（"那个类里根本没有密钥这个字符串"），而不是一次碰巧的巧合。 代价是多一个 30 行的类。
 *
 * <p><b>未配置时不构造客户端</b>：{@code client()} 返回 {@link Optional#empty()}。出厂部署（{@code
 * crm.ai.enabled=false}） 因此<b>连锁的 HTTP 客户端都不会被建出来</b>——"零出站"是结构性的，不靠调用方记得先判门。
 *
 * <p>构造发生在启动期（无网络 I/O：建客户端只是装配）。真正的出站只发生在 {@code AiContentService.generate(...)} 里，且必须先过 {@code
 * AiStatus.isConfigured()}。
 */
@Component
public class AiClientFactory {

  /**
   * SDK 自带的自动重试次数。
   *
   * <p>刻意<b>不</b>用 SDK 的默认值（2）：一次生成的最坏耗时是 {@code timeout} × (1 + 重试次数)， 而本项是<b>同步</b>调用、每次占用一个
   * servlet 线程（plan D3）。配 1 次重试把最坏耗时封在 2×timeout（默认 120s）， 足够覆盖"上游偶发
   * 5xx"，又不至于让一个卡住的请求无限占用线程。要放开并发前必须先有有界执行器（plan D3）。
   */
  private static final int MAX_RETRIES = 1;

  private final AnthropicClient client;

  public AiClientFactory(AiStatus status) {
    this.client = status.isConfigured() ? build(status) : null;
  }

  /** 已配置的模型客户端；未启用／未配置时为 {@link Optional#empty()}。 */
  public Optional<AnthropicClient> client() {
    return Optional.ofNullable(client);
  }

  private static AnthropicClient build(AiStatus status) {
    return AnthropicOkHttpClient.builder()
        // ⚠️ 方法名是 baseUrl（大写 U）。SDK 另有 Optional<String> 重载，签名只差一个参数类型——
        // 写错成 baseURL/setBaseUrl 会直接编译不过（本项开工时正是靠编译器确认的这一条，见 research.md §11.1）。
        .baseUrl(status.baseUrl())
        .apiKey(status.apiKey())
        .timeout(Duration.ofSeconds(status.timeoutSeconds()))
        .maxRetries(MAX_RETRIES)
        .build();
  }
}

package com.crm.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 通用 HTTP 客户端 Bean（055-open-platform Webhook 推送等）。 */
@Configuration
public class RestTemplateConfig {

  /**
   * 出站客户端：**不自动跟随 3xx**（FR-G13）。
   *
   * <p><b>为什么重定向的落点必须由我们自己校验</b>：创建订阅时校验的是<b>落库的那个地址</b>，而重定向的落点由对端在响应里
   * 临时给出、创建时并不存在。客户端一旦自动跟随，{@code Location} 就成了一条绕开校验的通道——服务端会去请求一个从未被判定过的 地址（{@code
   * http://169.254.169.254/…} 之类）。故这里关掉自动跟随，由 {@code WebhookDeliverer} 逐跳校验后再决定是否继续。
   *
   * <p><b>这一行不是"顺手加固"，但也不是唯一的防线</b>——实测过的事实，写下来免得后人误判：<br>
   * ① JDK 的 {@link HttpURLConnection} <b>默认跟随</b>，且对 POST 收到的 302 也跟随（实测：第二跳被改写成 GET 并返回
   * 200）——所以"POST 不会被跟随"是错觉。<br>
   * ② 但 Spring 的 {@link SimpleClientHttpRequestFactory#prepareConnection} 自身对 <b>非 GET</b> 方法就设了
   * {@code setInstanceFollowRedirects(false)}，对 <b>GET</b> 才设 true。也就是说本项目 Webhook 的 POST
   * 路径在改造前<b>恰好</b>没有跟随——靠的是框架一处未文档化的实现细节；换掉 request factory（如 Apache HttpClient 默认跟随
   * POST）或有人显式打开，这条防线立刻消失。<br>
   * ③ 故此处显式关闭：POST 路径上它把"碰巧安全"变成"明确安全"，GET 路径上它是唯一的防线。
   *
   * <p><b>与"逐跳校验"是一对</b>：只关不校验，合法重定向会被当成失败（投递器退化为不支持重定向）；
   * 只校验不关，校验代码根本不会被调用到（请求早已被客户端自己发往落点）。两处必须同时存在，改动其一即失效。 回归护栏见 {@code WebhookRedirectIT}。
   */
  @Bean
  public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory =
        new SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(HttpURLConnection connection, String httpMethod)
              throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
          }
        };
    return new RestTemplate(factory);
  }
}

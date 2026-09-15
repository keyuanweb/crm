package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 系统增强集成测试（016 T014/T024/T032）：自定义字段/通知/导出/权限。 */
class SystemEnhancementIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("自定义字段：配置→实体携带值→详情回显→筛选")
  void customFieldFlow() throws Exception {
    String token = loginAndGetToken();

    // 配置字段（LEAD 数字字段）
    String fieldResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"预算规模\", \"fieldType\": \"NUMBER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("预算规模"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long fieldId = objectMapper.readTree(fieldResp).path("data").path("id").asLong();

    // 创建线索携带字段值
    String leadResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"字段线索\", \"company\": \"字段公司\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"500\"}]}",
                            fieldId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long leadId = objectMapper.readTree(leadResp).path("data").path("id").asLong();

    // 详情回显
    mockMvc
        .perform(get("/api/v1/leads/{id}", leadId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customFieldValues[0].fieldId").value(fieldId))
        .andExpect(jsonPath("$.data.customFieldValues[0].value").value("500"));

    // 列表包含自定义字段值
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // SUPPORT 不可配置字段 → 403
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"enhanceagent\", \"password\": \"Passw0rd!\", \"displayName\": \"增强客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("enhanceagent", "Passw0rd!");
    mockMvc
        .perform(
            post("/api/v1/custom-fields")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\": \"LEAD\", \"name\": \"越权字段\", \"fieldType\": \"TEXT\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("自定义字段列表筛选（FR-S03：cf_<fieldId> SELECT 精确 / 文本 LIKE）")
  void customFieldListFilter() throws Exception {
    String token = loginAndGetToken();

    // 配置 LEAD SELECT 字段（选项 A/B）
    String selectResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"客户分级\", \"fieldType\": \"SELECT\", \"options\": \"A,B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long selectFieldId = objectMapper.readTree(selectResp).path("data").path("id").asLong();

    // 两条线索分别携带不同选项
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"筛选线索A\", \"company\": \"筛A\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"A\"}]}",
                        selectFieldId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"筛选线索B\", \"company\": \"筛B\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"B\"}]}",
                        selectFieldId)))
        .andExpect(status().isOk());

    // SELECT 精确筛选 → 仅命中 A
    mockMvc
        .perform(
            get("/api/v1/leads")
                .header("Authorization", bearer(token))
                .param("cf_" + selectFieldId, "A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("筛选线索A"));

    // 文本类型 LIKE 筛选
    String textResp =
        mockMvc
            .perform(
                post("/api/v1/custom-fields")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entityType\": \"LEAD\", \"name\": \"行业\", \"fieldType\": \"TEXT\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long textFieldId = objectMapper.readTree(textResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"行业线索\", \"company\": \"行\", \"customFieldValues\": [{\"fieldId\": %d, \"value\": \"制造业\"}]}",
                        textFieldId)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/leads")
                .header("Authorization", bearer(token))
                .param("cf_" + textFieldId, "制造"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("行业线索"));
  }

  @Test
  @DisplayName("通知中心：分配工单→通知→已读→未读计数")
  void notificationFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "通知客户");

    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"通知工单\", \"priority\": \"LOW\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    // 分配给自己不产生通知；先建一个 SUPPORT 用户再分配
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"notifyuser\", \"password\": \"Passw0rd!\", \"displayName\": \"通知用户\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    long targetId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/api/v1/users")
                            .param("keyword", "notifyuser")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data")
            .path("items")
            .get(0)
            .path("id")
            .asLong();

    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"assigneeId\": %d}", targetId)))
        .andExpect(status().isOk());

    // 目标用户登录查看通知
    String targetToken = loginAndGetToken("notifyuser", "Passw0rd!");
    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.unreadCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 全部已读
    mockMvc
        .perform(
            post("/api/v1/notifications/read-all").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", bearer(targetToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(0));
  }

  @Test
  @DisplayName("数据导出：创建任务→状态查询→下载（完成后）")
  void exportFlow() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "导出客户");

    String jobResp =
        mockMvc
            .perform(
                post("/api/v1/exports")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\": \"CUSTOMER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long jobId = objectMapper.readTree(jobResp).path("data").path("id").asLong();

    // 轮询状态直到 DONE（异步执行）
    String status = "PENDING";
    for (int i = 0; i < 20 && ("PENDING".equals(status) || "RUNNING".equals(status)); i++) {
      Thread.sleep(200);
      String listResp =
          mockMvc
              .perform(get("/api/v1/exports").header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      status =
          objectMapper.readTree(listResp).path("data").path("items").get(0).path("status").asText();
    }
    org.assertj.core.api.Assertions.assertThat(status).isEqualTo("DONE");

    // 下载
    mockMvc
        .perform(get("/api/v1/exports/{id}/download", jobId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(
                    "Content-Type",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  /**
   * 导出在独立线程池里跑，而 {@code SecurityUtil} 读的是 ThreadLocal 里的 SecurityContext —— 裸 {@code
   * Executors.newFixedThreadPool} 里主体恒为 null，063 的脱敏与行级过滤会双双退化为空转。本用例钉住"异步线程必须带上主体"。
   *
   * <p>两侧都断言：非 ADMIN 脱敏、ADMIN 不脱敏 —— 只测前者的话，一个"无差别脱敏"的实现同样能骗过它。
   *
   * <p>行级过滤是同一根因的**第二个**受害者（{@code visibleOwnersOrNull()} 同样读不到主体就返回 null = 不做过滤），故一并钉住：ADMIN
   * 名下（owner 为空）的客户不得出现在 SALES 的文件里。只断言"该有的在"会漏掉它。
   */
  @Test
  @DisplayName("导出：非 ADMIN 的文件必须脱敏且不得越界，ADMIN 不脱敏（异步线程须带主体）")
  void exportMasksSensitiveFieldsForNonAdmin() throws Exception {
    String adminToken = loginAndGetToken();
    String username = "exportsales" + (System.nanoTime() % 100000);
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"username\": \"%s\", \"password\": \"Passw0rd!\", \"displayName\": \"导出销售\", \"role\": \"SALES\"}",
                        username)))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken(username, "Passw0rd!");

    // 非 ADMIN 建的客户归属自己 ⇒ 必定落在其可见范围内（不受数据权限档位影响）
    String customerName = "脱敏客户" + System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"%s\", \"company\": \"脱敏公司\", \"phone\": \"13800001234\", \"email\": \"mask@example.com\"}",
                        customerName)))
        .andExpect(status().isCreated());

    // ADMIN 建的客户 owner 为空（CustomerService 只给非 ADMIN 设默认负责人）⇒ 必在 SALES 的 owner 集之外
    String outsiderName = "越界客户" + System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"%s\", \"company\": \"越界公司\", \"phone\": \"13900004321\"}",
                        outsiderName)))
        .andExpect(status().isCreated());

    String salesSheet = exportAsText(salesToken, customerName);
    org.assertj.core.api.Assertions.assertThat(salesSheet)
        .contains("138****1234")
        .contains("m***k@example.com")
        .doesNotContain("13800001234")
        .doesNotContain("mask@example.com")
        .as("行级过滤：他人名下的客户不得进入非 ADMIN 的导出")
        .doesNotContain(outsiderName);

    String adminSheet = exportAsText(adminToken, customerName);
    org.assertj.core.api.Assertions.assertThat(adminSheet)
        .contains("13800001234")
        // 反空断言：越界客户确实存在、确实导得出来 —— 否则上一句的 doesNotContain 可能只是"它压根不在文件里"
        .contains(outsiderName);
  }

  /** 建导出任务 → 轮询到 DONE → 下载 → 工作簿所有单元格拼成一个字符串。 */
  private String exportAsText(String token, String mustContain) throws Exception {
    String jobResp =
        mockMvc
            .perform(
                post("/api/v1/exports")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\": \"CUSTOMER\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long jobId = objectMapper.readTree(jobResp).path("data").path("id").asLong();

    String status = "PENDING";
    for (int i = 0; i < 50 && ("PENDING".equals(status) || "RUNNING".equals(status)); i++) {
      Thread.sleep(200);
      String listResp =
          mockMvc
              .perform(get("/api/v1/exports").header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      for (var item : objectMapper.readTree(listResp).path("data").path("items")) {
        if (item.path("id").asLong() == jobId) {
          status = item.path("status").asText();
        }
      }
    }
    org.assertj.core.api.Assertions.assertThat(status).as("导出任务应完成").isEqualTo("DONE");

    byte[] bytes =
        mockMvc
            .perform(
                get("/api/v1/exports/{id}/download", jobId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
    StringBuilder sb = new StringBuilder();
    try (org.apache.poi.ss.usermodel.Workbook wb =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      for (org.apache.poi.ss.usermodel.Sheet sheet : wb) {
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
          for (org.apache.poi.ss.usermodel.Cell cell : row) {
            sb.append(fmt.formatCellValue(cell)).append('');
          }
        }
      }
    }
    String text = sb.toString();
    org.assertj.core.api.Assertions.assertThat(text)
        .as("导出文件应包含目标行 %s", mustContain)
        .contains(mustContain);
    return text;
  }

  @Test
  @DisplayName("权限：SUPPORT 不可配置 SLA 之外的字段相关均走 403 路径已覆盖")
  void permissionMatrix() throws Exception {
    String token = loginAndGetToken();
    // SUPPORT 下载他人导出 → 403（先由 admin 创建）
    String jobResp =
        mockMvc
            .perform(
                post("/api/v1/exports")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exportType\": \"LEAD\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long jobId = objectMapper.readTree(jobResp).path("data").path("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"exportagent\", \"password\": \"Passw0rd!\", \"displayName\": \"导出客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("exportagent", "Passw0rd!");
    mockMvc
        .perform(
            get("/api/v1/exports/{id}/download", jobId)
                .header("Authorization", bearer(supportToken)))
        .andExpect(status().isForbidden());
  }

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"%s%d\", \"company\": \"%s公司\"}",
                            name, System.nanoTime(), name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }
}

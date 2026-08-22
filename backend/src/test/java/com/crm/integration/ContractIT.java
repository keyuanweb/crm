package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** 合同集成测试（008 T013/T020/T025）：创建→编辑→提交→审批→生效→附件→权限。 */
class ContractIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"合同测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createApprovedQuote(String token, long customerId) throws Exception {
    // 产品
    String prodResp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\": \"CT-IT-P-001\", \"name\": \"合同产品\", \"standardPrice\": 100000}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long productId = objectMapper.readTree(prodResp).path("data").path("id").asLong();
    // 报价
    String quoteResp =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"items\": [{\"productId\": %d, \"quantity\": 2, \"discount\": 1}]}",
                            customerId, productId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long quoteId = objectMapper.readTree(quoteResp).path("data").path("id").asLong();
    mockMvc
        .perform(post("/api/v1/quotes/{id}/submit", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/quotes/{id}/approve", quoteId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    return quoteId;
  }

  @Test
  @DisplayName("合同生命周期：基于报价创建→编辑→提交→审批→生效→完成")
  void contractLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "合同客户A");
    long quoteId = createApprovedQuote(token, customerId);

    // 1. 基于报价创建（金额自动带入 200000）
    String createBody =
        String.format(
            "{\"title\": \"合同IT-001\", \"customerId\": %d, \"quoteId\": %d}", customerId, quoteId);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.contractNo").value(org.hamcrest.Matchers.startsWith("HT-")))
            .andExpect(jsonPath("$.data.amount").value(200000))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 2. 编辑草稿
    mockMvc
        .perform(
            put("/api/v1/contracts/{id}", contractId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"title\": \"合同IT-001改\", \"customerId\": %d, \"amount\": 250000, \"version\": 0}",
                        customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("合同IT-001改"))
        .andExpect(jsonPath("$.data.amount").value(250000));

    // 3. 提交
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

    // 4. 提交后不可编辑
    mockMvc
        .perform(
            put("/api/v1/contracts/{id}", contractId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"title\": \"x\", \"customerId\": %d, \"amount\": 1, \"version\": 0}",
                        customerId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONTRACT_INVALID_STATE"));

    // 5. 审批通过
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/approve", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.approverId").isNumber());

    // 6. 生效
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EFFECTIVE"))
        .andExpect(jsonPath("$.data.effectiveAt").isNotEmpty());

    // 7. 完成
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/complete", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"));

    // 8. 终态重复生效 → 409
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONTRACT_INVALID_STATE"));
  }

  @Test
  @DisplayName("拒绝流程：提交→拒绝填意见→可编辑重提")
  void rejectAndResubmit() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "合同客户B");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"合同IT-002\", \"customerId\": %d, \"amount\": 100000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/reject", contractId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"条款需修改\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REJECTED"))
        .andExpect(jsonPath("$.data.rejectReason").value("条款需修改"));

    // 重提（rejectReason 清空）
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.data.rejectReason").doesNotExist());
  }

  @Test
  @DisplayName("模板渲染：基于模板创建合同占位符替换")
  void templateRendering() throws Exception {
    String token = loginAndGetToken();
    // 创建模板
    String templateResp =
        mockMvc
            .perform(
                post("/api/v1/contract-templates")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"标准模板\", \"content\": \"甲方：{customerName}，编号 {contractNo}，金额 {amount} 元\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long templateId = objectMapper.readTree(templateResp).path("data").path("id").asLong();

    long customerId = createCustomer(token, "模板客户");
    mockMvc
        .perform(
            post("/api/v1/contracts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"title\": \"合同IT-003\", \"customerId\": %d, \"amount\": 150000, \"templateId\": %d}",
                        customerId, templateId)))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("甲方：模板客户")))
        .andExpect(
            jsonPath("$.data.content")
                .value(org.hamcrest.Matchers.containsString("金额 1,500.00 元")));
  }

  @Test
  @DisplayName("附件：上传→列表→下载一致→删除")
  void attachmentLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "合同客户C");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"合同IT-004\", \"customerId\": %d, \"amount\": 100000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();

    byte[] fileContent = "contract-scan-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    MockMultipartFile file =
        new MockMultipartFile("file", "扫描件.pdf", "application/pdf", fileContent);

    // 上传
    String uploadResp =
        mockMvc
            .perform(
                multipart("/api/v1/contracts/{id}/attachments", contractId)
                    .file(file)
                    .header("Authorization", bearer(token)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.fileName").value("扫描件.pdf"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long attachmentId = objectMapper.readTree(uploadResp).path("data").path("id").asLong();

    // 列表（详情含附件）
    mockMvc
        .perform(get("/api/v1/contracts/{id}", contractId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attachments[0].fileName").value("扫描件.pdf"));

    // 下载内容一致
    byte[] downloaded =
        mockMvc
            .perform(
                get("/api/v1/contracts/{id}/attachments/{aid}/download", contractId, attachmentId)
                    .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(downloaded).isEqualTo(fileContent);

    // 删除后列表为空
    mockMvc
        .perform(
            delete("/api/v1/contracts/{id}/attachments/{aid}", contractId, attachmentId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/contracts/{id}", contractId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attachments").isEmpty());
  }

  @Test
  @DisplayName("附件：非法类型返回 400")
  void invalidAttachmentTypeThrows400() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "合同客户D");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"合同IT-005\", \"customerId\": %d, \"amount\": 100000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();

    MockMultipartFile file =
        new MockMultipartFile("file", "恶意.exe", "application/octet-stream", "bad".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/contracts/{id}/attachments", contractId)
                .file(file)
                .header("Authorization", bearer(token)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("ATTACHMENT_INVALID"));
  }

  @Test
  @DisplayName("非管理员审批合同返回 403")
  void nonAdminApproveForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"salescontract\", \"password\": \"Passw0rd!\", \"displayName\": \"合同销售\", \"role\": \"SALES\"}"))
        .andExpect(status().isCreated());
    String salesToken = loginAndGetToken("salescontract", "Passw0rd!");

    long customerId = createCustomer(salesToken, "合同客户E");
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(salesToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"合同IT-006\", \"customerId\": %d, \"amount\": 100000}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractId = objectMapper.readTree(resp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractId)
                .header("Authorization", bearer(salesToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/approve", contractId)
                .header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());

    // SALES 也不能创建模板
    mockMvc
        .perform(
            post("/api/v1/contract-templates")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"x\", \"content\": \"y\"}"))
        .andExpect(status().isForbidden());
  }
}

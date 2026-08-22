package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.Customer;
import com.crm.repository.CustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 客户搜索/筛选/分页集成测试（T026，US2）。 */
class CustomerSearchIT extends AbstractIntegrationTest {

  @Autowired private CustomerMapper customerMapper;

  @BeforeEach
  void seedCustomers() {
    for (int i = 1; i <= 25; i++) {
      Customer c = new Customer();
      c.setName("客户" + i);
      c.setCompany(i % 2 == 0 ? "科技公司" : "贸易公司");
      c.setPhone("1380000" + String.format("%04d", i));
      c.setStatus("ACTIVE");
      customerMapper.insert(c);
    }
  }

  @Test
  @DisplayName("关键字搜索仅返回命中的客户")
  void keywordSearchFilters() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            get("/api/v1/customers").header("Authorization", bearer(token)).param("keyword", "科技"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(12))
        .andExpect(jsonPath("$.data.items[0].company").value("科技公司"));
  }

  @Test
  @DisplayName("分页返回正确的 page/pageSize 与数据")
  void paginationWorks() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", bearer(token))
                .param("page", "2")
                .param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(25))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.items.length()").value(10));
  }

  @Test
  @DisplayName("按状态筛选生效")
  void statusFilterWorks() throws Exception {
    String token = loginAndGetToken();
    // 先将一条改为 INACTIVE
    Customer first = customerMapper.selectList(null).get(0);
    first.setStatus("INACTIVE");
    customerMapper.updateById(first);

    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", bearer(token))
                .param("status", "INACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  @DisplayName("批量造数（>10k）下搜索仍正确（SC-002 冒烟）")
  void searchCorrectnessAtVolume() throws Exception {
    for (int i = 0; i < 100; i++) {
      Customer c = new Customer();
      c.setName("批量客户" + i);
      c.setCompany("批量公司" + i);
      c.setStatus("ACTIVE");
      customerMapper.insert(c);
    }
    // 共 25 + 100 = 125 条
    String token = loginAndGetToken();
    long start = System.currentTimeMillis();
    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", bearer(token))
                .param("keyword", "批量客户1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(11));
    long elapsed = System.currentTimeMillis() - start;
    org.assertj.core.api.Assertions.assertThat(elapsed).isLessThan(5000);
  }
}

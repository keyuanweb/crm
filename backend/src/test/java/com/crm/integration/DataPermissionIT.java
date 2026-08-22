package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 数据权限集成测试（012 T014/T023/T028）：部门/行级过滤/共享。 */
class DataPermissionIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"权限测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createUser(String adminToken, String username) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \""
                            + username
                            + "\", \"password\": \"Passw0rd!\", \"displayName\": \""
                            + username
                            + "\", \"role\": \"SALES\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("部门管理：建部门→子部门→树→删除防护")
  void departmentLifecycle() throws Exception {
    String adminToken = loginAndGetToken();

    // 建顶级部门
    String resp =
        mockMvc
            .perform(
                post("/api/v1/departments")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"销售一部\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long deptId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 建子部门
    mockMvc
        .perform(
            post("/api/v1/departments")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"华东组\", \"parentId\": " + deptId + "}"))
        .andExpect(status().isCreated());

    // 部门树
    mockMvc
        .perform(get("/api/v1/departments/tree").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.departments[0].name").value("销售一部"))
        .andExpect(jsonPath("$.data.departments[0].children[0].name").value("华东组"));

    // 删除有子部门的顶级部门 → 409
    mockMvc
        .perform(
            delete("/api/v1/departments/{id}", deptId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DEPARTMENT_HAS_CHILDREN_OR_MEMBERS"));
  }

  @Test
  @DisplayName("行级权限：SELF 仅本人客户，DEPT_AND_CHILD 含同部门")
  void dataScopeFiltering() throws Exception {
    String adminToken = loginAndGetToken();
    // 建两部门
    String dept1Resp =
        mockMvc
            .perform(
                post("/api/v1/departments")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"销售一部\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long dept1 = objectMapper.readTree(dept1Resp).path("data").path("id").asLong();
    String dept2Resp =
        mockMvc
            .perform(
                post("/api/v1/departments")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"销售二部\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long dept2 = objectMapper.readTree(dept2Resp).path("data").path("id").asLong();

    // 两销售用户：salesA 归一部（DEPT_AND_CHILD），salesB 归二部（SELF）
    long salesA = createUser(adminToken, "dpsalesa");
    long salesB = createUser(adminToken, "dpsalesb");
    mockMvc
        .perform(
            put("/api/v1/users/{id}/data-permission", salesA)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\": " + dept1 + ", \"dataScope\": \"DEPT_AND_CHILD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dataScope").value("DEPT_AND_CHILD"));
    mockMvc
        .perform(
            put("/api/v1/users/{id}/data-permission", salesB)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\": " + dept2 + ", \"dataScope\": \"SELF\"}"))
        .andExpect(status().isOk());

    // 客户归属 salesA（admin 领取后转移给 salesA）
    long customerId = createCustomer(adminToken, "权限客户A");
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", customerId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/customers/batch-transfer")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerIds\": [" + customerId + "], \"targetOwnerId\": " + salesA + "}"))
        .andExpect(status().isOk());

    // salesA（DEPT_AND_CHILD，一部）可见该客户
    String salesAToken = loginAndGetToken("dpsalesa", "Passw0rd!");
    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", bearer(salesAToken))
                .param("keyword", "权限客户A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    // salesB（SELF，二部）不可见该客户
    String salesBToken = loginAndGetToken("dpsalesb", "Passw0rd!");
    mockMvc
        .perform(
            get("/api/v1/customers")
                .header("Authorization", bearer(salesBToken))
                .param("keyword", "权限客户A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    // salesB 直接访问详情 → 403（非归属非共享）
    mockMvc
        .perform(
            get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(salesBToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("客户共享：共享→对方可见详情→编辑 403→取消不可见")
  void customerSharing() throws Exception {
    String adminToken = loginAndGetToken();
    long ownerUser = createUser(adminToken, "shareowner");
    long viewerUser = createUser(adminToken, "shareviewer");

    // 客户归属 ownerUser
    long customerId = createCustomer(adminToken, "共享客户X");
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", customerId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/customers/batch-transfer")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerIds\": ["
                        + customerId
                        + "], \"targetOwnerId\": "
                        + ownerUser
                        + "}"))
        .andExpect(status().isOk());

    // 归属者共享给 viewer
    String ownerToken = loginAndGetToken("shareowner", "Passw0rd!");
    String shareResp =
        mockMvc
            .perform(
                post("/api/v1/customer-shares")
                    .header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"customerId\": "
                            + customerId
                            + ", \"sharedToUserId\": "
                            + viewerUser
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long shareId = objectMapper.readTree(shareResp).path("data").path("shareId").asLong();

    // viewer 可见详情
    String viewerToken = loginAndGetToken("shareviewer", "Passw0rd!");
    mockMvc
        .perform(
            get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(viewerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("共享客户X"));

    // viewer 编辑 → 403（只读）
    mockMvc
        .perform(
            put("/api/v1/customers/{id}", customerId)
                .header("Authorization", bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权\", \"company\": \"越权公司\", \"version\": 0}"))
        .andExpect(status().isForbidden());

    // 取消共享 → viewer 不可见
    mockMvc
        .perform(
            delete("/api/v1/customer-shares/{id}", shareId)
                .header("Authorization", bearer(ownerToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(viewerToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("非管理员部门管理返回 403")
  void nonAdminDepartmentForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    long sales = createUser(adminToken, "deptsales");
    String salesToken = loginAndGetToken("deptsales", "Passw0rd!");

    mockMvc
        .perform(
            post("/api/v1/departments")
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权部门\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/v1/users/{id}/data-permission", sales)
                .header("Authorization", bearer(salesToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataScope\": \"ALL\"}"))
        .andExpect(status().isForbidden());
  }
}

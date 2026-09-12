package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 角色权限集成测试（028 T005）：CRUD + me 返回菜单/权限 + 无权限 403。 */
class RoleIT extends AbstractIntegrationTest {

  private String adminToken() throws Exception {
    return loginAndGetToken();
  }

  private long createRole(String token, String code, String name, String dataScope)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/roles")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\""
                            + code
                            + "\",\"name\":\""
                            + name
                            + "\",\"dataScope\":\""
                            + dataScope
                            + "\",\"menus\":[\"customers\",\"orders\"],\"permissions\":[\"customer:create\"]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("管理员创建角色并配置菜单/权限")
  void adminCreatesRole() throws Exception {
    String token = adminToken();

    // 角色码必须避开生产迁移预置的角色码（V75 起含 VIEWER 等 10 个）——重用预置码会撞唯一键得到
    // 409，而那不是本用例要验证的行为。
    long roleId = createRole(token, "IT_VIEWER", "只读查看", "SELF");

    mockMvc
        .perform(get("/api/v1/roles").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.code == 'IT_VIEWER')].menus[0]").value("customers"));
    org.assertj.core.api.Assertions.assertThat(roleId).isPositive();
  }

  @Test
  @DisplayName("me 返回菜单与操作权限（ADMIN 全量）")
  void meReturnsMenusAndPermissions() throws Exception {
    String token = adminToken();

    // 断言"ADMIN 拿到全量"而非某个具体数字：菜单目录随功能增加而增长（曾为 28，现已 55+），
    // 写死数字只会把用例变成一颗定时炸弹。基准取自字典接口本身——它与 /auth/me 的 ADMIN 分支
    // 读的是同一份目录，因此这里检验的是"兜底分支确实取到了全量"，而不是"目录恰好是 N 项"。
    List<String> expectedMenus = dictKeys(token, "/api/v1/roles/menu-tree", "key");
    List<String> expectedPermissions = dictKeys(token, "/api/v1/roles/permission-defs", "code");

    String meResp =
        mockMvc
            .perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
            .andExpect(jsonPath("$.data.menus").isArray())
            .andExpect(jsonPath("$.data.permissions").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode data = objectMapper.readTree(meResp).path("data");

    org.assertj.core.api.Assertions.assertThat(
            objectMapper.convertValue(data.path("menus"), new TypeReference<List<String>>() {}))
        .containsExactlyInAnyOrderElementsOf(expectedMenus);
    org.assertj.core.api.Assertions.assertThat(
            objectMapper.convertValue(
                data.path("permissions"), new TypeReference<List<String>>() {}))
        .containsExactlyInAnyOrderElementsOf(expectedPermissions);
  }

  /** 展开字典接口（分组 → children）的叶子键，顺序即接口返回顺序。 */
  private List<String> dictKeys(String token, String path, String field) throws Exception {
    String resp =
        mockMvc
            .perform(get(path).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> keys = new ArrayList<>();
    for (JsonNode group : objectMapper.readTree(resp).path("data")) {
      for (JsonNode child : group.path("children")) {
        keys.add(child.path(field).asText());
      }
    }
    org.assertj.core.api.Assertions.assertThat(keys).isNotEmpty();
    return keys;
  }

  @Test
  @DisplayName("内建角色不可删除")
  void builtInNotDeletable() throws Exception {
    String token = adminToken();
    JsonNode roles =
        objectMapper
            .readTree(
                mockMvc
                    .perform(get("/api/v1/roles").header("Authorization", bearer(token)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data")
            .path("items");
    long adminRoleId = 0;
    for (JsonNode r : roles) {
      if ("ADMIN".equals(r.path("code").asText())) {
        adminRoleId = r.path("id").asLong();
        break;
      }
    }

    mockMvc
        .perform(delete("/api/v1/roles/" + adminRoleId).header("Authorization", bearer(token)))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("无权限用户调删除客户 API 返回 403（SALES 无 customer:delete）")
  void noPermissionReturns403() throws Exception {
    String token = adminToken();
    // 创建 SALES 用户并登录
    String userResp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"permuser\",\"password\":\"pass1234\",\"displayName\":\"权限测试\",\"role\":\"SALES\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long userId = objectMapper.readTree(userResp).path("data").path("id").asLong();
    org.assertj.core.api.Assertions.assertThat(userId).isPositive();

    String salesLogin =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"permuser\",\"password\":\"pass1234\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String salesToken = objectMapper.readTree(salesLogin).path("data").path("accessToken").asText();

    // SALES 无 customer:delete → 删除客户 403
    mockMvc
        .perform(delete("/api/v1/customers/1").header("Authorization", bearer(salesToken)))
        .andExpect(status().isForbidden());
  }
}

package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.dto.personal.PersonalInfoResponse;
import com.crm.dto.personal.UpdateDisplayNameRequest;
import com.crm.entity.Department;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** PersonalCenterService 单元测试：个人信息/修改显示名。 */
@ExtendWith(MockitoExtension.class)
class PersonalCenterServiceTest {

  @Mock private UserMapper userMapper;
  @Mock private DepartmentMapper departmentMapper;

  private PersonalCenterService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    service = new PersonalCenterService(userMapper, departmentMapper);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private User sampleUser() {
    User u = new User();
    u.setId(1L);
    u.setUsername("admin");
    u.setDisplayName("管理员");
    u.setRole("ADMIN");
    u.setDepartmentId(10L);
    u.setDataScope("ALL");
    u.setEnabled(true);
    u.setLastLoginAt(LocalDateTime.now());
    u.setCreatedAt(LocalDateTime.now());
    u.setUpdatedAt(LocalDateTime.now());
    return u;
  }

  private Department sampleDept() {
    Department d = new Department();
    d.setId(10L);
    d.setName("技术部");
    return d;
  }

  @Test
  @DisplayName("获取个人信息：返回用户详情+部门名称")
  void getPersonalInfo_shouldReturnUserInfoWithDepartment() {
    // Given
    when(userMapper.selectById(1L)).thenReturn(sampleUser());
    when(departmentMapper.selectById(10L)).thenReturn(sampleDept());

    // When
    PersonalInfoResponse resp = service.getPersonalInfo(1L);

    // Then
    assertThat(resp).isNotNull();
    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getUsername()).isEqualTo("admin");
    assertThat(resp.getDisplayName()).isEqualTo("管理员");
    assertThat(resp.getRole()).isEqualTo("ADMIN");
    assertThat(resp.getDepartmentId()).isEqualTo(10L);
    assertThat(resp.getDataScope()).isEqualTo("ALL");
    assertThat(resp.getEnabled()).isTrue();
    assertThat(resp.getDepartmentName()).isEqualTo("技术部");
    assertThat(resp.getLastLoginAt()).isNotNull();
    assertThat(resp.getCreatedAt()).isNotNull();
    assertThat(resp.getPasswordUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("获取个人信息：用户不存在抛出异常")
  void getPersonalInfo_userNotFound_shouldThrow() {
    // Given
    when(userMapper.selectById(999L)).thenReturn(null);

    // When & Then
    assertThatThrownBy(() -> service.getPersonalInfo(999L))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("用户不存在");
  }

  @Test
  @DisplayName("获取个人信息：无部门时部门名称为 null")
  void getPersonalInfo_noDepartment_shouldReturnNullDepartmentName() {
    // Given
    User u = sampleUser();
    u.setDepartmentId(null);
    when(userMapper.selectById(1L)).thenReturn(u);

    // When
    PersonalInfoResponse resp = service.getPersonalInfo(1L);

    // Then
    assertThat(resp).isNotNull();
    assertThat(resp.getDepartmentName()).isNull();
    verify(departmentMapper, never()).selectById(any());
  }

  @Test
  @DisplayName("修改显示名：成功更新")
  void updateDisplayName_shouldUpdateSuccessfully() {
    // Given
    User u = sampleUser();
    when(userMapper.selectById(1L)).thenReturn(u);

    UpdateDisplayNameRequest req = new UpdateDisplayNameRequest();
    req.setDisplayName("新显示名");

    // When
    service.updateDisplayName(1L, req);

    // Then
    assertThat(u.getDisplayName()).isEqualTo("新显示名");
    verify(userMapper).updateById(u);
  }

  @Test
  @DisplayName("修改显示名：用户不存在抛出异常")
  void updateDisplayName_userNotFound_shouldThrow() {
    // Given
    when(userMapper.selectById(999L)).thenReturn(null);

    UpdateDisplayNameRequest req = new UpdateDisplayNameRequest();
    req.setDisplayName("新显示名");

    // When & Then
    assertThatThrownBy(() -> service.updateDisplayName(999L, req))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("用户不存在");

    verify(userMapper, never()).updateById(any());
  }

  @Test
  @DisplayName("修改显示名：自动去除前后空格")
  void updateDisplayName_shouldTrimWhitespace() {
    // Given
    User u = sampleUser();
    when(userMapper.selectById(1L)).thenReturn(u);

    UpdateDisplayNameRequest req = new UpdateDisplayNameRequest();
    req.setDisplayName("  带空格的显示名  ");

    // When
    service.updateDisplayName(1L, req);

    // Then
    assertThat(u.getDisplayName()).isEqualTo("带空格的显示名");
  }
}

package com.crm.service;

import com.crm.dto.personal.PersonalInfoResponse;
import com.crm.dto.personal.UpdateDisplayNameRequest;
import com.crm.entity.Department;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import org.springframework.stereotype.Service;

/** 个人中心服务。 */
@Service
public class PersonalCenterService {

  private final UserMapper userMapper;
  private final DepartmentMapper departmentMapper;

  public PersonalCenterService(UserMapper userMapper, DepartmentMapper departmentMapper) {
    this.userMapper = userMapper;
    this.departmentMapper = departmentMapper;
  }

  /** 获取当前登录用户的个人信息。 */
  public PersonalInfoResponse getPersonalInfo(Long userId) {
    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new RuntimeException("用户不存在");
    }

    PersonalInfoResponse response = new PersonalInfoResponse();
    response.setId(user.getId());
    response.setUsername(user.getUsername());
    response.setDisplayName(user.getDisplayName());
    response.setRole(user.getRole());
    response.setDepartmentId(user.getDepartmentId());
    response.setDataScope(user.getDataScope());
    response.setEnabled(user.getEnabled());
    response.setLastLoginAt(user.getLastLoginAt());
    response.setCreatedAt(user.getCreatedAt());
    // 使用 updated_at 作为密码修改时间的近似值
    response.setPasswordUpdatedAt(user.getUpdatedAt());

    // 查询部门名称
    if (user.getDepartmentId() != null) {
      Department department = departmentMapper.selectById(user.getDepartmentId());
      if (department != null) {
        response.setDepartmentName(department.getName());
      }
    }

    return response;
  }

  /** 修改当前用户的显示名。 */
  public void updateDisplayName(Long userId, UpdateDisplayNameRequest request) {
    User user = userMapper.selectById(userId);
    if (user == null) {
      throw new RuntimeException("用户不存在");
    }

    user.setDisplayName(request.getDisplayName().trim());
    userMapper.updateById(user);
  }
}

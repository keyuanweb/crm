package com.crm.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 初始化种子数据：默认管理员 admin/admin123（bcrypt，research.md R1）。幂等执行。 */
@Component
public class DataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  public DataInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    Long count =
        userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
    if (count == null || count == 0) {
      User admin = new User();
      admin.setUsername("admin");
      admin.setPasswordHash(passwordEncoder.encode("admin123"));
      admin.setDisplayName("系统管理员");
      admin.setRole("ADMIN");
      admin.setEnabled(true);
      userMapper.insert(admin);
      log.info("Seeded default admin user (admin/admin123)");
    }
  }
}

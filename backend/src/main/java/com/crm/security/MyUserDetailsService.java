package com.crm.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.User;
import com.crm.repository.UserMapper;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 认证用户加载（research.md R1）。 */
@Service
public class MyUserDetailsService implements UserDetailsService {

  private final UserMapper userMapper;

  public MyUserDetailsService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    if (user == null) {
      throw new UsernameNotFoundException("User not found: " + username);
    }
    return org.springframework.security.core.userdetails.User.builder()
        .username(user.getUsername())
        .password(user.getPasswordHash())
        .disabled(!Boolean.TRUE.equals(user.getEnabled()))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
        .build();
  }
}

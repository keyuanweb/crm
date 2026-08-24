package com.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/** CRM backend application entry point. */
@SpringBootApplication
@MapperScan("com.crm.repository")
@EnableAsync
public class CrmApplication {

  public static void main(String[] args) {
    SpringApplication.run(CrmApplication.class, args);
  }
}

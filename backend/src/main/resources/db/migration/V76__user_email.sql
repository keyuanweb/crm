-- V76: add email column to user table
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(100) NULL COMMENT '邮箱地址' AFTER `display_name`;

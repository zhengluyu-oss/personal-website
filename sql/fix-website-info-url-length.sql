-- OSS 完整 URL 超过原 varchar(100)，导致头像上传写库失败
ALTER TABLE `sys_website_info`
  MODIFY COLUMN `webmaster_avatar` varchar(512) NULL DEFAULT NULL COMMENT '站长头像',
  MODIFY COLUMN `webmaster_profile_background` varchar(512) NULL DEFAULT NULL COMMENT '站长资料卡背景图';

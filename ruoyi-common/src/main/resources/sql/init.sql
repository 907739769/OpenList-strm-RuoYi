-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (100, 'openlist-访问地址', 'openlist.server.url', 'http://xxx:5244', 'N', 'admin', '2025-07-16 11:35:53', 'admin', '2025-07-17 11:39:18', 'openlist的访问地址http://xxxx:5244');
INSERT INTO `sys_config` VALUES (101, 'openlist-api访问token', 'openlist.server.token', 'xxx', 'N', 'admin', '2025-07-16 11:38:39', 'admin', '2025-07-17 11:39:24', 'openlist的api访问token');
INSERT INTO `sys_config` VALUES (102, 'openlist复制的最小文件', 'openlist.copy.minfilesize', '10', 'N', 'admin', '2025-07-17 10:43:53', 'admin', '2025-07-17 11:55:27', '单位是MB');
INSERT INTO `sys_config` VALUES (103, 'openlist复制完文件生成strm', 'openlist.copy.strm', '1', 'N', 'admin', '2025-07-17 10:44:48', '', NULL, NULL);

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (100, 0, '0', '若依科技', 0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:16', '', NULL);
INSERT INTO `sys_dept` VALUES (101, 100, '0,100', '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:16', '', NULL);
INSERT INTO `sys_dept` VALUES (102, 100, '0,100', '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:16', '', NULL);
INSERT INTO `sys_dept` VALUES (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:16', '', NULL);
INSERT INTO `sys_dept` VALUES (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:16', '', NULL);
INSERT INTO `sys_dept` VALUES (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:17', '', NULL);
INSERT INTO `sys_dept` VALUES (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:17', '', NULL);
INSERT INTO `sys_dept` VALUES (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:17', '', NULL);
INSERT INTO `sys_dept` VALUES (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:17', '', NULL);
INSERT INTO `sys_dept` VALUES (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2025-07-15 11:53:17', '', NULL);

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2025-07-15 11:53:55', '', NULL, '性别男');
INSERT INTO `sys_dict_data` VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-07-15 11:53:55', '', NULL, '性别女');
INSERT INTO `sys_dict_data` VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-07-15 11:53:55', '', NULL, '性别未知');
INSERT INTO `sys_dict_data` VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '显示菜单');
INSERT INTO `sys_dict_data` VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '隐藏菜单');
INSERT INTO `sys_dict_data` VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:56', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2025-07-15 11:53:57', '', NULL, '默认分组');
INSERT INTO `sys_dict_data` VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2025-07-15 11:53:57', '', NULL, '系统分组');
INSERT INTO `sys_dict_data` VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2025-07-15 11:53:57', '', NULL, '系统默认是');
INSERT INTO `sys_dict_data` VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:57', '', NULL, '系统默认否');
INSERT INTO `sys_dict_data` VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2025-07-15 11:53:57', '', NULL, '通知');
INSERT INTO `sys_dict_data` VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '公告');
INSERT INTO `sys_dict_data` VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '关闭状态');
INSERT INTO `sys_dict_data` VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '其他操作');
INSERT INTO `sys_dict_data` VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '新增操作');
INSERT INTO `sys_dict_data` VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-07-15 11:53:58', '', NULL, '修改操作');
INSERT INTO `sys_dict_data` VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '删除操作');
INSERT INTO `sys_dict_data` VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '授权操作');
INSERT INTO `sys_dict_data` VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '导出操作');
INSERT INTO `sys_dict_data` VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '导入操作');
INSERT INTO `sys_dict_data` VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '强退操作');
INSERT INTO `sys_dict_data` VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-07-15 11:53:59', '', NULL, '生成操作');
INSERT INTO `sys_dict_data` VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:54:00', '', NULL, '清空操作');
INSERT INTO `sys_dict_data` VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2025-07-15 11:54:00', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2025-07-15 11:54:00', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (100, 1, '处理中', '1', 'openlist_copy_status', '', 'info', 'Y', '0', 'admin', '2025-07-15 13:33:27', 'admin', '2025-07-17 14:14:58', 'openlist复制任务处理中');
INSERT INTO `sys_dict_data` VALUES (101, 2, '失败', '2', 'openlist_copy_status', '', 'danger', 'Y', '0', 'admin', '2025-07-15 13:33:59', 'admin', '2025-07-17 12:04:39', 'openlist复制任务失败');
INSERT INTO `sys_dict_data` VALUES (102, 3, '成功', '3', 'openlist_copy_status', '', 'primary', 'Y', '0', 'admin', '2025-07-15 13:34:19', 'admin', '2025-07-17 14:14:27', 'openlist复制任务成功');
INSERT INTO `sys_dict_data` VALUES (103, 4, '未知', '4', 'openlist_copy_status', '', 'warning', 'Y', '0', 'admin', '2025-07-15 13:34:58', 'admin', '2025-07-17 12:04:52', 'openlist复制任务状态未知');
INSERT INTO `sys_dict_data` VALUES (104, 2, '成功', '1', 'openlist_strm_status', '', 'success', 'Y', '0', 'admin', '2025-07-16 13:34:44', 'admin', '2025-07-17 12:05:32', '');
INSERT INTO `sys_dict_data` VALUES (105, 1, '失败', '0', 'openlist_strm_status', '', 'danger', 'Y', '0', 'admin', '2025-07-16 13:34:57', 'admin', '2025-07-17 12:05:28', '');
INSERT INTO `sys_dict_data` VALUES (106, 1, 'mp4', 'mp4', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:52:11', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (107, 2, 'mkv', 'mkv', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:52:19', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (108, 3, 'avi', 'avi', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:52:30', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (109, 4, 'mov', 'mov', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:52:42', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (110, 5, 'rmvb', 'rmvb', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:52:52', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (111, 6, 'flv', 'flv', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:53:09', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (112, 7, 'webm', 'webm', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:53:25', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (113, 8, 'm3u8', 'm3u8', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:53:32', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (114, 9, 'wmv', 'wmv', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:53:50', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (115, 10, 'iso', 'iso', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:53:59', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (116, 11, 'ts', 'ts', 'openlist_video_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:54:08', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (117, 1, 'ass', 'ass', 'openlist_srt_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:54:51', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (118, 2, 'srt', 'srt', 'openlist_srt_type', NULL, NULL, 'Y', '0', 'admin', '2025-07-16 13:54:59', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (119, 2, '停用', '0', 'openlist_copy_task_status', '', 'danger', 'N', '0', 'admin', '2025-07-17 10:28:59', 'admin', '2025-07-17 12:06:08', '');
INSERT INTO `sys_dict_data` VALUES (120, 1, '启用', '1', 'openlist_copy_task_status', '', 'primary', 'Y', '0', 'admin', '2025-07-17 10:29:11', 'admin', '2025-07-17 12:06:14', '');

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2025-07-15 11:53:53', '', NULL, '用户性别列表');
INSERT INTO `sys_dict_type` VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2025-07-15 11:53:53', '', NULL, '菜单状态列表');
INSERT INTO `sys_dict_type` VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2025-07-15 11:53:53', '', NULL, '系统开关列表');
INSERT INTO `sys_dict_type` VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '任务状态列表');
INSERT INTO `sys_dict_type` VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '任务分组列表');
INSERT INTO `sys_dict_type` VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '系统是否列表');
INSERT INTO `sys_dict_type` VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '通知类型列表');
INSERT INTO `sys_dict_type` VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '通知状态列表');
INSERT INTO `sys_dict_type` VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2025-07-15 11:53:54', '', NULL, '操作类型列表');
INSERT INTO `sys_dict_type` VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2025-07-15 11:53:55', '', NULL, '登录状态列表');
INSERT INTO `sys_dict_type` VALUES (100, 'openlist复制任务状态', 'openlist_copy_status', '0', 'admin', '2025-07-15 13:31:30', '', NULL, 'openlist复制任务状态 1-处理中 2-失败 3-成功 4-未知');
INSERT INTO `sys_dict_type` VALUES (101, 'openlist strm任务状态', 'openlist_strm_status', '0', 'admin', '2025-07-16 13:34:16', '', NULL, 'openlist strm任务状态0失败1成功');
INSERT INTO `sys_dict_type` VALUES (102, 'openlist视频格式', 'openlist_video_type', '0', 'admin', '2025-07-16 13:51:22', '', NULL, NULL);
INSERT INTO `sys_dict_type` VALUES (103, 'openlist字幕格式', 'openlist_srt_type', '0', 'admin', '2025-07-16 13:54:41', '', NULL, NULL);
INSERT INTO `sys_dict_type` VALUES (104, 'openlist同步文件任务状态', 'openlist_copy_task_status', '0', 'admin', '2025-07-17 10:28:35', '', NULL, 'openlist同步文件任务状态0-停用1-启用');

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO `sys_job` VALUES (100, 'openliststrm-复制任务', 'DEFAULT', 'openListStrmTask.copy()', '0 0 3 * * ?', '3', '1', '1', 'admin', '2025-07-17 11:38:05', '', NULL, '');
INSERT INTO `sys_job` VALUES (101, 'openliststrm-strm任务', 'DEFAULT', 'openListStrmTask.strm()', '0 0 5 * * ?', '3', '1', '1', 'admin', '2025-07-18 11:44:23', '', NULL, '');

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 1, '#', '', 'M', '0', '1', '', 'fa fa-gear', 'admin', '2025-07-15 11:53:20', '', NULL, '系统管理目录');
INSERT INTO `sys_menu` VALUES (2, '系统监控', 0, 2, '#', '', 'M', '0', '1', '', 'fa fa-video-camera', 'admin', '2025-07-15 11:53:21', '', NULL, '系统监控目录');
INSERT INTO `sys_menu` VALUES (100, '用户管理', 1, 1, '/system/user', '', 'C', '0', '1', 'system:user:view', 'fa fa-user-o', 'admin', '2025-07-15 11:53:21', '', NULL, '用户管理菜单');
INSERT INTO `sys_menu` VALUES (101, '角色管理', 1, 2, '/system/role', '', 'C', '0', '1', 'system:role:view', 'fa fa-user-secret', 'admin', '2025-07-15 11:53:21', '', NULL, '角色管理菜单');
INSERT INTO `sys_menu` VALUES (102, '菜单管理', 1, 3, '/system/menu', '', 'C', '0', '1', 'system:menu:view', 'fa fa-th-list', 'admin', '2025-07-15 11:53:21', '', NULL, '菜单管理菜单');
INSERT INTO `sys_menu` VALUES (103, '部门管理', 1, 4, '/system/dept', '', 'C', '0', '1', 'system:dept:view', 'fa fa-outdent', 'admin', '2025-07-15 11:53:22', '', NULL, '部门管理菜单');
INSERT INTO `sys_menu` VALUES (104, '岗位管理', 1, 5, '/system/post', '', 'C', '0', '1', 'system:post:view', 'fa fa-address-card-o', 'admin', '2025-07-15 11:53:22', '', NULL, '岗位管理菜单');
INSERT INTO `sys_menu` VALUES (105, '字典管理', 1, 6, '/system/dict', '', 'C', '0', '1', 'system:dict:view', 'fa fa-bookmark-o', 'admin', '2025-07-15 11:53:22', '', NULL, '字典管理菜单');
INSERT INTO `sys_menu` VALUES (106, '参数设置', 1, 7, '/system/config', '', 'C', '0', '1', 'system:config:view', 'fa fa-sun-o', 'admin', '2025-07-15 11:53:22', '', NULL, '参数设置菜单');
INSERT INTO `sys_menu` VALUES (108, '日志管理', 1, 9, '#', '', 'M', '0', '1', '', 'fa fa-pencil-square-o', 'admin', '2025-07-15 11:53:22', '', NULL, '日志管理菜单');
INSERT INTO `sys_menu` VALUES (109, '在线用户', 2, 1, '/monitor/online', '', 'C', '0', '1', 'monitor:online:view', 'fa fa-user-circle', 'admin', '2025-07-15 11:53:23', '', NULL, '在线用户菜单');
INSERT INTO `sys_menu` VALUES (110, '定时任务', 2, 2, '/monitor/job', '', 'C', '0', '1', 'monitor:job:view', 'fa fa-tasks', 'admin', '2025-07-15 11:53:23', '', NULL, '定时任务菜单');
INSERT INTO `sys_menu` VALUES (500, '操作日志', 108, 1, '/monitor/operlog', '', 'C', '0', '1', 'monitor:operlog:view', 'fa fa-address-book', 'admin', '2025-07-15 11:53:24', '', NULL, '操作日志菜单');
INSERT INTO `sys_menu` VALUES (501, '登录日志', 108, 2, '/monitor/logininfor', '', 'C', '0', '1', 'monitor:logininfor:view', 'fa fa-file-image-o', 'admin', '2025-07-15 11:53:24', '', NULL, '登录日志菜单');
INSERT INTO `sys_menu` VALUES (1000, '用户查询', 100, 1, '#', '', 'F', '0', '1', 'system:user:list', '#', 'admin', '2025-07-15 11:53:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1001, '用户新增', 100, 2, '#', '', 'F', '0', '1', 'system:user:add', '#', 'admin', '2025-07-15 11:53:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1002, '用户修改', 100, 3, '#', '', 'F', '0', '1', 'system:user:edit', '#', 'admin', '2025-07-15 11:53:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1003, '用户删除', 100, 4, '#', '', 'F', '0', '1', 'system:user:remove', '#', 'admin', '2025-07-15 11:53:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1004, '用户导出', 100, 5, '#', '', 'F', '0', '1', 'system:user:export', '#', 'admin', '2025-07-15 11:53:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1005, '用户导入', 100, 6, '#', '', 'F', '0', '1', 'system:user:import', '#', 'admin', '2025-07-15 11:53:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1006, '重置密码', 100, 7, '#', '', 'F', '0', '1', 'system:user:resetPwd', '#', 'admin', '2025-07-15 11:53:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1007, '角色查询', 101, 1, '#', '', 'F', '0', '1', 'system:role:list', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1008, '角色新增', 101, 2, '#', '', 'F', '0', '1', 'system:role:add', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1009, '角色修改', 101, 3, '#', '', 'F', '0', '1', 'system:role:edit', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1010, '角色删除', 101, 4, '#', '', 'F', '0', '1', 'system:role:remove', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1011, '角色导出', 101, 5, '#', '', 'F', '0', '1', 'system:role:export', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1012, '菜单查询', 102, 1, '#', '', 'F', '0', '1', 'system:menu:list', '#', 'admin', '2025-07-15 11:53:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1013, '菜单新增', 102, 2, '#', '', 'F', '0', '1', 'system:menu:add', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1014, '菜单修改', 102, 3, '#', '', 'F', '0', '1', 'system:menu:edit', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1015, '菜单删除', 102, 4, '#', '', 'F', '0', '1', 'system:menu:remove', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1016, '部门查询', 103, 1, '#', '', 'F', '0', '1', 'system:dept:list', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1017, '部门新增', 103, 2, '#', '', 'F', '0', '1', 'system:dept:add', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1018, '部门修改', 103, 3, '#', '', 'F', '0', '1', 'system:dept:edit', '#', 'admin', '2025-07-15 11:53:27', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1019, '部门删除', 103, 4, '#', '', 'F', '0', '1', 'system:dept:remove', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1020, '岗位查询', 104, 1, '#', '', 'F', '0', '1', 'system:post:list', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1021, '岗位新增', 104, 2, '#', '', 'F', '0', '1', 'system:post:add', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1022, '岗位修改', 104, 3, '#', '', 'F', '0', '1', 'system:post:edit', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1023, '岗位删除', 104, 4, '#', '', 'F', '0', '1', 'system:post:remove', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1024, '岗位导出', 104, 5, '#', '', 'F', '0', '1', 'system:post:export', '#', 'admin', '2025-07-15 11:53:28', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1025, '字典查询', 105, 1, '#', '', 'F', '0', '1', 'system:dict:list', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1026, '字典新增', 105, 2, '#', '', 'F', '0', '1', 'system:dict:add', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1027, '字典修改', 105, 3, '#', '', 'F', '0', '1', 'system:dict:edit', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1028, '字典删除', 105, 4, '#', '', 'F', '0', '1', 'system:dict:remove', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1029, '字典导出', 105, 5, '#', '', 'F', '0', '1', 'system:dict:export', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1030, '参数查询', 106, 1, '#', '', 'F', '0', '1', 'system:config:list', '#', 'admin', '2025-07-15 11:53:29', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1031, '参数新增', 106, 2, '#', '', 'F', '0', '1', 'system:config:add', '#', 'admin', '2025-07-15 11:53:30', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1032, '参数修改', 106, 3, '#', '', 'F', '0', '1', 'system:config:edit', '#', 'admin', '2025-07-15 11:53:30', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1033, '参数删除', 106, 4, '#', '', 'F', '0', '1', 'system:config:remove', '#', 'admin', '2025-07-15 11:53:30', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1034, '参数导出', 106, 5, '#', '', 'F', '0', '1', 'system:config:export', '#', 'admin', '2025-07-15 11:53:30', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1039, '操作查询', 500, 1, '#', '', 'F', '0', '1', 'monitor:operlog:list', '#', 'admin', '2025-07-15 11:53:31', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1040, '操作删除', 500, 2, '#', '', 'F', '0', '1', 'monitor:operlog:remove', '#', 'admin', '2025-07-15 11:53:31', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1041, '详细信息', 500, 3, '#', '', 'F', '0', '1', 'monitor:operlog:detail', '#', 'admin', '2025-07-15 11:53:31', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1042, '日志导出', 500, 4, '#', '', 'F', '0', '1', 'monitor:operlog:export', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1043, '登录查询', 501, 1, '#', '', 'F', '0', '1', 'monitor:logininfor:list', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1044, '登录删除', 501, 2, '#', '', 'F', '0', '1', 'monitor:logininfor:remove', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1045, '日志导出', 501, 3, '#', '', 'F', '0', '1', 'monitor:logininfor:export', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1046, '账户解锁', 501, 4, '#', '', 'F', '0', '1', 'monitor:logininfor:unlock', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1047, '在线查询', 109, 1, '#', '', 'F', '0', '1', 'monitor:online:list', '#', 'admin', '2025-07-15 11:53:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1048, '批量强退', 109, 2, '#', '', 'F', '0', '1', 'monitor:online:batchForceLogout', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1049, '单条强退', 109, 3, '#', '', 'F', '0', '1', 'monitor:online:forceLogout', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1050, '任务查询', 110, 1, '#', '', 'F', '0', '1', 'monitor:job:list', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1051, '任务新增', 110, 2, '#', '', 'F', '0', '1', 'monitor:job:add', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1052, '任务修改', 110, 3, '#', '', 'F', '0', '1', 'monitor:job:edit', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1053, '任务删除', 110, 4, '#', '', 'F', '0', '1', 'monitor:job:remove', '#', 'admin', '2025-07-15 11:53:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1054, '状态修改', 110, 5, '#', '', 'F', '0', '1', 'monitor:job:changeStatus', '#', 'admin', '2025-07-15 11:53:34', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1055, '任务详细', 110, 6, '#', '', 'F', '0', '1', 'monitor:job:detail', '#', 'admin', '2025-07-15 11:53:34', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1056, '任务导出', 110, 7, '#', '', 'F', '0', '1', 'monitor:job:export', '#', 'admin', '2025-07-15 11:53:34', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2006, 'OpenListStrm', 0, 4, '#', 'menuItem', 'M', '0', '1', NULL, 'fa fa-heart', 'admin', '2025-07-15 13:46:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2013, '同步任务记录', 2006, 1, '/openliststrm/copy', 'menuItem', 'C', '0', '1', 'openliststrm:copy:view', '#', 'admin', '2025-07-16 06:17:49', 'admin', '2025-07-17 11:20:55', 'openlist的文件同步复制任务菜单');
INSERT INTO `sys_menu` VALUES (2014, 'openlist的文件同步复制任务查询', 2013, 1, '#', '', 'F', '0', '1', 'openliststrm:copy:list', '#', 'admin', '2025-07-16 06:17:49', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2015, 'openlist的文件同步复制任务新增', 2013, 2, '#', '', 'F', '0', '1', 'openliststrm:copy:add', '#', 'admin', '2025-07-16 06:17:50', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2016, 'openlist的文件同步复制任务修改', 2013, 3, '#', '', 'F', '0', '1', 'openliststrm:copy:edit', '#', 'admin', '2025-07-16 06:17:50', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2017, 'openlist的文件同步复制任务删除', 2013, 4, '#', '', 'F', '0', '1', 'openliststrm:copy:remove', '#', 'admin', '2025-07-16 06:17:50', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2018, 'openlist的文件同步复制任务导出', 2013, 5, '#', '', 'F', '0', '1', 'openliststrm:copy:export', '#', 'admin', '2025-07-16 06:17:51', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2019, 'STRM生成记录', 2006, 1, '/openliststrm/strm', 'menuItem', 'C', '0', '1', 'openliststrm:strm:view', '#', 'admin', '2025-07-16 08:19:31', 'admin', '2025-07-17 11:21:20', 'strm生成菜单');
INSERT INTO `sys_menu` VALUES (2020, 'strm生成查询', 2019, 1, '#', '', 'F', '0', '1', 'openliststrm:strm:list', '#', 'admin', '2025-07-16 08:19:31', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2021, 'strm生成新增', 2019, 2, '#', '', 'F', '0', '1', 'openliststrm:strm:add', '#', 'admin', '2025-07-16 08:19:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2022, 'strm生成修改', 2019, 3, '#', '', 'F', '0', '1', 'openliststrm:strm:edit', '#', 'admin', '2025-07-16 08:19:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2023, 'strm生成删除', 2019, 4, '#', '', 'F', '0', '1', 'openliststrm:strm:remove', '#', 'admin', '2025-07-16 08:19:32', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2024, 'strm生成导出', 2019, 5, '#', '', 'F', '0', '1', 'openliststrm:strm:export', '#', 'admin', '2025-07-16 08:19:33', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2025, '同步任务配置', 2006, 1, '/openliststrm/task', 'menuItem', 'C', '0', '1', 'openliststrm:task:view', '#', 'admin', '2025-07-17 10:30:19', 'admin', '2025-07-17 11:21:33', '文件同步任务菜单');
INSERT INTO `sys_menu` VALUES (2026, '文件同步任务查询', 2025, 1, '#', '', 'F', '0', '1', 'openliststrm:task:list', '#', 'admin', '2025-07-17 10:30:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2027, '文件同步任务新增', 2025, 2, '#', '', 'F', '0', '1', 'openliststrm:task:add', '#', 'admin', '2025-07-17 10:30:20', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2028, '文件同步任务修改', 2025, 3, '#', '', 'F', '0', '1', 'openliststrm:task:edit', '#', 'admin', '2025-07-17 10:30:20', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2029, '文件同步任务删除', 2025, 4, '#', '', 'F', '0', '1', 'openliststrm:task:remove', '#', 'admin', '2025-07-17 10:30:20', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2030, '文件同步任务导出', 2025, 5, '#', '', 'F', '0', '1', 'openliststrm:task:export', '#', 'admin', '2025-07-17 10:30:21', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2037, 'strm任务配置', 2006, 1, '/openliststrm/strm_task', '', 'C', '0', '1', 'openliststrm:strm_task:view', '#', 'admin', '2025-07-18 11:36:14', '', NULL, 'strm任务配置菜单');
INSERT INTO `sys_menu` VALUES (2038, 'strm任务配置查询', 2037, 1, '#', '', 'F', '0', '1', 'openliststrm:strm_task:list', '#', 'admin', '2025-07-18 11:36:14', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2039, 'strm任务配置新增', 2037, 2, '#', '', 'F', '0', '1', 'openliststrm:strm_task:add', '#', 'admin', '2025-07-18 11:36:14', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2040, 'strm任务配置修改', 2037, 3, '#', '', 'F', '0', '1', 'openliststrm:strm_task:edit', '#', 'admin', '2025-07-18 11:36:15', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2041, 'strm任务配置删除', 2037, 4, '#', '', 'F', '0', '1', 'openliststrm:strm_task:remove', '#', 'admin', '2025-07-18 11:36:15', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2042, 'strm任务配置导出', 2037, 5, '#', '', 'F', '0', '1', 'openliststrm:strm_task:export', '#', 'admin', '2025-07-18 11:36:15', '', NULL, '');

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2025-07-15 11:53:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2025-07-15 11:53:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2025-07-15 11:53:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2025-07-15 11:53:19', '', NULL, '');

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', '0', '0', 'admin', '2025-07-15 11:53:20', '', NULL, '超级管理员');
INSERT INTO `sys_role` VALUES (2, '普通角色', 'common', 2, '2', '0', '0', 'admin', '2025-07-15 11:53:20', 'admin', '2025-07-15 12:26:43', '普通角色');

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO `sys_role_dept` VALUES (2, 100);
INSERT INTO `sys_role_dept` VALUES (2, 101);
INSERT INTO `sys_role_dept` VALUES (2, 105);

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (2, 1);
INSERT INTO `sys_role_menu` VALUES (2, 2);
INSERT INTO `sys_role_menu` VALUES (2, 100);
INSERT INTO `sys_role_menu` VALUES (2, 101);
INSERT INTO `sys_role_menu` VALUES (2, 102);
INSERT INTO `sys_role_menu` VALUES (2, 103);
INSERT INTO `sys_role_menu` VALUES (2, 104);
INSERT INTO `sys_role_menu` VALUES (2, 105);
INSERT INTO `sys_role_menu` VALUES (2, 106);
INSERT INTO `sys_role_menu` VALUES (2, 108);
INSERT INTO `sys_role_menu` VALUES (2, 109);
INSERT INTO `sys_role_menu` VALUES (2, 110);
INSERT INTO `sys_role_menu` VALUES (2, 500);
INSERT INTO `sys_role_menu` VALUES (2, 501);
INSERT INTO `sys_role_menu` VALUES (2, 1000);
INSERT INTO `sys_role_menu` VALUES (2, 1001);
INSERT INTO `sys_role_menu` VALUES (2, 1002);
INSERT INTO `sys_role_menu` VALUES (2, 1003);
INSERT INTO `sys_role_menu` VALUES (2, 1004);
INSERT INTO `sys_role_menu` VALUES (2, 1005);
INSERT INTO `sys_role_menu` VALUES (2, 1006);
INSERT INTO `sys_role_menu` VALUES (2, 1007);
INSERT INTO `sys_role_menu` VALUES (2, 1008);
INSERT INTO `sys_role_menu` VALUES (2, 1009);
INSERT INTO `sys_role_menu` VALUES (2, 1010);
INSERT INTO `sys_role_menu` VALUES (2, 1011);
INSERT INTO `sys_role_menu` VALUES (2, 1012);
INSERT INTO `sys_role_menu` VALUES (2, 1013);
INSERT INTO `sys_role_menu` VALUES (2, 1014);
INSERT INTO `sys_role_menu` VALUES (2, 1015);
INSERT INTO `sys_role_menu` VALUES (2, 1016);
INSERT INTO `sys_role_menu` VALUES (2, 1017);
INSERT INTO `sys_role_menu` VALUES (2, 1018);
INSERT INTO `sys_role_menu` VALUES (2, 1019);
INSERT INTO `sys_role_menu` VALUES (2, 1020);
INSERT INTO `sys_role_menu` VALUES (2, 1021);
INSERT INTO `sys_role_menu` VALUES (2, 1022);
INSERT INTO `sys_role_menu` VALUES (2, 1023);
INSERT INTO `sys_role_menu` VALUES (2, 1024);
INSERT INTO `sys_role_menu` VALUES (2, 1025);
INSERT INTO `sys_role_menu` VALUES (2, 1026);
INSERT INTO `sys_role_menu` VALUES (2, 1027);
INSERT INTO `sys_role_menu` VALUES (2, 1028);
INSERT INTO `sys_role_menu` VALUES (2, 1029);
INSERT INTO `sys_role_menu` VALUES (2, 1030);
INSERT INTO `sys_role_menu` VALUES (2, 1031);
INSERT INTO `sys_role_menu` VALUES (2, 1032);
INSERT INTO `sys_role_menu` VALUES (2, 1033);
INSERT INTO `sys_role_menu` VALUES (2, 1034);
INSERT INTO `sys_role_menu` VALUES (2, 1039);
INSERT INTO `sys_role_menu` VALUES (2, 1040);
INSERT INTO `sys_role_menu` VALUES (2, 1041);
INSERT INTO `sys_role_menu` VALUES (2, 1042);
INSERT INTO `sys_role_menu` VALUES (2, 1043);
INSERT INTO `sys_role_menu` VALUES (2, 1044);
INSERT INTO `sys_role_menu` VALUES (2, 1045);
INSERT INTO `sys_role_menu` VALUES (2, 1046);
INSERT INTO `sys_role_menu` VALUES (2, 1047);
INSERT INTO `sys_role_menu` VALUES (2, 1048);
INSERT INTO `sys_role_menu` VALUES (2, 1049);
INSERT INTO `sys_role_menu` VALUES (2, 1050);
INSERT INTO `sys_role_menu` VALUES (2, 1051);
INSERT INTO `sys_role_menu` VALUES (2, 1052);
INSERT INTO `sys_role_menu` VALUES (2, 1053);
INSERT INTO `sys_role_menu` VALUES (2, 1054);
INSERT INTO `sys_role_menu` VALUES (2, 1055);
INSERT INTO `sys_role_menu` VALUES (2, 1056);

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 103, 'admin', 'admin', '00', 'admin@163.com', '18888888888', '0', '', '0ecec3cd5cf3ef3e6e5c8b751278c9d9', '3fbab7', '0', '0', '127.0.0.1', '2025-07-19 09:28:11', '2025-07-18 20:27:17', 'admin', '2025-07-15 11:53:18', '', '2025-07-19 01:28:11', '管理员');

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO `sys_user_post` VALUES (1, 1);
INSERT INTO `sys_user_post` VALUES (2, 2);

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);


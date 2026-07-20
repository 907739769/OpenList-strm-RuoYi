-- ----------------------------
-- 20260720: 重命名文件名模板 + 分类规则可视化配置
-- 分类规则原来硬编码在 MediaRenameProcessor.defaultRules()，本次挪到数据库表，
-- 种子数据与原硬编码规则完全一致（电影3条 + 剧集9条，剧集含8条条件规则+1条兜底）。
-- 文件名模板原来硬编码为 DEFAULT_FILENAME_TEMPLATE 常量，本次挪到 sys_config，
-- 用 INSERT ... WHERE NOT EXISTS 保证幂等，与 20260718-add-openlist-configs.sql 手法一致。
-- ----------------------------

CREATE TABLE `rename_category_rule` (
    `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `media_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '媒体类型 movie/tv',
    `seq` int(10) NOT NULL DEFAULT 0 COMMENT '排序序号，越小优先级越高，同media_type下最大seq为兜底规则',
    `genre_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔的TMDB genre id，空表示不限',
    `original_languages` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔语言码，空表示不限',
    `origin_countries` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '逗号分隔国家码，空表示不限',
    `target_dir` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '命中后的目标目录名，同时兼作展示名',
    `is_fallback` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否兜底规则 0-否 1-是，每个media_type有且只有一条，且seq最大',
    `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_media_type_seq`(`media_type`, `seq`) USING BTREE
) COMMENT = '重命名分类规则配置';

-- ----------------------------
-- 种子数据：电影 3 条（对应原 defaultRules() 的 movie 列表）
-- ----------------------------
INSERT INTO `rename_category_rule` (`media_type`, `seq`, `genre_ids`, `original_languages`, `origin_countries`, `target_dir`, `is_fallback`, `create_time`) VALUES
('movie', 0, '16', NULL, NULL, '动画电影', '0', '2026-07-20 00:00:00'),
('movie', 1, NULL, 'zh,cn,bo,za', NULL, '华语电影', '0', '2026-07-20 00:00:00'),
('movie', 2, NULL, NULL, NULL, '外语电影', '1', '2026-07-20 00:00:00');

-- ----------------------------
-- 种子数据：电视剧 9 条（对应原 defaultRules() 的 tv 列表，8条条件规则 + 1条兜底）
-- ----------------------------
INSERT INTO `rename_category_rule` (`media_type`, `seq`, `genre_ids`, `original_languages`, `origin_countries`, `target_dir`, `is_fallback`, `create_time`) VALUES
('tv', 0, '16', NULL, 'CN,TW,HK', '国漫', '0', '2026-07-20 00:00:00'),
('tv', 1, '16', NULL, 'JP', '日番', '0', '2026-07-20 00:00:00'),
('tv', 2, '99', NULL, NULL, '纪录片', '0', '2026-07-20 00:00:00'),
('tv', 3, '10762', NULL, NULL, '儿童', '0', '2026-07-20 00:00:00'),
('tv', 4, '10764,10767', NULL, NULL, '综艺', '0', '2026-07-20 00:00:00'),
('tv', 5, NULL, NULL, 'CN,TW,HK', '国产剧', '0', '2026-07-20 00:00:00'),
('tv', 6, NULL, NULL, 'US,FR,GB,DE,ES,IT,NL,PT,RU,UK', '欧美剧', '0', '2026-07-20 00:00:00'),
('tv', 7, NULL, NULL, 'JP,KP,KR,TH,IN,SG', '日韩剧', '0', '2026-07-20 00:00:00'),
('tv', 8, NULL, NULL, NULL, '未分类', '1', '2026-07-20 00:00:00');

-- ----------------------------
-- 文件名模板默认值种子（与原 DEFAULT_FILENAME_TEMPLATE 常量内容完全一致）
-- ----------------------------
INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `remark`)
SELECT '重命名文件名模板', 'rename.filename.template', '{{ title }} {% if year %} ({{ year }}) {% endif %}/{% if season %}Season {{ season }}/{% endif %}{{ title }} {% if year and not season %} ({{ year }}) {% endif %}{% if season %}S{{ season }}{% endif %}{% if episode %}E{{ episode }}{% endif %}{% if resolution %} - {{ resolution }}{% endif %}{% if source %}.{{ source }}{% endif %}{% if videoCodec %}.{{ videoCodec }}{% endif %}{% if audioCodec %}.{{ audioCodec }}{% endif %}{% if tags is not empty %}.{{ tags|join(\'.\') }}{% endif %}{% if releaseGroup %}-{{ releaseGroup }}{% endif %}.{{ extension }}', 'N', 'admin', '2026-07-20 00:00:00', '重命名文件名模板（Pebble语法），可在"重命名规则设置"页面修改'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'rename.filename.template');

-- ----------------------------
-- 菜单：挂在 OpenListStrm(2006) 下
-- ----------------------------
INSERT INTO `sys_menu`(`menu_id`, `menu_name`, `parent_id`, `order_num`, `url`, `target`, `menu_type`, `visible`, `is_refresh`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2060, '重命名规则设置', 2006, 8, '/openlist/renameConfig', '', 'C', '0', '1', 'openliststrm:renameConfig:view', '#', 'admin', '2026-07-20 00:00:00', '', NULL, '重命名文件名模板与分类规则可视化配置菜单');

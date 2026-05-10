-- 清理未使用的数据库表
-- gen_table / gen_table_column: RuoYi 代码生成器表，项目已不使用
-- sys_notice: 通知公告表，项目已不使用

DROP TABLE IF EXISTS `gen_table_column`;
DROP TABLE IF EXISTS `gen_table`;
DROP TABLE IF EXISTS `sys_notice`;

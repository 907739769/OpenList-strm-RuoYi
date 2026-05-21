-- P0 并发修复：为 strm 和 copy 表添加唯一索引
-- 替代原有的静态 synchronized 锁，从数据库层面保证并发安全

-- openlist_strm 表唯一索引：strm_path + strm_file_name
ALTER TABLE `openlist_strm`
    ADD UNIQUE INDEX `uk_strm_path_file_name` (`strm_path`(191), `strm_file_name`(191));

-- openlist_copy 表唯一索引：copy_src_path + copy_src_file_name
ALTER TABLE `openlist_copy`
    ADD UNIQUE INDEX `uk_copy_src_path_file_name` (`copy_src_path`(191), `copy_src_file_name`(191));

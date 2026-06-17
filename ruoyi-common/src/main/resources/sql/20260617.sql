ALTER TABLE `openlist_strm`
    drop INDEX `uk_strm_path_file_name`;
ALTER TABLE `openlist_copy`
    drop INDEX `uk_copy_src_path_file_name`;

-- openlist_strm 表唯一索引：strm_path + strm_file_name
ALTER TABLE `openlist_strm`
    ADD UNIQUE INDEX `uk_strm_path_file_name` (`strm_path`(1024), `strm_file_name`(255));

-- openlist_copy 表唯一索引：copy_src_path + copy_src_file_name
ALTER TABLE `openlist_copy`
    ADD UNIQUE INDEX `uk_copy_src_path_file_name` (`copy_src_path`(1024), `copy_src_file_name`(255));

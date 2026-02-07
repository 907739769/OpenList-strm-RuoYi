package com.ruoyi.openliststrm.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.api.OpenlistApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路径选择通用接口
 */
@Controller
@RequestMapping("/openliststrm/common/path")
public class OpenlistPathController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(OpenlistPathController.class);

    @Autowired
    private OpenlistApi openlistApi;

    private String prefix = "openliststrm/common";

    @GetMapping("/select")
    public String selectPath(String type, String inputId, org.springframework.ui.ModelMap mmap) {
        mmap.put("type", type);
        mmap.put("inputId", inputId);
        return prefix + "/tree";
    }

    /**
     * 获取 Openlist 目录结构
     */
    @PostMapping("/openlist")
    @ResponseBody
    public List<Map<String, Object>> getOpenlistPath(String id) {
        List<Map<String, Object>> trees = new ArrayList<>();
        String path = StringUtils.isEmpty(id) ? "/" : id;
        log.info("正在获取 Openlist 目录: {}", path);

        try {
            JSONObject response = openlistApi.getOpenlist(path);
            if (response != null && response.getInteger("code") == 200) {
                JSONObject data = response.getJSONObject("data");
                // 兼容不同的 Openlist/Alist 返回结构，这里假设是 data -> content
                JSONArray content = data.getJSONArray("content");
                // 如果 content 为空，尝试直接读取 files 字段等，视具体 API 而定

                if (content != null) {
                    for (int i = 0; i < content.size(); i++) {
                        JSONObject file = content.getJSONObject(i);
                        if (file.getBoolean("is_dir")) {
                            Map<String, Object> map = new HashMap<>();
                            String name = file.getString("name");
                            // 处理路径拼接，避免出现 //
                            String currentPath = "/".equals(path) ? "/" + name : path + "/" + name;

                            map.put("id", currentPath);
                            map.put("pId", path);
                            map.put("name", name);
                            map.put("title", name);
                            map.put("isParent", true);
                            trees.add(map);
                        }
                    }
                }
            } else {
                log.error("Openlist API 返回错误: {}", response);
            }
        } catch (Exception e) {
            log.error("获取 Openlist 目录异常", e);
        }
        return trees;
    }

    /**
     * 获取本地目录结构
     */
    @PostMapping("/local")
    @ResponseBody
    public List<Map<String, Object>> getLocalPath(String id) {
        List<Map<String, Object>> trees = new ArrayList<>();
        log.info("正在获取本地目录，父节点 ID: {}", id);

        File[] files;
        boolean isRoot = StringUtils.isEmpty(id);

        try {
            if (isRoot) {
                // 加载根节点
                files = File.listRoots();
                log.info("系统根目录数量: {}", files == null ? 0 : files.length);
            } else {
                File parent = new File(id);
                if (!parent.exists()) {
                    log.warn("目录不存在: {}", id);
                    return trees;
                }
                files = parent.listFiles();
            }

            if (files != null) {
                for (File file : files) {
                    // 必须是目录，且非隐藏文件（可视需要放开隐藏文件）
                    if (file.isDirectory() && !file.isHidden()) {
                        Map<String, Object> map = new HashMap<>();
                        // 如果是根目录列表（如 Windows 的 C:\），直接用 getPath
                        String currentPath = file.getAbsolutePath();
                        // 统一路径分隔符，避免 Windows 下出现反斜杠问题
                        currentPath = currentPath.replace("\\", "/");

                        map.put("id", currentPath);
                        map.put("pId", id);
                        // 根节点显示完整路径（C:\），子节点显示名称（Movies）
                        map.put("name", isRoot ? file.getPath() : file.getName());
                        map.put("title", file.getPath());
                        map.put("isParent", true); // 只有目录才显示，所以肯定是 Parent
                        trees.add(map);
                    }
                }
            } else {
                log.warn("无法列出目录内容 (可能是权限问题): {}", id);
            }
        } catch (Exception e) {
            log.error("获取本地目录失败", e);
        }

        // 特殊处理：如果是 Linux 根目录，File.listRoots() 可能只返回一个 "/" 且listFiles为空
        // 如果上面没加载到任何东西，且是根目录查询，手动添加根节点
        if (isRoot && trees.isEmpty()) {
            File root = new File("/");
            if (root.exists()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", "/");
                map.put("pId", "");
                map.put("name", "/");
                map.put("title", "系统根目录");
                map.put("isParent", true);
                trees.add(map);
            }
        }

        return trees;
    }
}
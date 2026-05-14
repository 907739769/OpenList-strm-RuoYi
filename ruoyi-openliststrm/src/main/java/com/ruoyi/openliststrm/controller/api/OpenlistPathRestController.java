package com.ruoyi.openliststrm.controller.api;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.api.OpenlistApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路径选择 REST API控制器
 *
 * @author Jack
 */
@RestController
@RequestMapping("/api/openliststrm/path")
@Anonymous
@CrossOrigin
public class OpenlistPathRestController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(OpenlistPathRestController.class);

    @Autowired
    private OpenlistApi openlistApi;

    /**
     * 获取 Openlist 目录结构
     */
    @GetMapping("/openlist")
    public Result<List<Map<String, Object>>> getOpenlistPath(@RequestParam(value = "id", required = false) String id)
    {
        List<Map<String, Object>> trees = new ArrayList<>();
        String path = StringUtils.isEmpty(id) ? "/" : id;
        log.info("正在获取 Openlist 目录: {}", path);

        try
        {
            JSONObject response = openlistApi.getOpenlist(path);
            if (response != null && response.getInteger("code") == 200)
            {
                JSONObject data = response.getJSONObject("data");
                JSONArray content = data.getJSONArray("content");

                if (content != null)
                {
                    for (int i = 0; i < content.size(); i++)
                    {
                        JSONObject file = content.getJSONObject(i);
                        if (file.getBoolean("is_dir"))
                        {
                            Map<String, Object> map = new HashMap<>();
                            String name = file.getString("name");
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
            }
            else
            {
                log.error("Openlist API 返回错误: {}", response);
            }
        }
        catch (Exception e)
        {
            log.error("获取 Openlist 目录异常", e);
        }
        return Result.success(trees);
    }

    /**
     * 获取本地目录结构
     */
    @GetMapping("/local")
    public Result<List<Map<String, Object>>> getLocalPath(@RequestParam(value = "id", required = false) String id)
    {
        List<Map<String, Object>> trees = new ArrayList<>();
        log.info("正在获取本地目录，父节点 ID: {}", id);

        File[] files;
        boolean isRoot = StringUtils.isEmpty(id);

        try
        {
            if (isRoot)
            {
                files = File.listRoots();
                log.info("系统根目录数量: {}", files == null ? 0 : files.length);
            }
            else
            {
                File parent = new File(id);
                if (!parent.exists())
                {
                    log.warn("目录不存在: {}", id);
                    return Result.success(trees);
                }
                files = parent.listFiles();
            }

            if (files != null)
            {
                for (File file : files)
                {
                    if (file.isDirectory() && !file.isHidden())
                    {
                        Map<String, Object> map = new HashMap<>();
                        String currentPath = file.getAbsolutePath();
                        currentPath = currentPath.replace("\\", "/");

                        map.put("id", currentPath);
                        map.put("pId", id);
                        map.put("name", isRoot ? file.getPath() : file.getName());
                        map.put("title", file.getPath());
                        map.put("isParent", true);
                        trees.add(map);
                    }
                }
            }
            else
            {
                log.warn("无法列出目录内容 (可能是权限问题): {}", id);
            }
        }
        catch (Exception e)
        {
            log.error("获取本地目录失败", e);
        }

        if (isRoot && trees.isEmpty())
        {
            File root = new File("/");
            if (root.exists())
            {
                Map<String, Object> map = new HashMap<>();
                map.put("id", "/");
                map.put("pId", "");
                map.put("name", "/");
                map.put("title", "系统根目录");
                map.put("isParent", true);
                trees.add(map);
            }
        }

        return Result.success(trees);
    }
}

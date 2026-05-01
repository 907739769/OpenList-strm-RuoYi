package com.ruoyi.web.controller.api.common;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.config.ServerConfig;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.Result;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.FileUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * 通用REST API控制器
 *
 * @author ruoyi
 */
@Tag(name = "通用API")
@RestController
@RequestMapping("/api/common")
@Anonymous
@CrossOrigin(origins = "*")
public class CommonApiController
{
    private static final Logger log = LoggerFactory.getLogger(CommonApiController.class);

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 文件上传（单个）
     */
    @Operation(summary = "文件上传（单个）")
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(MultipartFile file) throws Exception
    {
        try
        {
            String filePath = RuoYiConfig.getUploadPath();
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = serverConfig.getUrl() + fileName;
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("fileName", fileName);
            data.put("newFileName", FileUtils.getName(fileName));
            data.put("originalFilename", file.getOriginalFilename());
            return Result.success(data);
        }
        catch (Exception e)
        {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 文件下载
     */
    @Operation(summary = "文件下载")
    @GetMapping("/download/{fileName}")
    public void fileDownload(@PathVariable("fileName") String fileName, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = RuoYiConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 获取用户头像
     */
    @Operation(summary = "获取用户头像")
    @GetMapping("/profile")
    public Result<String> profile()
    {
        String avatar = RuoYiConfig.getProfile() + "/avatar.png";
        return Result.success(avatar);
    }
}

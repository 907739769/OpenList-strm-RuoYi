package com.ruoyi.common.core.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 详细错误响应
 */
public class ErrorResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private String timestamp;
    private String path;

    public ErrorResult() {}

    public ErrorResult(int code, String message, LocalDateTime timestamp, String path)
    {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp.toString();
        this.path = path;
    }

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(String timestamp)
    {
        this.timestamp = timestamp;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }
}

# ruoyi-admin 模块知识库

## OVERVIEW
启动入口模块，包含 Spring Boot 主类、Servlet 初始化器、WebSocket 配置及监控/系统管理 API 控制器。

## STRUCTURE
```
src/main/java/com/ruoyi/
├── RuoYiApplication.java              # Spring Boot 启动入口
├── RuoYiServletInitializer.java       # 外部 Tomcat 部署支持
└── framework/config/WebSocketConfig.java  # WebSocket 配置
src/main/java/com/ruoyi/web/controller/api/
├── monitor/
│   ├── MonitorJobApiController.java   # 定时任务状态 API
│   └── MonitorJobLogApiController.java # 定时任务日志 API
└── system/
    ├── AuthApiController.java         # 认证 API（登录/登出/Token刷新）
    ├── SysConfigApiController.java    # 系统配置 API
    ├── SysDictDataApiController.java  # 字典数据 API
    └── SysDictTypeApiController.java  # 字典类型 API
src/main/java/com/ruoyi/web/controller/monitor/
└── LogWebSocket.java                  # 实时日志 WebSocket 推送
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 启动入口 | `RuoYiApplication.java` |
| 认证 API | `web/controller/api/system/AuthApiController.java` |
| WebSocket 日志推送 | `web/controller/monitor/LogWebSocket.java` |
| 定时任务监控 | `web/controller/api/monitor/MonitorJobApiController.java` |
| 后端配置 | `src/main/resources/application.yml` + `application-druid.yml` |
| 日志配置 | `src/main/resources/logback-spring.xml` |

## CONVENTIONS
- 控制器统一放在 `web/controller/api/` 下，按功能域（monitor/system）分组
- 监控类 API 放在 `web/controller/monitor/`（如 LogWebSocket）
- 启动类加 `@SpringBootApplication`，不含组件扫描路径限制
- 日志配置使用 logback-spring.xml，支持 Spring Profile 激活

## ANTI-PATTERNS
- 不要在控制器中写业务逻辑，只负责参数接收和响应封装
- WebSocket 推送只用于实时日志，不要承载业务消息
- application.yml 中只放模块级配置，全局配置在 `config/application.yml`

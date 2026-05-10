# ruoyi-framework 模块知识库

## OVERVIEW
Spring Boot 基础设施配置模块，集中管理 Shiro 安全、Druid 数据源、MyBatis/MyBatis-Plus、缓存、拦截器、过滤器等核心配置。

## STRUCTURE
```
src/main/java/com/ruoyi/framework/
├── aspectj/            # AOP 切面（DataScope, DataSource, Log, Permissions）
├── config/             # 配置类（Shiro, Druid, MyBatis, Cache, Security 等）
├── config/properties/  # 配置属性类（DruidProperties, PermitAllUrlProperties）
├── datasource/         # 动态数据源（DynamicDataSource）
├── filter/             # 过滤器（RequestLogFilter）
├── interceptor/        # 拦截器（ApiInterceptor）
├── manager/            # 异步任务管理器
├── security/           # 认证过滤器（JwtFilter, LoginFilter 等）
└── web/                # Web 相关（ExceptionHandler, TableSupport 等）
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| Shiro 安全配置 | `config/ShiroConfig.java` |
| Druid 数据源 | `config/DruidConfig.java` |
| MyBatis 配置 | `config/MyBatisConfig.java` |
| MyBatis-Plus 配置 | `config/MybatisPlusConfig.java` |
| 动态数据源 | `datasource/DynamicDataSource.java` |
| 数据权限切面 | `aspectj/DataScopeAspect.java` |
| 日志切面 | `aspectj/LogAspect.java` |
| 安全配置 | `config/SecurityConfig.java` |
| 缓存配置 | `config/CacheConfig.java` |
| 拦截器 | `interceptor/ApiInterceptor.java` |

## CONVENTIONS
- 配置类统一加 `@Configuration` + `@EnableConfigurationProperties`
- Shiro 使用 jakarta 版本（Spring Boot 4.0.6 要求）
- 双 ORM 共存：MyBatis XML 用于 ruoyi-system 模块，MyBatis-Plus 用于 ruoyi-openliststrm 模块
- 动态数据源通过 `@DataSource` 注解切换

## ANTI-PATTERNS
- 不要在配置类中写业务逻辑
- Shiro 权限注解必须使用 Jakarta 命名空间
- 动态数据源切换只在读写分离场景使用，不要滥用

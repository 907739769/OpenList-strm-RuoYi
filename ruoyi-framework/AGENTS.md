# ruoyi-framework — Infrastructure

## OVERVIEW
Infrastructure module: Shiro 2.0.0 security, MyBatis/MyBatis-Plus config, Druid datasource, filters, interceptors, async managers, AOP aspects. 60+ Java files.

## STRUCTURE
```
ruoyi-framework/src/main/java/com/ruoyi/framework/
├── aspectj/          — AOP: DataSourceAspect, DataScopeAspect, LogAspect, PermissionsAspect
├── config/           — 15+ config classes
│   ├── DruidConfig.java            — Druid datasource + dynamic data source
│   ├── DruidProperties.java        — Druid connection pool properties
│   ├── MyBatisConfig.java          — Traditional MyBatis (PageHelper, commented)
│   ├── MybatisPlusConfig.java      — MyBatis-Plus (pagination, optimistic lock, block-attack)
│   ├── ShiroConfig.java            — Realm, session manager, cache, cookie, filters
│   ├── SecurityConfig.java         — Spring Security integration (JWT, CORS, anonymous URLs)
│   ├── ResourcesConfig.java        — Static resources, CORS, interceptors
│   ├── CaptchaConfig.java          — Kaptcha验证码配置
│   ├── FilterConfig.java           — XSS filter registration
│   ├── WebSocketConfig.java        — WebSocket endpoint export
│   ├── I18nConfig.java             — i18n locale switching
│   ├── ApplicationConfig.java      — @EnableAspectJAutoProxy, MapperScan
│   ├── CacheConfig.java            — Caffeine cache management
│   └── properties/                 — PermitAllUrlProperties, DruidProperties
├── datasource/       — DynamicDataSource (AbstractRoutingDataSource)
├── filter/           — RequestLogFilter, XssFilter, RepeatSubmitFilter
├── interceptor/      — RateLimiterInterceptor, RepeatSubmitInterceptor, ApiInterceptor
├── manager/          — AsyncManager, AsyncFactory, ShutdownManager
├── security/         — SecurityUserDetailsService, JwtAuthenticationFilter (Spring Security)
├── shiro/            — Shiro realm + session/cache/rememberMe implementations
│   ├── realm/UserRealm.java        — Custom realm (authN/authZ logic)
│   ├── service/                    — SysLoginService, SysRegisterService, SysPasswordService
│   ├── session/                    — OnlineSession, OnlineSessionDAO, OnlineSessionFactory
│   ├── web/filter/                 — JwtAuthFilter, CsrfValidateFilter, KickoutSessionFilter
│   ├── web/jakarta/                — JakartaDefaultWebSecurityManager, JakartaCookie*
│   └── web/session/                — OnlineWebSessionManager, SpringSessionValidationScheduler
└── web/              — GlobalExceptionHandler, Server/Sys/Mem/Jvm/Cpu/SysFile domain models
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Shiro security config | `config/ShiroConfig.java` |
| Shiro realm (authN/authZ) | `shiro/realm/UserRealm.java` |
| JWT auth filter | `shiro/web/filter/JwtAuthFilter.java` |
| CSRF validation | `shiro/web/filter/csrf/CsrfValidateFilter.java` |
| Kickout session | `shiro/web/filter/kickout/KickoutSessionFilter.java` |
| Druid datasource | `config/DruidConfig.java` |
| Dynamic data source | `datasource/DynamicDataSource.java` |
| MyBatis-Plus config | `config/MybatisPlusConfig.java` |
| Static resource/CORS | `config/ResourcesConfig.java` |
| AOP: data source switch | `aspectj/DataSourceAspect.java` |
| AOP: data scope filter | `aspectj/DataScopeAspect.java` |
| AOP: operation log | `aspectj/LogAspect.java` |
| AOP: permissions context | `aspectj/PermissionsAspect.java` |
| Request rate limiter | `interceptor/RateLimiterInterceptor.java` |
| Async task executors | `manager/AsyncManager.java` |
| Global exception handler | `web/exception/GlobalExceptionHandler.java` |
| Server monitoring models | `web/domain/server/` (Cpu, Mem, Jvm, Sys, SysFile) |

## CONVENTIONS
- Datasource configured manually (no `DataSourceAutoConfiguration`) — excluded in `RuoYiApplication.java`
- **Shiro 2.0.0** (upgraded from 1.x): Jakarta EE compatible, session synced to DB every 1min
- **Spring Boot 3 / Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`), `JakartaDefaultWebSecurityManager`
- Redis used for Shiro session + permission cache
- Druid stat filter enabled for SQL monitoring (`druid-spring-boot-3-starter`)
- Knife4j 4.4.0 for API documentation (not Swagger 2)
- MyBatis-Plus plugins: pagination, optimistic lock, block-attack (full table update prevention)
- `@Anonymous` URLs configured via `PermitAllUrlProperties` — bypasses authentication
- Spring Security integration coexists with Shiro for JWT authentication flow

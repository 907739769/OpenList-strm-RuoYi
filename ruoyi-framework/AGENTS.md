# ruoyi-framework — Infrastructure

## OVERVIEW
Infrastructure module: Shiro security, MyBatis/MyBatis-Plus config, Druid datasource, filters, interceptors, async managers.

## STRUCTURE
```
ruoyi-framework/src/main/java/com/ruoyi/framework/
├── aspectj/          — AOP: @RateLimiter, @Log, data scope
├── config/           — 12 config classes
│   ├── DruidConfig.java          — Druid datasource + stat/wall filters
│   ├── MyBatisConfig.java        — Traditional MyBatis (PageHelper)
│   ├── MybatisPlusConfig.java    — MyBatis-Plus (pagination, performance)
│   ├── ShiroConfig.java          — Realm, session manager, cache, cookie
│   ├── ResourcesConfig.java      — Static resources, CORS
│   └── properties/               — Config property POJOs
├── datasource/       — Dynamic datasource switching
├── filter/           — XSS filter, repeat submission filter, property filter
├── interceptor/      — Repeat submit interceptor
├── manager/          — AsyncManager, FactoryManager, ShutdownManager
└── shiro/            — Shiro realm + session/cache implementations
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Shiro security config | `config/ShiroConfig.java` |
| Druid datasource | `config/DruidConfig.java` |
| MyBatis-Plus config | `config/MybatisPlusConfig.java` |
| Static resource/CORS | `config/ResourcesConfig.java` |
| XSS filter | `filter/XssFilter.java` |
| Async task executors | `manager/AsyncManager.java` |

## CONVENTIONS
- Datasource configured manually (no `DataSourceAutoConfiguration`)
- Shiro session synced to DB every 1min (configurable)
- Redis/Ehcache used for Shiro session + permission cache
- Druid stat filter enabled for SQL monitoring

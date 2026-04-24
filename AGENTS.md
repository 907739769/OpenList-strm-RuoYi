# OpenList-Strm-RuoYi Project Knowledge Base

Java 1.8 / Spring Boot 2.5.15 / RuoYi 4.8.1 / Maven multi-module

## OVERVIEW
RuoYi-based admin system extended with OpenList cloud storage management: STRM file generation, file sync/copy between cloud backends, media file auto-renaming with TMDb/OpenAI metadata extraction, Telegram bot control, and WatchService file monitoring.

## STRUCTURE
```
OpenList-strm-RuoYi/
├── ruoyi-admin/          # Entry point (RuoYiApplication), web controllers, resources, static assets
├── ruoyi-common/         # Shared: base entities, utils, annotations, enums, exceptions, MyBatis-Plus helpers
├── ruoyi-framework/      # Infrastructure: Shiro config, MyBatis/MyBatis-Plus config, Druid, filters, interceptors
├── ruoyi-system/         # RuoYi core domain: user/role/dept/menu/dict/notice/log management
├── ruoyi-quartz/         # RuoYi scheduled task management
├── ruoyi-generator/      # RuoYi code generator (Velocity templates)
├── ruoyi-openliststrm/   # Custom business: OpenList API, STRM generation, file sync, media rename, Telegram bot
├── sql/                  # DB schema (RuoYi framework-generated, not project code)
└── config/               # External runtime config (not tracked in git)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| App entry point | `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java` |
| App config | `ruoyi-admin/src/main/resources/application.yml` + `application-druid.yml` |
| MyBatis mappers | `**/src/main/resources/mapper/**/*.xml` |
| MyBatis-Plus config | `ruoyi-framework/src/main/java/com/ruoyi/framework/config/MybatisPlusConfig.java` |
| Shiro security | `ruoyi-framework/src/main/java/com/ruoyi/framework/shiro/` |
| RuoYi base controllers | `ruoyi-common/src/main/java/com/ruoyi/common/core/controller/BaseController.java` |
| RuoYi base entity | `ruoyi-common/src/main/java/com/ruoyi/common/core/domain/BaseEntity.java` |
| Custom business logic | `ruoyi-openliststrm/` (see sub-module AGENTS.md) |
| Common utilities | `ruoyi-common/src/main/java/com/ruoyi/common/utils/` |
| DB schema | `ruoyi-common/src/main/resources/sql/` (user SQL, not framework sql/) |

## CONVENTIONS
- **Package structure**: `com.ruoyi.{module}.{layer}` — layer = controller, service, service.impl, mapper, domain, config
- **Service interface + impl**: All services have `I*Service` interface + `*ServiceImpl` implementing `IService<T>` (MyBatis-Plus) or standard interface
- **Controller pattern**: RuoYi BaseController → `prefix` returns Thymeleaf template path; `@RequiresPermissions("module:entity:action")` for Shiro
- **MyBatis XML mappers**: `mapper/*Mapper.java` interface + `resources/mapper/{module}/*Mapper.xml`
- **MyBatis-Plus**: Entities use `@TableName`, `@TableId(type = IdType.AUTO)`, extend `BaseEntity`; mappers extend `BaseMapper<T>`
- **Response wrapper**: `AjaxResult` (success/error with code/message/data)
- **Pagination**: `TableDataInfo` + `TableSupport` + PageHelper for traditional MyBatis; MyBatis-Plus `IPage` for Plus mappers
- **Annotation-driven**: `@Service`, `@Controller`, `@RestController`, `@Mapper`, `@Repository`, `@Configuration`, `@Bean`
- **Custom annotations** (in `ruoyi-common/annotation/`): `@Log`, `@RateLimiter`, `@DataScope`, `@Excel`, `@Excels`, `@RepeatSubmit`

## ANTI-PATTERNS
- Do NOT add business logic to `ruoyi-framework` or `ruoyi-common` — those are infrastructure/shared only
- Do NOT mix MyBatis XML and MyBatis-Plus programmatic queries for the same entity — pick one per module
- Do NOT hardcode paths — use `sys_config` table via `OpenlistConfig` or RuoYi's `ISysConfigService`
- Do NOT skip `@RequiresPermissions` on controller methods
- `ruoyi-openliststrm` has dual persistence (MyBatis XML + MyBatis-Plus) — the `*Plus` variants are for new code

## COMMANDS
```bash
# Build
mvn clean package -DskipTests

# Run (from ruoyi-admin)
cd ruoyi-admin && mvn spring-boot:run

# Scripts
./ry.sh    # Linux/Mac start/stop/restart
./ry.bat   # Windows start/stop/restart
```

## NOTES
- `sql/` is framework-generated RuoYi schema — user SQL is in `ruoyi-common/src/main/resources/sql/`
- `config/` is excluded from git (runtime config only)
- Static assets in `ruoyi-admin/src/main/resources/static/` (jQuery, Bootstrap, Layui, Bootstrap-Table)
- Thymeleaf templates in `ruoyi-openliststrm/src/main/resources/templates/openliststrm/`
- Spring Boot excludes `DataSourceAutoConfiguration` — datasource configured manually via Druid
- SQL init runs on startup: `classpath*:sql/schema.sql` and `classpath*:sql/data.sql` (globally)

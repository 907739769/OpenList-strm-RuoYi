# OpenList-Strm-RuoYi Project Knowledge Base

Java 21 / Spring Boot 3.2.5 / RuoYi 4.8.1 (upgraded) / Shiro 2.0.0 / Maven multi-module

## OVERVIEW
RuoYi-based admin system extended with OpenList cloud storage management: STRM file generation, file sync/copy between cloud backends, media file auto-renaming with TMDb/OpenAI metadata extraction, Telegram bot control, and WatchService file monitoring. Vue 3 + Element Plus admin frontend.

## STRUCTURE
```
OpenList-strm-RuoYi/
├── ruoyi-admin/          # Entry point (RuoYiApplication), web controllers, resources
├── ruoyi-common/         # Shared: base entities, utils, annotations, enums, exceptions
├── ruoyi-framework/      # Infrastructure: Shiro 2.0, MyBatis/Plus config, Druid, filters
├── ruoyi-system/         # RuoYi core domain: user/role/dept/menu/dict/notice/log
├── ruoyi-quartz/         # RuoYi scheduled task management (Quartz)
├── ruoyi-openliststrm/   # Custom business: OpenList API, STRM, sync, rename, Telegram
├── openlist-web/         # Vue 3 + Vite + Element Plus admin frontend (TS)
├── docker-compose.yml    # 3-service compose: mysql + backend + frontend
├── Dockerfile.backend    # Backend Docker image (Eclipse Temurin 21)
├── Dockerfile.frontend   # Frontend Docker image (nginx)
├── nginx.conf            # Nginx config: SPA fallback, API/WebSocket proxy, caching
└── config/               # External runtime config (NOT in repo — created at deploy time)
```

## MODULE DEPENDENCIES
```
ruoyi-common  ← (base, no backend deps)
    ↑
ruoyi-system  ← depends on ruoyi-common
    ↑
ruoyi-framework ← depends on ruoyi-system
    ↑              ↑              ↑
ruoyi-admin   ruoyi-quartz   ruoyi-openliststrm
(deploy)      (scheduled)    (custom business)
    ↑
openlist-web  ← (frontend, communicates via REST API)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| App entry point | `ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java` |
| App config | `ruoyi-admin/src/main/resources/application.yml` + `application-druid.yml` |
| MyBatis mappers | `**/src/main/resources/mapper/**/*.xml` |
| MyBatis-Plus config | `ruoyi-framework/src/main/java/com/ruoyi/framework/config/MybatisPlusConfig.java` |
| Shiro 2.0 security | `ruoyi-framework/src/main/java/com/ruoyi/framework/shiro/` |
| RuoYi base controllers | `ruoyi-common/src/main/java/com/ruoyi/common/core/controller/BaseController.java` |
| RuoYi base entity | `ruoyi-common/src/main/java/com/ruoyi/common/core/domain/BaseEntity.java` |
| Custom business logic | `ruoyi-openliststrm/` (see sub-module AGENTS.md) |
| Common utilities | `ruoyi-common/src/main/java/com/ruoyi/common/utils/` |
| DB schema | `ruoyi-common/src/main/resources/sql/` (user SQL, not framework sql/) |
| Vue 3 frontend | `openlist-web/src/` (see sub-module AGENTS.md) |
| Docker deployment | `docker-compose.yml`, `Dockerfile.backend`, `Dockerfile.frontend` |

## CONVENTIONS
- **Package structure**: `com.ruoyi.{module}.{layer}` — layer = controller, service, service.impl, mapper, domain, config
- **Service interface + impl**: All services have `I*Service` interface + `*ServiceImpl` implementing `IService<T>` (MyBatis-Plus) or standard interface
- **Controller pattern**: RuoYi BaseController → `prefix` returns Thymeleaf template path; `@RequiresPermissions("module:entity:action")` for Shiro 2.0
- **MyBatis XML mappers**: `mapper/*Mapper.java` interface + `resources/mapper/{module}/*Mapper.xml`
- **MyBatis-Plus**: Entities use `@TableName`, `@TableId(type = IdType.AUTO)`, extend `BaseEntity`; mappers extend `BaseMapper<T>`
- **Response wrapper**: `AjaxResult` (success/error with code/message/data)
- **Pagination**: `TableDataInfo` + `TableSupport` + PageHelper for traditional MyBatis; MyBatis-Plus `IPage` for Plus mappers
- **Annotation-driven**: `@Service`, `@Controller`, `@RestController`, `@Mapper`, `@Repository`, `@Configuration`, `@Bean`
- **Custom annotations** (in `ruoyi-common/annotation/`): `@Log`, `@RateLimiter`, `@DataScope`, `@Excel`, `@Excels`, `@RepeatSubmit`
- **Spring Boot 3 / Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`), `druid-spring-boot-3-starter`
- **Knife4j API docs**: `ruoyi-admin` web controllers use Knife4j (not Swagger 2)

## ANTI-PATTERNS
- Do NOT add business logic to `ruoyi-framework` or `ruoyi-common` — those are infrastructure/shared only
- Do NOT mix MyBatis XML and MyBatis-Plus programmatic queries for the same entity — pick one per module
- Do NOT hardcode paths — use `sys_config` table via `OpenlistConfig` or RuoYi's `ISysConfigService`
- Do NOT skip `@RequiresPermissions` on controller methods
- `ruoyi-openliststrm` has dual persistence (MyBatis XML + MyBatis-Plus) — the `*Plus` variants are for new code
- Do NOT use `javax.*` packages — project upgraded to Jakarta EE 9+ (Spring Boot 3)

## COMMANDS
```bash
# Build
mvn clean package -DskipTests

# Run (from ruoyi-admin)
cd ruoyi-admin && mvn spring-boot:run

# Frontend dev
cd openlist-web && npm run dev

# Frontend build
cd openlist-web && npm run build

# Docker compose (all services)
docker-compose up -d

# Scripts
./ry.sh    # Linux/Mac start/stop/restart
./ry.bat   # Windows start/stop/restart
```

## NOTES
- `sql/` is framework-generated RuoYi schema — user SQL is in `ruoyi-common/src/main/resources/sql/`
- `config/` is excluded from git (runtime config only, created at deploy time)
- Static assets in `ruoyi-admin/src/main/resources/static/` (jQuery, Bootstrap, Layui, Bootstrap-Table)
- Thymeleaf templates in `ruoyi-openliststrm/src/main/resources/templates/openliststrm/`
- Spring Boot excludes `DataSourceAutoConfiguration` — datasource configured manually via Druid
- SQL init runs on startup: `classpath*:sql/schema.sql` and `classpath*:sql/data.sql` (globally)
- `ruoyi-generator` removed from Maven modules (no longer used)
- Backend runs on port 6895; frontend on port 80 (nginx); MySQL on 3306
- Docker: `DB_HOST=mysql`, `SPRING_PROFILES_ACTIVE=druid`, cipher key via env var
- Maven module names use hyphens (e.g., `ruoyi-openliststrm`) — consistent with project naming
- Frontend dev server on port 3000, proxies `/api` to backend `localhost:6895`

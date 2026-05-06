# ruoyi-common — Shared Utilities & Base Classes

## OVERVIEW
Shared infrastructure module: base entities, utilities, annotations, enums, exceptions, MyBatis-Plus helpers. 100+ Java files. 8 custom annotations, 7+ enums, 20+ exception classes, 40+ utility classes.

## STRUCTURE
```
ruoyi-common/src/main/java/com/ruoyi/common/
├── core/
│   ├── controller/BaseController.java    — Pagination (TableDataInfo), AjaxResult wrapper, current user helpers
│   ├── domain/                           — BaseEntity, R, TreeEntity, entity beans
│   └── page/                             — TableDataInfo, TableSupport, PageDomain
├── annotation/                           — 8 custom annotations
│   ├── @Log              — Operation log capture (business type, title, etc.)
│   ├── @RateLimiter      — Request rate limiting (mode: count/total)
│   ├── @DataScope        — Data permission scope (dept/user filters)
│   ├── @Excel            — Single field Excel export/import config
│   ├── @Excels           — Multiple Excel field annotations
│   ├── @RepeatSubmit     — Idempotent request guard (interval ms)
│   ├── @Sensitive        — Data desensitization (type-based masking)
│   └── @Anonymous        — Bypass authentication for URL
├── constant/                             — 6 constant classes
│   ├── Constants.java           — General constants (YES/NO, TRUE/FALSE, etc.)
│   ├── UserConstants.java       — User/role/dept/menu default values
│   ├── ShiroConstants.java      — Session, cache, remember-me keys
│   ├── ScheduleConstants.java   — Quartz job status constants
│   ├── PermissionConstants.java — Cache keys, session flags
│   └── GenConstants.java        — Code generator placeholders
├── enums/                                — 7+ enum classes
│   ├── BusinessType       — INSERT, UPDATE, DELETE, GRANT, EXPORT, IMPORT, etc.
│   ├── DataSourceType     — MASTER, SLAVE
│   ├── UserStatus         — ONLINE, OFFLINE, ONLINE_BLOCK, OFFLINE_BLOCK
│   ├── OnlineStatus       — ONLINE, OFFLINE
│   ├── OperatorType       — OTHER, SELF, ADMIN
│   ├── DesensitizedType   — ID_CARD, PHONE, EMAIL, PASSWORD, etc.
│   └── BusinessStatus     — SUCCESS, FAIL
├── exception/                            — 20+ exception classes
│   ├── base/BaseException.java          — Base exception class
│   ├── GlobalException.java             — Global exception handler target
│   ├── ServiceException.java            — Business logic exception (with message + code)
│   ├── UtilException.java               — Utility class exceptions
│   ├── BusinessException.java           — Business error exception
│   ├── DemoModeException.java           — Demo mode guard (read-only)
│   ├── user/                            — 10 user-related exceptions
│   ├── file/                            — 5 file-related exceptions
│   └── job/TaskException.java           — Quartz job execution exception
├── utils/                                — 40+ utility classes
│   ├── poi/ExcelUtil.java               — 1928 lines, Excel import/export
│   ├── poi/ExcelHandlerAdapter.java     — Excel custom date formatter
│   ├── html/HTMLFilter.java             — 569 lines, XSS sanitization
│   ├── html/EscapeUtil.java             — HTML escape/unescape
│   ├── text/Convert.java                — 1010 lines, type conversion
│   ├── uuid/UUID.java                   — Secure random UUID generator
│   ├── uuid/Seq.java                    — Sequence number generator
│   ├── uuid/IdUtils.java                — ID generation utilities
│   ├── security/                        — Md5Utils, CipherUtils, PermissionUtils
│   ├── file/                            — FileUtils, FileUploadUtils, MimeTypeUtils, ImageUtils
│   ├── http/                            — HttpUtils, UserAgentUtils
│   ├── spring/SpringUtils.java          — Spring context access
│   ├── bean/                            — BeanUtils, BeanValidators
│   ├── sql/SqlUtil.java                 — SQL injection prevention
│   ├── StringUtils.java                 — 722 lines, string operations
│   ├── DateUtils.java                   — Date formatting/parsing
│   ├── IpUtils.java                     — IP to location
│   ├── AddressUtils.java                — Address parsing
│   ├── CookieUtils.java                 — Cookie read/write
│   ├── JwtTokenUtil.java                — JWT token generation/validation
│   ├── DesensitizedUtil.java            — Data masking utilities
│   ├── DictUtils.java                   — Dict cache utilities
│   ├── CacheUtils.java                  — Redis cache helpers
│   ├── ShiroUtils.java                  — Shiro session/permission helpers
│   ├── LogUtils.java                    — Log formatting
│   ├── ServletUtils.java                — Servlet request/response helpers
│   ├── ExceptionUtil.java               — Stack trace utilities
│   └── Arith.java                       — Decimal arithmetic utilities
├── xss/                                  — XssFilter, XssHttpServletRequestWrapper, @Xss
└── mybatisplus/                          — MyMetaObjectHandler, MysqlDdl, BaseEntity (Plus)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Base entity for new domain | `core/domain/BaseEntity.java` (+ `mybatisplus/BaseEntity.java` for Plus) |
| Response wrapper | `core/domain/AjaxResult.java` |
| Pagination | `core/page/TableDataInfo.java` |
| Custom annotations | `annotation/` (8 annotations) |
| Excel import/export | `utils/poi/ExcelUtil.java` |
| String/date utilities | `utils/StringUtils.java`, `utils/DateUtils.java` |
| File upload | `utils/file/FileUploadUtils.java` |
| MyBatis-Plus auto-fill | `mybatisplus/MyMetaObjectHandler.java` |
| XSS filter | `xss/XssFilter.java` |
| JWT token utilities | `utils/JwtTokenUtil.java` |
| IP/location utilities | `utils/IpUtils.java`, `utils/AddressUtils.java` |
| Business exceptions | `exception/ServiceException.java` |
| User exceptions | `exception/user/` (10 classes) |

## CONVENTIONS
- New entities extend `BaseEntity` (id, createBy, createTime, updateBy, updateTime, remark)
- MyBatis-Plus entities use `@TableName`, `@TableId(type = IdType.AUTO)`, extend common `BaseEntity`
- Service impls extend `ServiceImpl<M, T>` and implement `IService<T>` (MyBatis-Plus standard)
- Excel export: use `@Excel` annotation on entity fields
- **Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`) — Spring Boot 3 upgrade complete
- Large files: `ExcelUtil.java` (1928 lines), `Convert.java` (1010 lines), `StringUtils.java` (722 lines), `HTMLFilter.java` (569 lines)
- SQL schema files: `src/main/resources/sql/` (schema.sql, init.sql, data.sql + dated migration files)

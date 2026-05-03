# ruoyi-common — Shared Utilities & Base Classes

## OVERVIEW
Shared infrastructure module: base entities, utilities, annotations, enums, exceptions, MyBatis-Plus helpers. 100+ Java files.

## STRUCTURE
```
ruoyi-common/src/main/java/com/ruoyi/common/
├── core/
│   ├── controller/BaseController.java    — Pagination (TableDataInfo), AjaxResult wrapper
│   ├── domain/                           — BaseEntity, R, TreeEntity, entity beans
│   └── page/                             — TableDataInfo, TableSupport, PageDomain
├── annotation/                           — @Log, @RateLimiter, @DataScope, @Excel, @RepeatSubmit
├── constant/                             — UserConstants, ShiroConstants, ScheduleConstants
├── enums/                                — BusinessType, DataSourceType, UserStatus etc.
├── exception/                            — ServiceException, GlobalException, user/file exceptions
├── utils/                                — StringUtils, DateUtils, FileUtils, ExcelUtil etc.
│   ├── poi/ExcelUtil.java               — 1928 lines, Excel import/export
│   ├── html/HTMLFilter.java             — 569 lines, XSS sanitization
│   ├── text/Convert.java                — 1010 lines, type conversion
│   └── uuid/UUID, Seq, IdUtils
├── xss/                                  — XssFilter, XssHttpServletRequestWrapper, Xss annotation
└── mybatisplus/                          — MyMetaObjectHandler, MysqlDdl, BaseEntity (Plus)
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| Base entity for new domain | `common/core/domain/BaseEntity.java` |
| Response wrapper | `common/core/domain/AjaxResult.java` |
| Pagination | `common/core/page/TableDataInfo.java` |
| Custom annotations | `common/annotation/` |
| Excel import/export | `common/utils/poi/ExcelUtil.java` |
| String/date utilities | `common/utils/StringUtils.java`, `common/utils/DateUtils.java` |
| File upload | `common/utils/file/FileUploadUtils.java` |
| MyBatis-Plus auto-fill | `common/mybatisplus/MyMetaObjectHandler.java` |
| XSS filter | `common/xss/XssFilter.java` |

## CONVENTIONS
- New entities extend `BaseEntity` (id, createBy, createTime, updateBy, updateTime, remark)
- MyBatis-Plus entities use `@TableName`, `@TableId(type = IdType.AUTO)`, extend common `BaseEntity`
- Service impls extend `ServiceImpl<M, T>` and implement `IService<T>` (MyBatis-Plus standard)
- Excel export: use `@Excel` annotation on entity fields
- **Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`) — Spring Boot 3 upgrade complete
- Large files: `ExcelUtil.java` (1928 lines), `Convert.java` (1010 lines), `StringUtils.java` (722 lines)

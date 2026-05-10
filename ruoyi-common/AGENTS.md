# ruoyi-common 模块知识库

## OVERVIEW
通用工具模块，提供全项目共享的基础设施：字符串/日期/UUID 工具、Excel 导出、JSON 处理、加密、异常体系、注解、枚举、实体基类等。

## STRUCTURE
```
src/main/java/com/ruoyi/common/
├── annotation/         # 自定义注解（@Log, @DataScope, @DataSource 等）
├── constant/           # 常量定义（Constants, SecurityConstants 等）
├── core/               # 核心基类（BaseController, BaseEntity, TableDataInfo）
├── core/domain/entity/ # 系统实体（SysUser, SysRole, SysMenu 等）
├── enums/              # 通用枚举（UserStatus, OperatorType 等）
├── exception/          # 异常体系（base + user 子包）
├── json/               # JSON 处理（自定义 JSONObject）
├── utils/              # 工具类（StringUtils, IpUtils, DateUtils, ExcelUtil 等）
└── utils/poi/          # Excel 导出（ExcelUtil.java 1928行）
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 字符串工具 | `utils/StringUtils.java` |
| Excel 导出 | `utils/poi/ExcelUtil.java` |
| IP 地址工具 | `utils/IpUtils.java` |
| UUID 生成 | `utils/uuid/UUID.java` |
| 转换工具 | `core/text/Convert.java` |
| HTML 过滤 | `utils/html/HTMLFilter.java` |
| 反射工具 | `utils/reflect/ReflectUtils.java` |
| 系统用户实体 | `core/domain/entity/SysUser.java` |
| 操作日志注解 | `annotation/Log.java` |
| 数据权限注解 | `annotation/DataScope.java` |
| SQL 初始化脚本 | `src/main/resources/sql/` |

## CONVENTIONS
- 工具类全部为 static 方法，不实例化
- 异常体系：自定义异常继承 `base/BaseException`
- Excel 导出统一用 `ExcelUtil`，通过注解标注字段
- 实体基类继承 `BaseEntity`（含 createBy, createTime 等通用字段）

## ANTI-PATTERNS
- 不要在此模块写业务逻辑，只放纯工具
- ExcelUtil 已封装大部分导出场景，不要手写 POI
- 密码加密使用项目内置工具，不要引入 BCrypt 等新库

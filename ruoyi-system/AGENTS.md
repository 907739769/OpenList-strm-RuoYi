# ruoyi-system 模块知识库

## OVERVIEW
系统管理模块，提供用户、角色、菜单、字典、配置、日志等核心系统功能。使用 MyBatis XML（非 MyBatis-Plus）作为 ORM。

## STRUCTURE
```
src/main/java/com/ruoyi/system/
├── domain/          # 系统实体（SysUser, SysRole, SysMenu, SysConfig, SysDict等）
├── mapper/          # MyBatis Mapper 接口（13个 Mapper）
└── service/
    ├── ISysXxxService.java      # 服务接口（11个接口）
    └── impl/SysXxxServiceImpl.java  # 服务实现（11个实现）
src/main/resources/mapper/system/
└── *.xml              # MyBatis XML 映射文件（13个）
```

## WHERE TO LOOK
| 任务 | 位置 |
|------|------|
| 用户实体 | `domain/SysUser.java` |
| 用户 Service | `service/ISysUserService.java` + `impl/SysUserServiceImpl.java` |
| 用户 Mapper | `mapper/SysUserMapper.java` |
| 用户 SQL 映射 | `mapper/system/SysUserMapper.xml` |
| 角色权限 | `domain/SysRole.java` + `mapper/SysRoleMapper.java` |
| 菜单树构建 | `service/impl/SysMenuServiceImpl.java` |
| 字典管理 | `domain/SysDictData.java` + `mapper/SysDictDataMapper.java` |
| 系统配置 | `domain/SysConfig.java` + `mapper/SysConfigMapper.java` |
| 操作日志 | `domain/SysOperLog.java` + `mapper/SysOperLogMapper.java` |
| 登录日志 | `domain/SysLogininfor.java` + `mapper/SysLogininforMapper.xml` |

## CONVENTIONS
- **ORM**: 统一使用 MyBatis XML 映射（非 MyBatis-Plus），Mapper 接口 + XML 配对
- **包结构**: `domain/mapper/service/impl` 四层分离
- **命名**: Service 接口以 `I` 开头（`ISysUserService`），实现类以 `ServiceImpl` 结尾
- **实体基类**: 继承 `com.ruoyi.common.core.domain.BaseEntity`（含 createBy, createTime, updateBy, updateTime 等通用字段）
- **Mapper XML 路径**: `src/main/resources/mapper/system/{Entity}Mapper.xml`

## ANTI-PATTERNS
- 不要在此模块使用 MyBatis-Plus，统一用 MyBatis XML
- 不要在 Service 实现中直接写 SQL，必须通过 Mapper XML
- 用户密码加密使用项目内置工具（`com.ruoyi.common.utils.SecurityUtils`）
- 数据权限通过 `@DataScope` 注解 + `aspectj/DataScopeAspect.java` 切面实现

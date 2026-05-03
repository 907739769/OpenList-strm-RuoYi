# ruoyi-system — RuoYi Core Domain

## OVERVIEW
RuoYi framework core: user/role/department/menu/dict/notice/operational-log management. 16 MyBatis XML mappers.

## STRUCTURE
```
ruoyi-system/src/main/java/com/ruoyi/system/
├── domain/   (10 files) — SysUser, SysRole, SysDept, SysMenu, SysPost, SysDictType, SysDictData, SysConfig, SysOperLog, SysLogininfor
├── mapper/   (16 files) — *Mapper interfaces for all domain entities
└── service/  (12 files) — ISys*Service interfaces + *ServiceImpl implementations
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| User management | `domain/SysUser.java`, `service/ISysUserService.java` |
| Role/permission | `domain/SysRole.java`, `service/ISysRoleService.java` |
| Department tree | `domain/SysDept.java`, `service/ISysDeptService.java` |
| Menu tree | `domain/SysMenu.java`, `service/ISysMenuService.java` |
| Dict data | `domain/SysDictData.java`, `service/ISysDictDataService.java` |
| System config | `domain/SysConfig.java`, `service/ISysConfigService.java` |
| Oper log | `domain/SysOperLog.java`, `service/ISysOperLogService.java` |
| MyBatis XML | `src/main/resources/mapper/system/*.xml` |

## CONVENTIONS
- All 16 mappers use traditional MyBatis XML (not MyBatis-Plus)
- Dept/Menu use tree structure with `parentId`
- Dict data used throughout for dropdown enums (consistent with `common/enums/`)
- **Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`) — Spring Boot 3 upgrade complete

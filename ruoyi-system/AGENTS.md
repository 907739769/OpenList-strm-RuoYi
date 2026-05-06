# ruoyi-system — RuoYi Core Domain

## OVERVIEW
RuoYi framework core: user/role/department/menu/dict/notice/operational-log management. 16 MyBatis XML mappers, 12 service interfaces. Core domain entities (SysUser, SysRole, SysDept, SysMenu, SysDictType, SysDictData) defined in `ruoyi-common/core/domain/entity/`.

## STRUCTURE
```
ruoyi-system/src/main/java/com/ruoyi/system/
├── domain/   (10 files) — System-specific domain entities
│   ├── SysLogininfor.java         — Login attempt log
│   ├── SysConfig.java             — System configuration
│   ├── SysNotice.java             — System announcements
│   ├── SysOperLog.java            — Operation log
│   ├── SysPost.java               — Job position
│   ├── SysUserOnline.java         — Online user session
│   ├── SysUserRole.java           — User-role association
│   ├── SysUserPost.java           — User-post association
│   ├── SysRoleMenu.java           — Role-menu association
│   └── SysRoleDept.java           — Role-dept association
├── mapper/   (16 files) — *Mapper interfaces (traditional MyBatis XML)
│   ├── SysUserMapper.java
│   ├── SysRoleMapper.java
│   ├── SysDeptMapper.java
│   ├── SysMenuMapper.java
│   ├── SysPostMapper.java
│   ├── SysDictTypeMapper.java
│   ├── SysDictDataMapper.java
│   ├── SysConfigMapper.java
│   ├── SysOperLogMapper.java
│   ├── SysLogininforMapper.java
│   ├── SysUserOnlineMapper.java
│   ├── SysNoticeMapper.java
│   ├── SysUserRoleMapper.java
│   ├── SysUserPostMapper.java
│   ├── SysRoleMenuMapper.java
│   └── SysRoleDeptMapper.java
└── service/  (12 files) — ISys*Service interfaces + *ServiceImpl implementations
    ├── ISysUserService + SysUserServiceImpl
    ├── ISysRoleService + SysRoleServiceImpl
    ├── ISysDeptService + SysDeptServiceImpl
    ├── ISysMenuService + SysMenuServiceImpl
    ├── ISysPostService + SysPostServiceImpl
    ├── ISysDictTypeService + SysDictTypeServiceImpl
    ├── ISysDictDataService + SysDictDataServiceImpl
    ├── ISysConfigService + SysConfigServiceImpl
    ├── ISysOperLogService + SysOperLogServiceImpl
    ├── ISysLogininforService + SysLogininforServiceImpl
    ├── ISysNoticeService + SysNoticeServiceImpl
    └── ISysUserOnlineService + SysUserOnlineServiceImpl
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| User management | `service/ISysUserService.java` (+ entity in `ruoyi-common`) |
| Role/permission | `service/ISysRoleService.java` |
| Department tree | `service/ISysDeptService.java` |
| Menu tree | `service/ISysMenuService.java` |
| Dict data | `service/ISysDictDataService.java` |
| System config | `service/ISysConfigService.java` |
| Oper log | `service/ISysOperLogService.java` |
| Logininfor | `domain/SysLogininfor.java`, `service/ISysLogininforService.java` |
| MyBatis XML | `src/main/resources/mapper/system/*.xml` (16 files) |

## CONVENTIONS
- All 16 mappers use traditional MyBatis XML (not MyBatis-Plus)
- Dept/Menu use tree structure with `parentId`, `children`, `orderNum`
- Core entities (User/Role/Dept/Menu/Dict) defined in `ruoyi-common/core/domain/entity/`
- Association entities (UserRole/UserPost/RoleMenu/RoleDept) in `ruoyi-system/domain/`
- Dict data used throughout for dropdown enums (consistent with `common/enums/`)
- **Jakarta EE**: `jakarta.servlet.*` (not `javax.servlet.*`) — Spring Boot 3 upgrade complete

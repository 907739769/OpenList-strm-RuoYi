# ruoyi-openliststrm — Custom Business Logic

## OVERVIEW
OpenList cloud storage management: STRM generation, file sync/copy, media auto-renaming (TMDb/OpenAI), Telegram bot, WatchService monitoring. 100 Java files. Dual persistence: MyBatis XML + MyBatis-Plus.

## STRUCTURE
```
ruoyi-openliststrm/src/main/java/com/ruoyi/openliststrm/
├── api/OpenlistApi.java          — OpenList cloud API (OkHttp: list, copy, mkdir, delete, retry)
├── config/OpenlistConfig.java    — Config reader from sys_config table
├── controller/                   — 8 Thymeleaf controllers (@RequiresPermissions)
├── domain/                       — MyBatis XML entities (6 classes)
├── mybatisplus/                  — MyBatis-Plus layer (6 entities + 6 mappers + 6 services)
├── service/                      — Core business + CRUD services
│   ├── StrmServiceImpl          — STRM generation: cloud dir → .strm files
│   └── CopyServiceImpl          — File sync: queue-based cloud-to-cloud copy
├── helper/                       — OpenListHelper, StrmHelper, CopyHelper, AsynHelper, TgHelper
├── mapper/                       — MyBatis XML mapper interfaces (6 classes)
├── task/OpenListStrmTask.java    — Quartz job: copy(), strm(), rename()
├── monitor/                      — File system monitoring
│   ├── WatchServiceMonitor       — NIO WatchService
│   ├── FileMonitor               — Monitor abstraction
│   ├── FileEvent                 — File event domain
│   ├── FileAlterationMonitorMonitor — Apache Commons Monitor wrapper
│   ├── service/FileMonitorCoordinator — Monitor coordination service
│   └── processor/
│       ├── MediaRenameProcessor  — Parse → classify → organize
│       ├── MediaUploadProcessor  — Local file → cloud upload
│       └── FileProcessor         — File processor interface
├── rename/                       — Media renaming engine
│   ├── MediaParser.java          — Pipeline: extractors → TMDb → OpenAI
│   ├── CategoryRule.java         — Genre/language classification
│   ├── PebbleRenderer.java       — Template rendering
│   ├── RenameTaskManager.java    — 10s poller, task orchestrator
│   └── extractor/impl/           — Resolution, Codec, SourceAndGroup, YearSeasonEpisode
├── tg/                           — Telegram bot (AbilityBot + long-polling)
├── tmdb/                         — TMDb API with @Cacheable
└── openai/                       — OpenAI metadata extraction
```

## WHERE TO LOOK
| Task | Location |
|------|----------|
| OpenList API client | `api/OpenlistApi.java` |
| Config keys | `config/OpenlistConfig.java` |
| STRM generation | `service/impl/StrmServiceImpl.java` |
| File sync | `service/impl/CopyServiceImpl.java` |
| Quartz job entry | `task/OpenListStrmTask.java` |
| Media rename pipeline | `rename/MediaParser.java` |
| Rename templates | `rename/render/PebbleRenderer.java` |
| Telegram bot | `tg/StrmBot.java` |
| External webhook | `controller/NotifyController.java` |
| MyBatis XML mappers | `resources/mapper/openliststrm/` |
| MyBatis-Plus mappers | `mybatisplus/mapper/` |
| Thymeleaf templates | `resources/templates/openliststrm/` |

## BUSINESS FLOWS
1. **STRM**: Task config → `StrmServiceImpl.strmDir()` → OpenlistApi.list() → generate .strm → record to `openlist_strm`
2. **Copy**: Task config → `CopyServiceImpl.syncFiles()` → queue traversal → OpenlistApi.copy() → AsynHelper polls → optional STRM trigger
3. **Rename**: WatchService → MediaParser.extractors → TMDb → OpenAI fallback → CategoryRule → PebbleRenderer → copy to organized dir
4. **Upload**: WatchService → `MediaUploadProcessor` → `CopyService.syncOneFile()` → cloud push

## ANTI-PATTERNS
- New entities use `*Plus` variants (MyBatis-Plus), not legacy `domain/` classes
- Config values from `sys_config` via `OpenlistConfig`, never hardcoded
- MyBatis XML mappers use `<where>` dynamic queries + date range filtering
- MyBatis-Plus XML files are empty stubs — use `QueryWrapper`/`LambdaQueryWrapper` for queries
- `AsynHelper` handles async copy task status polling (30s interval)
- Use `jakarta.servlet.*` (not `javax.servlet.*`) — Spring Boot 3 / Jakarta EE 9+

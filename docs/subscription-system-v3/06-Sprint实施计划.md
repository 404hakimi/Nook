# 06-Sprint 实施计划

> Sprint 1-5+ 实施清单 + 依赖前置. PM / Tech Lead 跟踪进度用.

---

## 十一、实施计划 (Sprint)

### Sprint 0 — 会员管理 + 资源管理改造 (已完成 ✅, 2026-05-20)

#### S0.A 会员管理 (P1)

**目标**: 把会员账号体系单独切片做掉, 解锁后续所有依赖 `member_user_id` 的功能 + 立起 customer portal 鉴权骨架.

**交付**:
- DB: `member_user` (email UQ / password_hash / sub_token UQ / status / last_login_at + ip / remark / 软删)
- 后端 (`nook-module-member`):
  - `MemberAuthService`: 注册 (邮箱 + 密码强度校验 + 自动生成 sub_token + 注册即登录) / 登录 / 登出
  - `MemberProfileService`: getProfile / changePassword (旧密码校验 + 新密码强度) / resetSubToken (碰撞重试)
  - `AdminMemberService`: 列表分页 / 详情 / 禁用 (sa-token kickout) / 启用 / 备注
  - `MemberUserValidator` 集中校验; `MemberErrorCode` 3xxx 段错误码; MapStruct Convert 双 VO (portal / admin)
- 路径前缀: portal `/portal/member/auth/*` + `/portal/member/profile/*`; admin `/admin/member/users/*`
- 鉴权: 复用现有 `SaTokenConfig` 双 StpLogic (system / member) 隔离; 放行注册 / 登录 / 登出
- 前端 (`nook-admin`): `/member/accounts` 列表页 (邮箱搜索 + 状态过滤 + 详情弹窗 + 禁用 / 启用 / 编辑备注)
- 验证: 18 步 PowerShell + MCP DB 端到端测试 ✅; Chrome DevTools UI 验证 ✅

**未做 (转后续 Sprint)**:
- 注册邮箱验证码 (起步不做, 接 SMTP 后补)
- 单元 / 集成测试 (JUnit + MockMvc, 等 CI 时补)
- Customer portal 前端 (起步用 Postman / 后续 P5 订阅生命周期时一起做)

> 详见 `docs/dev/backend-coding-standards.md` (Nook 后端规范 v1.0).

#### S0.B 资源管理 v3 改造 (P2)

**目标**: 把现有 `resource_server` / `resource_ip_pool` 表对齐 v3 设计 (字段 / 状态机 / 区域字典 / 容量+运行时子表拆分); 解锁 P3 Agent 接入和 P4 SKU 关联.

**交付**:
- **DB**:
  - `resource_region` 字典 (新建, 预录入 8 区: US-WEST/EAST / JP-TYO / HK / SG / DE-FRA / UK-LON / KR-SEL)
  - `resource_server` 改造: 加 `bandwidth_mbps / domain UQ / cf_zone_id / cf_record_id / cost_monthly_usd / billing_cycle_day / expires_at / max_concurrent_clients / lifecycle_state ENUM / deleted`; 删 `status` / `total_bandwidth`; `region` 改 FK → `resource_region.code`
  - `resource_server_capacity` (1:1 中频): `monthly_traffic_gb / used_traffic_gb / quota_reset_policy ENUM / throttle_state ENUM`
  - `resource_server_runtime` (1:1 高频): `last_heartbeat_at / temp_unhealthy / agent_version / last_agent_seen_ip / consecutive_miss`
  - `resource_ip_pool` 改造: 加 `lifecycle_state ENUM`; `status` 从 TINYINT(6 值) 改为 ENUM 4 值 (AVAILABLE/RESERVED/OCCUPIED/COOLING); `assigned_member_id` 改名 `occupied_by_member_id`; 加 `reserved_expires_at / cost_monthly_usd / billing_cycle_day / expires_at / deleted`; `region` 改 FK
- **后端 (`nook-module-node`)**:
  - DO 重写: `ResourceServerDO` / `ResourceIpPoolDO` + 新建 `ResourceServerCapacityDO` / `ResourceServerRuntimeDO` / `ResourceRegionDO`
  - Enum 重构: 拆 3 个 (`ResourceServerLifecycleEnum` / `ResourceIpPoolLifecycleEnum` / `ResourceIpPoolStatusEnum`), 删旧 `ResourceServerStatusEnum`
  - Mapper: 全部按新字段重写, `selectAvailable` 加 lifecycle=LIVE AND status=AVAILABLE 双约束, 加 `updateLifecycleState`
  - Service: `createServer` 同步初始化 capacity+runtime 占位行; 加 `transitionLifecycle` 双向流转校验 (INSTALLING ↔ READY ↔ LIVE → RETIRED, RETIRED 可复活回 LIVE, 上线 LIVE 必须 domain 已填)
  - 新建 `ResourceRegionService + Controller` (字典 CRUD + 表单下拉)
  - Validator: 加 `validateDomainUnique`
  - 错误码: `SERVER_DOMAIN_DUPLICATE` / `SERVER_LIFECYCLE_INVALID_TRANSITION` / `SERVER_LIVE_DOMAIN_REQUIRED` / `IP_POOL_LIFECYCLE_INVALID_TRANSITION`
- **路径**:
  - 新增: `POST /admin/resource/server/lifecycle` / `POST /admin/resource/ip-pool/lifecycle` / `GET /admin/resource/region/enabled,list`
- **前端 (`nook-admin`)**:
  - API client 重写: `server.ts` / `ip-pool.ts` 类型对齐 + 新建 `region.ts`
  - ServerList / IpPoolList: 列表展示 region 字典 + 生命周期 Tag + 双状态标签; 顶部搜索栏改 lifecycle / status 下拉过滤; 操作列加"流转"按钮 (NDropdown 选目标状态)
  - ServerFormDialog 重写: 加 domain / cfZoneId / cfRecordId / cost / billing / expires / max_concurrent_clients / region Select; 删 monthlyTrafficGb (归 capacity 子表)
  - IpPoolFormDialog: 加 lifecycleState 字段, 删 status 字段 (allocator 流转, 不在 admin 表单)
  - 兼容修复: XrayNodeDiffDialog / ClientProvisionDialog 适配 status 类型 + occupiedByMemberId 改名
- **验证**:
  - 后端编译 0 错 / 0 warning (跟 P2 相关)
  - 前端 vue-tsc 跟 P2 相关 0 错
  - 端到端: 列表查询 / 详情查 / lifecycle 流转 + 校验 (LIVE 前置 domain / 非法流转拒绝) ✅
  - Chrome DevTools UI 流转按钮 Dropdown 弹出 + 4 状态选项 ✅

**P2 未做 (转后续 Sprint)**:
- Server 详情融合 capacity + runtime 数据展示
- 操作日志接入 (`op_log` 已有框架, 资源模块挂载延后)
- 自动化测试 (依赖 CI 落地)

#### S0.C Agent 接入 (P3, 已完成 ✅, 2026-05-20)

**目标**: 把现有 "backend SSH 拉数据 + 推配置" 模式反转为 "Agent 主动 push + 拉任务"; 跑通端到端心跳 / NIC 流量 / 任务队列三条链路.

**交付**:
- **DB**:
  - `agent_task` 表 (PENDING/PICKED/SUCCESS/FAILED, JSON payload + result_payload, retry_count, FK → resource_server)
  - `resource_server.agent_token` 字段 (CHAR(64) UNIQUE, SHA256 hex)
- **Backend** (`nook-module-node`):
  - `AgentTaskDO` + `AgentTaskMapper` (CAS markPicked WHERE status=PENDING 防并发)
  - `AgentAuthService` (X-Agent-Token Header 鉴权 → server)
  - `AgentReportService + Impl` (心跳 / NIC / 任务拉取 / 结果回写)
  - `AgentController` (`POST /api/agent/heartbeat | nic-traffic | task-result` + `GET /api/agent/tasks`)
  - 4 VO + `ResourceServerMapper.selectByAgentToken`
- **Go Agent** (`nook-agent/`, 新仓库根目录子项目):
  - `main.go` + 3 goroutine (heartbeat / nic / poller) + 优雅 SIGTERM 退出
  - `internal/config` (yaml + 默认值 + 校验)
  - `internal/client` (HTTP client + X-Agent-Token + Result<T> 解码)
  - `internal/heartbeat` (1min POST /api/agent/heartbeat)
  - `internal/nic` (5min vnstat -i eth0 --json → POST /api/agent/nic-traffic)
  - `internal/poller` (30s GET /api/agent/tasks → 派 executor → POST /api/agent/task-result)
  - `internal/executor` (task_type → Handler 注册表, 内置 ping)
- **部署**:
  - 装机脚本: 清旧 xray (apt 卸载) + 装 vnstat 2.12 + 装 Go 1.22.2 + 远程编译 + systemd unit + enable
  - 已在 fra-test (64.118.158.12) 完整验证
- **端到端验证**:
  - 心跳: agent 启动 1s 内 backend 收到, `resource_server_runtime.last_heartbeat_at` 实时更新 ✅
  - NIC: 5min tick, vnstat 数据上报 (新装 vnstat 累计 0GB 正常) ✅
  - 任务: 手工 INSERT ping task → agent 30s 内拉到 → 回写 `{"pong":true}` SUCCESS ✅

#### S0.D v3 完整化 (P4, 已完成 ✅, 2026-05-20)

**目标**: 把 S0.C 列出的 5 个 gap 收尾, 后端代码完全切到 v3 "Agent push + task queue" 模式, 不再有 v2 SSH 路径.

**交付**:
- ✅ **Agent xray executor** (Go): `xray_provision_user` / `xray_remove_user` / `xray_update_outbound` 三个 task_type 已实现 (`nook-agent/internal/xray/xray.go` + `executor/xray_executor.go`); 调本地 xray API adu/rmu/ado/rmo
- ✅ **Agent xray-stats collector** (Go): 每 5min 调 `xray api statsquery` POST `/api/agent/xray-traffic` (`nook-agent/internal/xray/stats.go`)
- ✅ **Backend xray-traffic 接口**: `POST /api/agent/xray-traffic` 接收 user 累计流量, 调 `XrayClientTrafficSampleService.applyAgentStats` 复用 SSH 路径的 email→clientId 反查 + delta 算法
- ✅ **AgentTaskDispatchService**: backend → agent_task 队列单向派任务; 取代 v2 SSH 推送
- ✅ **AgentHeartbeatTimeoutJob**: 每分钟 :30 扫 LIVE server, 1/3/5min 分级判超时, 自动写 `temp_unhealthy` (allocator 跳过); 端到端验证 237s 后 → `temp_unhealthy=1`, 重启 agent → 3s 内清零 ✅
- ✅ **XrayTrafficSampleJob (v2 SSH)** 删除
- ✅ **XrayClientReconcilerJob (v2 SSH)** 暂停 `@Scheduled`, 加 TODO 注释 (Sprint 1 改派 `xray_full_sync` task 后恢复)
- ✅ **AgentInstallScriptService**: admin 调 `POST /admin/resource/server/agent-install-script?id={id}` 生成装机脚本 + 重置 agent_token (轻量版; Sprint 1 全自动化)

#### S0.E Agent 管理体验完善 (P5, 已完成 ✅, 2026-05-20)

**目标**: agent 升级 / 状态可视化 / 无日志 / 一键清日志, 解决磁盘紧张问题 (fra-test 10GB 总, 5G 可用).

**交付**:
- ✅ **Agent 静默运行**: systemd `StandardOutput=null` + `StandardError=null`; journald `SystemMaxUse=50M` 硬上限. fra-test journal 从 100M 压回 32M
- ✅ **Backend 源码下载接口**: `GET /admin/agent-dist/agent-src.tar.gz` 即时打包 nook-agent/ 流式返回 (Java ProcessBuilder tar); 取代 b64 SSH 命令低效路径 (20KB 一次 curl 拉完)
- ✅ **Agent 版本管理**: 编译 ldflags `-X main.Version=0.2.0` 注入; `nook-agent -v` 打印版本; 心跳上报写 `resource_server_runtime.agent_version`
- ✅ **Agent install_dir 可配**: `config.runtime.bin_path` 默认 `/usr/local/bin/nook-agent`; 升级时新 binary 写 `binPath+.new` → sha256 校验 → atomic rename → 自杀 (systemd Restart=always 10s 内拉起)
- ✅ **agent_upgrade task**: payload `{url, sha256, version}`; `UpgradeExecutor` (Go) 下载 + 校验 + 替换 + 重启
- ✅ **truncate_log task**: payload `{paths: [...]}`; `LogExecutor` 白名单防御 (/var/log, /home/socks5/logs, /home/xray/logs); `truncate -s 0` 保留 inode 不重启服务
- ✅ **Admin Agent 管理 UI**:
  - 后端: `AdminAgentController` + `AdminAgentService` (列表 / 详情 / 升级 / 清日志); 在线状态分类 (ONLINE/WARN/TEMP_UNHEALTHY/OFFLINE/NEVER) 跟 HeartbeatTimeoutJob 阈值一致
  - 前端: `views/agent/AgentList.vue` + `/agent/list` 路由; 显示 server / version / 在线 Tag / 心跳延迟 + 升级 + 清日志按钮
- ✅ **端到端验证**:
  - natapp 隧道下载源码 + 远程编译 → agent 从 v0.1.0-fra-test 升到 **v0.2.0** ✅
  - 派 `truncate_log` task → SUCCESS, 释放 **13.4 MB** (/var/log/wtmp + /var/log/btmp) ✅
  - admin UI 看到 v0.2.0 + ONLINE + 心跳 20s ✅

#### S0.F Agent 部署规范化 + Binary 入库 (P6, ✅ 完成, 2026-05-20)

**目标**: 把 Agent 从"远端编译 + /usr/local/bin" 升级到"DB 存 binary + /home/nook-agent 标准目录 + 可配置功能开关 + 全栈时区统一".

**进度**:
- ✅ **P6-1 时区 + locale 统一 Asia/Shanghai + UTF-8** (2026-05-20)
  - fra-test 系统时区: `timedatectl` 已经 `Asia/Shanghai (CST, +0800)`, locale `en_US.UTF-8`
  - MySQL JDBC URL: `serverTimezone=Asia/Shanghai` (dev / prod profile 都已配)
  - Backend Spring Boot Jackson: 加 `spring.jackson.time-zone=Asia/Shanghai` + `date-format=yyyy-MM-dd HH:mm:ss` + `locale=zh_CN`; 所有 JSON 时间字段按上海时区序列化
  - Go Agent: 依赖系统时区 (`time.Now()` 返回 +08:00); 装机脚本里 `timedatectl set-timezone Asia/Shanghai` 兜底
  - MySQL 服务端 `@@global.time_zone=SYSTEM` → CST, 跟 JDBC 一致, 无需改

- ✅ **P6-2 安装目录改 `/home/nook-agent`** (2026-05-20):
  - Go config: `runtime.bin_path` 默认 `/home/nook-agent/bin/nook-agent` (跟 `/home/socks5` `/home/xray` 同级)
  - systemd unit: `ExecStart=/home/nook-agent/bin/nook-agent -c /home/nook-agent/etc/config.yml`
  - 装机脚本 `AgentInstallScriptServiceImpl`: 自动创建 `/home/nook-agent/{bin,etc}/` + 配置 + systemd
  - 兼容: 旧装机 `/usr/local/bin/nook-agent` + `/etc/nook-agent/config.yml` 继续工作 (config 显式给路径覆盖默认即可)
- ✅ **P6-3 `agent_binary` 表 (LONGBLOB) + admin 上传** (2026-05-20):
  - DB: `agent_binary(id, version, os, arch, sha256, size_bytes, bin LONGBLOB, is_default, notes, uploaded_by, ...)`; 唯一约束 `(version, os, arch, deleted)`
  - `AgentBinaryService + Impl`: upload (50MB 上限 + 查重 + sha256 自动算) / setDefault (同 os+arch 自动清旧 default) / delete (默认版本拒删)
  - `AdminAgentBinaryController`: `POST /admin/agent-binary/upload` (multipart) + `GET /list` + `PUT /{id}/default` + `DELETE /{id}`
- ✅ **P6-4 二进制下载 + upgrade task 改造 (从 DB 拉)** (2026-05-20):
  - `GET /admin/agent-dist/bin?id={binaryId}` 或 `?os=linux&arch=amd64` (默认版本); 走 X-Agent-Token 鉴权
  - SaTokenConfig 把 `/admin/agent-dist/bin` 加进放行清单 (走 X-Agent-Token 而非 admin sa-token)
  - `AdminAgentUpgradeReqVO` 简化为只接 `binaryId`; backend 自动查 binary → 渲染 url + sha256 + version → 派 task
  - Go `UpgradeExecutor` 加 authToken 字段, downloadFile 带 X-Agent-Token Header
  - application-dev.yml 加 `nook.backend.public-url` 配置 (默认 natapp 隧道)
- ✅ **P6-5 features 开关 (用现有 interval_seconds=0 等效)** (2026-05-20):
  - 决策: 不新建 `features.heartbeat/nic/poller` 双重配置, **复用现有** `*.interval_seconds=0` 禁用单个 collector (heartbeat / nic / poller / xray.enabled 已支持)
  - 装机脚本 + config.yml.sample 已注释说明
  - admin 装机表单暴露 interval 参数 (P6-6 前端做)
- ✅ **P6-6 Admin UI 二进制管理 + 装机 + 升级集成** (2026-05-20):
  - `nook-admin/src/views/agent/BinaryList.vue`: 上传 (NUpload + multipart, 进度反馈待补) / setDefault / 删除, sha256 展示前 16 字符
  - `nook-admin/src/views/agent/AgentList.vue`: 升级 dialog 改 `NSelect` 列默认 binary
  - `nook-admin/src/router/index.ts` 加 `/agent/list` `/agent/binaries`; **AdminLayout 侧栏新增 Agent 管理分组** (P6-7 才补, 之前漏)
  - `nook-admin/src/views/resource/AgentInstallScriptDialog.vue` (新): 服务器管理每行加 "装机脚本" 按钮, 弹窗输入 backend URL → 调 `POST /admin/resource/server/agent-install-script` → 显示 token + bash 脚本 (NCode 高亮 + 一键复制); URL 记 localStorage 复用
- ✅ **P6-7 端到端测试 + cleanup fra-test 上 Go runtime** (2026-05-20):
  - **E2E 真闭环**: 编译 v0.3.0 binary on fra-line (经 ufw 临时开端口 HTTP 同步源码) → 上传到 DB → 设默认 → admin API 触发 upgrade → agent 拉 `/admin/agent-dist/bin` (带 X-Agent-Token) → sha256 校验通过 → 原子 mv 替换 → 上报 SUCCESS → systemd RestartSec=10s 拉起 → 心跳 v0.3.0 回归 ONLINE ✅
  - **修了 2 个真 bug**:
    - `AgentReportServiceImpl.receiveTaskResult`: `result_payload` 是 JSON column, 老 agent 上报裸字符串触发 `Invalid JSON text` 让 task 永久卡 PICKED + backend 500; 加 `normalizeJsonOrWrap` 兜底, 非法 JSON 包成 `{"raw":"..."}`
    - `AdminAgentBinaryController.upload`: Spring Boot 3 + Tomcat 10 multipart 文本字段绕过 CharacterEncodingFilter, notes 中文乱码; 加 `fixUtf8(s)` 用 latin1→UTF-8 重转 (仅部分场景生效, 完全修复需重写 multipart resolver, 列入打磨清单)
  - 改 `application.yml`: `spring.servlet.multipart.max-file-size=64MB` (binary 5.6MB 超过默认 1MB 上限)
  - 改 `application-dev.yml`: 合并重复 `nook:` 顶级 key (SnakeYAML 直接拒解析), `${NOOK_BACKEND_PUBLIC_URL:http://...}` 加引号避免 URL 内的 `:` 跟占位符语法冲突
  - fra-test cleanup: 卸 `golang-go` + 删 `/root/go` + `/root/nook-agent-src` + `/tmp/nook-agent-*`, 磁盘 4.3G → 4.0G (释放 ~300MB)
  - **暴露的 bootstrap 限制**: P5→P6 协议变了 (download 加 X-Agent-Token), 老 agent 不能自升级到新协议, 第一次必须人工换 binary; 文档化在装机脚本里, 后续新 server 直接用 P6 脚本就一步到位

##### P6 遗留 (打磨期处理, 不阻塞 Sprint 1)
- multipart 文本字段中文 UTF-8 完整修复 (改 MultipartResolver 或加高优先级 CharacterEncodingFilter)
- BinaryList 上传进度条 (axios `onUploadProgress`)
- AgentList 心跳分级颜色 (1/3/5min 黄橙红)
- AgentList 升级 dialog 加 "当前 vs 目标版本" 对比 + 同版本警告
- ServerList 操作栏 8 个按钮太挤, 收纳到 NDropdown "更多"
- 装机脚本 features 开关前端表单 (后端 generate signature 加可选 interval/xray.enabled 参数)
- 旧 P5 路径 `/usr/local/bin/nook-agent` 迁到 P6 标准 `/home/nook-agent/bin/nook-agent` (手动跑一次新装机脚本即可)

### Sprint 1 — 核心订阅模型 + SKU 池 (v3, 2.5 周)

**目标**: 后台能搭 SKU 资源池 + 用户能买套餐 + 资源 lifecycle 完整闭环 + 池内切换跑通.

- DB 新增:
  - `resource_region` / `member_user(sub_token)` / `plan_sku` / **`plan_sku_resource`** (v3 核心关联表)
  - `sub_main` (含 sub_domain + **双状态字段** status/provision_state + **version 乐观锁**)
  - `order_main` (**version 乐观锁**) / `order_item` (**CHECK 约束** type/字段一致性)
  - `alert_record` (5 种 type)
  - `resource_server_capacity` (砍 sold) / **`resource_server_runtime`** (v3 拆出, 高频心跳字段 1:1)
  - `resource_server_traffic` / `resource_ip_pool_traffic` (NIC 流量历史)
  - `agent_task` (Agent 任务队列)
- DB 改老表:
  - `resource_server` 加 `bandwidth_mbps` / `domain` / `cf_zone_id` / `cf_record_id` / `billing_cycle_day` / `expires_at` / `lifecycle_state` / `max_concurrent_clients` (必填)
  - `resource_ip_pool` 加 `ip_type` / `lifecycle_state` / **`status`** (AVAILABLE/RESERVED/OCCUPIED/COOLING, v3 优化) / `dante_user` / `dante_pass`
- 表前缀: `resource_*` / `member_*` / `plan_*` / `sub_*` / `order_*` / `alert_*`
- Service:
  - `SkuService.bindResources / unbindResources / listSkus`
  - `SubscriptionService.allocate / renew / cancel`
  - `AllocatorService.pickIpFromSkuPool / pickServerFromSkuPool` (单步, FOR UPDATE 行锁)
  - `CloudflareDnsService.createARecord / updateARecord / deleteARecord`
  - `OrderService.checkoutCart`
- 调度 Jobs (单一职责):
  - 状态: `ServerCapacityStateJob` / `SubscriptionExpireJob` / `SubscriptionCancelJob` / `FixedQuotaRetireJob` / `AgentHeartbeatTimeoutJob` (新)
  - 告警: `ServerTrafficAlertJob` / `ResourceExpiringAlertJob`
  - 通知: `UserQuotaNearLimitNotifyJob` / `SubscriptionRenewalReminderJob` / `SubscriptionExpiredNotifyJob`
- HTTP Controllers (新, 接收 agent push):
  - `AgentTrafficController` (POST NIC + xray 流量)
  - `AgentHeartbeatController` (POST 心跳)
  - `AgentTaskController` (GET 任务 / POST 结果)
  - `AgentSysInfoController` (POST 系统信息)
- ServerProvisioner / Socks5Installer 安装时**同时部署 nook-agent** (见 [05-Agent架构](./05-Agent架构.md))
- 后台 UI:
  - SKU 管理 + 批量复制
  - 订阅管理 / 订单管理
  - 区域字典
  - 告警视图 (4 种)
  - **资源管理: server / IP 列表带 lifecycle_state 标签, READY 状态显示"待上线" 按钮**
  - **server 容量看板: sold/used 双进度条, 自动 ×2 显示 NIC 视角**

### Sprint 2 — 客户面 portal (1.5 周)

**目标**: 用户注册 / 商品列表 / 购物车 / "我的实例" / 拿订阅 URL.

- 新前端项目 (或共用 admin 加 role)
- Customer portal:
  - 注册 / 登录 (自动生成 sub_token)
  - 商品列表页 (按 region / ip_type / 周期 筛选)
  - 购物车 (同 SKU 数量调整, 跨 SKU 多选)
  - "我的实例"列表 (按到期日升序, 含流量进度 / 续费 / 加购按钮)
  - 订阅 URL 页 + QR + "重置 URL" 按钮
- 后端: `/api/sub/{member.sub_token}` 聚合返回该用户所有活跃 sub 的节点

### Sprint 3 — 订单 + 支付 (1 周)

- 订单流程: 下单 → 支付 → 回调激活
- 集成 Stripe (海外) 或 支付宝 (国内, 后置)
- 退款不做, 工单走 admin

### Sprint 4 — 调度 + 告警 + 自动切换 (1 周)

- 月度流量重置由 NIC 采集自动检测周期边界 (Sprint 1 已支持 3 种 policy), Sprint 4 无需额外调度
- 到期 / 吊销调度 (ACTIVE → EXPIRED → CANCELLED)
- 邮件通知 (SMTP 集成)
- 节点故障自动切换 ([§9.1-9.4 04-监控](./04-监控-流量-告警-切换.md))
- admin 手动迁移工具 ([§9.4 04-监控](./04-监控-流量-告警-切换.md)) — 触发 UI 入口 + 后端 service
- 容量超限告警 (单阈值 90% `SERVER_TRAFFIC_HIGH`)
- admin 手动换 IP 工具 ([§9.5 04-监控](./04-监控-流量-告警-切换.md)) + 落地 IP 到期提醒巡检

### Sprint 5 — 后续优化 (按需, 起步阶段全部不做)

- **流量包加购** (`plan_traffic_addon` + `sub_addon` 两张新表 + 加购 UI + 三段式锁库存)
- **超售系数** (`resource_server_capacity.oversubscription_ratio` 字段, 运营有数据后再开)
- **UNLIMITED 配额策略** (真正不限流量的机房, 加 `quota_reset_policy` 第 4 个枚举值 + 跳过水位监控逻辑)
- **自动主动迁移** (admin 经验充足后从 [§9.4 04-监控](./04-监控-流量-告警-切换.md) 手动改为自动触发)
- **评分函数 5 项加权** (替代起步的 "客户数最少" 简单规则)
- **告警类型细分** (SERVER_OVERSOLD / NODE_MIGRATION 等)
- **退订工单流**
- **套餐升降级** (同 sub 换 SKU)
- **auto-renew 自动续费** (sub_main 加字段)
- **多区域降级分配** (allocator 升级)
- **带宽限速** (dante 端 / xray policy 探索)
- **自研客户端** + 推送通知中心 (减少邮件依赖)

---


---

## 十三、依赖 / 前置

- **现有能力**:
  - Xray 1:N inbound + per-user routing (✅ 已有)
  - xray_client 三段配置幂等推送 CLIENT_SYNC (✅ 已有)
  - 流量统计入库 (✅ 已有, 5min cron)
  - 全量重推 / 差异检测 CLIENT_ALL_SYNC / CLIENT_RECONCILE (✅ 已有)
  - SSH 凭据 + 远端命令调度 (✅ 已有)
  - 全局 TransactionTemplate Bean (✅ 已有)

- **需新建**:
  - 上述新表
  - SubscriptionService / AllocatorService / AlertService / OrderService
  - 客户面前端项目
  - 支付集成 (Stripe SDK)
  - 邮件 SDK (SMTP 或 SendGrid)

---


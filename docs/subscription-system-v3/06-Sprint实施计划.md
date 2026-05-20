# 06-Sprint 实施计划

> Sprint 1-5+ 实施清单 + 依赖前置. PM / Tech Lead 跟踪进度用.

---

## 十一、实施计划 (Sprint)

### Sprint 0 — 会员管理 (已完成 ✅, 2026-05-20)

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


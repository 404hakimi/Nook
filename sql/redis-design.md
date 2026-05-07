# Redis 数据结构设计

两套用户体系，令牌完全隔离：
- `member_user` → 前台会员令牌
- `system_user` → 后台管理令牌

---

## 一、登录令牌（替代 JWT）

登录成功后生成 UUID 作为 token，存入 Redis。

### 会员令牌（member_user）

```
Key:    token:member:{uuid}
Type:   Hash
Fields: member_user_id, username, login_ip, login_ua, device_fp, created_at
TTL:    7 天 (滑动过期)
```

### 后台令牌（system_user）

```
Key:    token:system:{uuid}
Type:   Hash
Fields: system_user_id, username, role, login_ip, login_ua, created_at
TTL:    2 小时 (滑动过期，后台权限敏感，TTL 短)
```

### 校验流程

请求 Header 携带 `Authorization: Bearer {uuid}`，按业务前缀(member/system)选择 key 命名空间：
- 前台 API：`GET token:member:{uuid}` → 不存在 = 401
- 后台 API：`GET token:system:{uuid}` → 不存在 = 401

每次命中 EXPIRE 刷新 TTL，避免活跃用户被踢。

### 强制下线（反向索引）

```
Key:    member_tokens:{memberUserId}     Type: Set    Values: {uuid, ...}
Key:    system_tokens:{systemUserId}     Type: Set    Values: {uuid, ...}
```

登录时 SADD，登出/封禁时遍历 Set 全部 DEL `token:member:{uuid}` 并清空 Set。

---

## 二、设备在线状态 & 数量限制（仅会员）

```
Key:    member_devices:{memberUserId}
Type:   Hash
Field:  {device_fingerprint}
Value:  JSON { device_name, client_type, client_ip, last_active, token }
TTL:    无（靠活跃检测清理）
```

连接时检查 `HLEN`：
- `HLEN member_devices:{memberUserId}` >= max_devices → 拒绝连接或踢掉最早的设备
- 通过则 HSET 注册设备

定时任务每 5 分钟扫描，`last_active` 超过 15 分钟的设备 HDEL 并写回 `member_device` 表为离线状态。

---

## 三、流量实时缓存

### 会员维度（供前台展示）

```
Key:    traffic:member:{memberUserId}:today
Type:   Hash
Fields: uplink, downlink (字节累计)
TTL:    次日 00:30 过期 (每日重置)

Key:    traffic:member:{memberUserId}:month
Type:   Hash
Fields: uplink, downlink
TTL:    次月 1 日 00:30 过期
```

### IP 维度（供后台监控大盘）

```
Key:    traffic:ip:{ipId}:current
Type:   Hash
Fields: uplink_bps, downlink_bps, online_devices, last_update
TTL:    10 分钟 (两个采集周期未更新自动清除)
```

### 服务器维度

```
Key:    traffic:server:{serverId}:current
Type:   Hash
Fields: total_uplink_bps, total_downlink_bps, active_ips, active_members, last_update
TTL:    10 分钟
```

---

## 四、Xray 流量基准值（增量计算用）

Xray stats 返回的是启动以来的累计值，需要记住上次采集的基准值来算增量。

```
Key:    xray_baseline:{serverId}:{email}
Type:   Hash
Fields: uplink, downlink (上次采集的累计值)
TTL:    无（Xray 重启时由采集任务检测到累计值回退，自动重置）
```

---

## 五、频率限制

```
Key:    rate:redeem:{memberUserId}             TTL: 60s     Limit: 5/分钟
Key:    rate:sub:{subToken}                    TTL: 60s     Limit: 10/分钟
Key:    rate:login:fail:member:{username}      TTL: 15分钟  Limit: 5次失败锁定
Key:    rate:login:fail:system:{username}      TTL: 30分钟  Limit: 3次失败锁定(后台更严)
```

---

## 六、CDK 兑换锁（防并发）

```
Key:    lock:cdk:{codeHash}
Type:   String
TTL:    30 秒
```

兑换前 SET NX，成功才继续。比数据库 SELECT FOR UPDATE 轻量，避免长事务。

---

## 七、系统配置缓存

```
Key:    sys:config
Type:   Hash
Fields: register_enabled, maintenance_mode, announcement, ...
TTL:    无（后台修改时主动刷新）
```

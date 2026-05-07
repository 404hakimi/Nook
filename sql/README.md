# SQL 脚本

按业务模块拆分，文件名前缀数字代表建议的执行顺序（无外键约束，实际无强依赖）。

| 文件 | 模块 | 表 |
|------|------|----|
| `01_system.sql` | system | system_user, system_user_log |
| `02_resource.sql` | resource | resource_server, resource_ip_type, resource_ip_pool, resource_ip_compat |
| `03_member.sql` | member | member_user, member_device, member_log, member_subscription |
| `04_business.sql` | business | business_plan, business_cdk, business_recharge, business_order_log |
| `05_xray.sql` | xray | xray_inbound |
| `06_monitor.sql` | monitor (含统计) | monitor_traffic_snapshot, monitor_traffic_daily, monitor_server_bw, monitor_ip_health, monitor_alert |
| `99_seed.sql` | 初始数据 | resource_ip_type 三条种子 |

## 现阶段约定

> 当前阶段需求很多还在 TBD，DDL 保持最小化：

- **只保留主键**（PRIMARY KEY），不加任何二级索引（KEY / UNIQUE KEY）。索引等业务稳定后按真实查询模式补。
- **不做分区**。`monitor_traffic_*` 等数据量大的表后续需要时再做 `ALTER TABLE ... PARTITION BY ...`。
- **不加外键约束**。引用关系仅在列 COMMENT 中说明。
- 表的 ENGINE/CHARSET 统一 InnoDB / utf8mb4。

## 执行

新建库后按文件名顺序执行：

```bash
mysql -u root -p nook < sql/01_system.sql
mysql -u root -p nook < sql/02_resource.sql
# ... 以此类推
mysql -u root -p nook < sql/99_seed.sql
```

或一次性串起来：

```bash
cat sql/0*.sql sql/99_seed.sql | mysql -u root -p nook
```

## 字段约定参考

详见 [docs/后端开发规范.md](../docs/后端开发规范.md) §二、§三。

-- Sprint 1: 交易域 (trade 模块) — 套餐 / 套餐资源池关联 / 订阅
-- 对应 nook-module-trade; 详见 08-Sprint1-套餐订阅-实施设计.md

CREATE TABLE trade_plan (
  id               CHAR(32)      NOT NULL COMMENT '主键ID',
  code             VARCHAR(64)   NOT NULL COMMENT '套餐码 (唯一), 如 jp_tyo_residential_100gb_monthly',
  name             VARCHAR(128)  NOT NULL COMMENT '套餐名',
  region_code      VARCHAR(32)   NOT NULL COMMENT 'FK system_region.code (展示分类)',
  ip_type_id       CHAR(32)               COMMENT 'FK system_ip_type.id (展示分类)',
  traffic_gb       INT           NOT NULL COMMENT '月配额 GB, 写 xray client totalBytes',
  bandwidth_mbps   INT                    COMMENT '账面带宽 Mbps (商品页展示, 不 enforce)',
  period_days      INT           NOT NULL DEFAULT 30 COMMENT '周期天数',
  limit_ip         INT           NOT NULL DEFAULT 0 COMMENT '同时连接 IP 数, 写 xray limitIp; 0=不限',
  price_cny        DECIMAL(10,2) NOT NULL COMMENT '售价 CNY',
  cost_basis_cny   DECIMAL(10,2)          COMMENT '成本核算 CNY',
  enabled          TINYINT       NOT NULL DEFAULT 1 COMMENT '上下架: 1=上架 0=下架',
  remark           VARCHAR(255)           COMMENT '备注',
  created_at       DATETIME      NOT NULL COMMENT '创建时间',
  updated_at       DATETIME      NOT NULL COMMENT '更新时间',
  deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trade_plan_code (code),
  INDEX idx_trade_plan_filter (region_code, ip_type_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐定义';

CREATE TABLE trade_plan_resource (
  id              CHAR(32)     NOT NULL COMMENT '主键ID',
  trade_plan_id   CHAR(32)     NOT NULL COMMENT 'FK trade_plan.id',
  resource_type   ENUM('FRONTLINE','LANDING') NOT NULL COMMENT '门禁(线路机) / 公寓(落地机)',
  resource_id     CHAR(32)     NOT NULL COMMENT 'FK resource_server.id',
  enabled         TINYINT      NOT NULL DEFAULT 1 COMMENT '临时禁用: 1=启用 0=禁用 (老 sub 保留不接新)',
  created_at      DATETIME     NOT NULL COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_res (trade_plan_id, resource_type, resource_id),
  INDEX idx_plan_res (trade_plan_id, resource_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐资源池关联 (SKU ↔ server/landing)';

CREATE TABLE trade_subscription (
  id              CHAR(32)     NOT NULL COMMENT '主键ID',
  member_user_id  CHAR(32)     NOT NULL COMMENT 'FK member_user.id',
  plan_id         CHAR(32)     NOT NULL COMMENT 'FK trade_plan.id',
  xray_client_id  CHAR(32)     NOT NULL COMMENT '1:1 FK xray_client.id',
  started_at      DATETIME     NOT NULL COMMENT '生效时间',
  expires_at      DATETIME     NOT NULL COMMENT '到期时间',
  status          ENUM('ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at      DATETIME     NOT NULL COMMENT '创建时间',
  updated_at      DATETIME     NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sub_xray_client (xray_client_id),
  INDEX idx_sub_member_status (member_user_id, status),
  INDEX idx_sub_expires (expires_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅 (1 sub = 1 套餐 = 1 xray_client)';

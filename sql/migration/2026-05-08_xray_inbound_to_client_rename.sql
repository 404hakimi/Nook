-- ============================================================
-- 迁移: 把 xray_inbound 表重命名为 xray_client, 修正命名错位
-- ============================================================
-- 原因: 该表每行实质 = "一个会员在某条线路+某个落地 IP 上的 client(用户)凭据",
--        历史命名 xray_inbound 误导 (Xray inbound 是监听端口/协议级配置, 由 xray.json 写死,
--        nook 这边只动 inbound 下的 clients[])。
--
-- 字段变化: 仅改表名; 字段名保持 (external_inbound_ref / listen_port / client_uuid 等
--           在 client 视角下含义仍清晰)。
--
-- 全新部署的库直接跑新版 sql/05_xray.sql 即可, 无需此 migration。
-- ============================================================

RENAME TABLE `xray_inbound` TO `xray_client`;

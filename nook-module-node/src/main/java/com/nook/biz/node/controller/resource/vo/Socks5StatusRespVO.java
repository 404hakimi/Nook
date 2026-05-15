package com.nook.biz.node.controller.resource.vo;

import lombok.Data;

/**
 * SOCKS5 (dante) systemd 运行状态 + dante 专属字段 (version / 监听端口); 与 XrayServerStatusRespVO 同口径,
 * 让前端 IpPoolStatusDialog 可以照葫芦画瓢 XrayNodeStatusDialog.
 *
 * @author nook
 */
@Data
public class Socks5StatusRespVO {

    /** 查询的 systemd unit 名 (固定 "danted") */
    private String unit;

    /** systemctl is-active 输出: active / inactive / failed / unknown */
    private String active;

    /** dante 版本; "dpkg-query -W -f='${Version}' dante-server" 取 */
    private String version;

    /** ActiveEnterTimestamp; 服务未起时为空 */
    private String uptimeFrom;

    /** ss -ltn 抓的当前 socks5 端口监听行 (多行字符串, 前端按 \n 拆分展示) */
    private String listening;

    /** systemctl is-enabled 输出: enabled / disabled / static / masked / ... */
    private String enabled;
}

package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.framework.ssh.SshSession;
import com.nook.biz.node.framework.ssh.SshSessionManager;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Xray Stats CLI 客户端 (走 SSH + xray api stats / statsquery); 单条走 stats, 批量走 statsquery.
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayStatsCli {

    @Resource
    private SshSessionManager sshSessionManager;

    /**
     * 读单个 stat 字节数; 不存在 / SSH 抖动 / xray 没起 都视为 0, 不让 stats 失败影响业务.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param statName Xray stat key
     * @param reset    读后是否清零
     * @return stat 字节数 (不存在 / 出错均返 0)
     */
    public long readStat(String serverId, int apiPort, String statName, boolean reset) {
        SshSession session = sshSessionManager.acquire(serverId);
        // 远端用 grep 把 protobuf text 输出 "value: 12345" 提成纯数字, || echo 0 兜底未找到 stat
        // --reset 是 go flag boolean: 只接受 "-reset" (true) 或 "-reset=false"; "-reset false" (空格) 会被当成
        // "-reset" + 位置参数 "false", 实际 reset=true 每次清零! 所以 reset=false 时干脆不传 flag, 走默认.
        String cmd = "xray api stats --server=127.0.0.1:" + apiPort
                + " --name " + ShellEscapeUtils.shellArg(statName)
                + (reset ? " --reset" : "")
                + " 2>/dev/null | grep -oP 'value: \\K[0-9]+' || echo 0";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout().trim();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] readStat 失败 server={} stat={}: {}",
                    serverId, statName, e.getMessage());
            return 0L;
        }
        // 远端 grep 已剥成纯数字, 但 SSH 抖动可能多带回车 / 空白; NumberUtil.isLong 兜底
        return NumberUtil.isLong(stdout) ? Long.parseLong(stdout) : 0L;
    }

    /**
     * 读单个 user 的上下行 + 可选清零, 拼成 XrayUserTrafficSnapshot;
     * 内部走批量 statsquery 一次拿到 (上下行两条 stat) 而不是两次 stats CLI, 省一次 SSH 往返.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param email    user email
     * @param reset    读后是否清零
     * @return XrayUserTrafficSnapshot (counter 不存在 / SSH 抖动均返 (0, 0) 占位)
     */
    public XrayUserTrafficSnapshot readUserTraffic(String serverId, int apiPort, String email, boolean reset) {
        // pattern 限定到这一个 user 的两条 stat, 不会把别人的也带回来
        String pattern = "user>>>" + email + ">>>";
        Map<String, XrayUserTrafficSnapshot> all = readUserTraffics(serverId, apiPort, pattern, reset);
        XrayUserTrafficSnapshot s = all.get(email);
        // counter 没注册 (新 client 还没流量) 或 statsquery 失败时, 仍按老行为返 (0, 0) 占位
        return s != null ? s : new XrayUserTrafficSnapshot(email, 0L, 0L, 0L, 0L, true);
    }

    /**
     * 批量按 pattern 读所有匹配的 user-level 流量 counter (statsquery), 一次 SSH 拿全;
     * 列表 dashboard / 全量对账场景比逐个 readStat 快 N 倍, 也支持 -reset 一并清零.
     *
     * @param serverId resource_server.id
     * @param apiPort  xray 内置 api server 端口
     * @param pattern  statsquery -pattern 参数, 如 "user>>>" 拿所有 user / "user>>>member_xxx_" 按前缀过滤
     * @param reset    读后是否清零所有匹配 counter
     * @return Map&lt;email, snapshot&gt;; 没匹配 / SSH 抖动均返空 map (不抛错)
     */
    public Map<String, XrayUserTrafficSnapshot> readUserTraffics(String serverId, int apiPort,
                                                                 String pattern, boolean reset) {
        SshSession session = sshSessionManager.acquire(serverId);
        // 不带 grep 兜底: statsquery 没匹配也返 exit 0 + stdout 空对象 / 空数组, exit !=0 才是真异常.
        // --reset 同 readStat: go flag boolean 只能 "-reset" 或 "-reset=false" 等号写法; reset=false 不传 flag.
        String cmd = "xray api statsquery --server=127.0.0.1:" + apiPort
                + " --pattern " + ShellEscapeUtils.shellArg(pattern)
                + (reset ? " --reset" : "");
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] readUserTraffics 失败 server={} pattern={}: {}",
                    serverId, pattern, e.getMessage());
            return Collections.emptyMap();
        }
        return parseUserStatsArray(stdout);
    }

    /**
     * 解析 statsquery 输出 JSON 成 email→snapshot map; 输出形态:
     * <pre>
     * { "stat": [
     *     {"name":"user&gt;&gt;&gt;EMAIL&gt;&gt;&gt;traffic&gt;&gt;&gt;uplink","value":"123"},
     *     {"name":"user&gt;&gt;&gt;EMAIL&gt;&gt;&gt;traffic&gt;&gt;&gt;downlink"}    // value 缺失 = 0 (protobuf JSON 默认值省略)
     * ]}
     * </pre>
     * 非 user&gt;&gt;&gt;X&gt;&gt;&gt;traffic&gt;&gt;&gt;{up,down}link 形态的 stat (如 inbound&gt;&gt;&gt;...) 直接跳过, 调用方按 pattern 收口.
     *
     * @param stdout statsquery 远端输出
     * @return Map&lt;email, snapshot&gt;
     */
    private Map<String, XrayUserTrafficSnapshot> parseUserStatsArray(String stdout) {
        if (StrUtil.isBlank(stdout)) return Collections.emptyMap();
        JSONObject root;
        try {
            root = JSON.parseObject(stdout);
        } catch (RuntimeException e) {
            // 远端输出不是合法 JSON, 视为没数据返空; 真出问题 SSH 层早就抛了
            log.warn("[xray-cli] statsquery 输出非 JSON, 截断 200 字: {}",
                    StrUtil.maxLength(stdout, 200));
            return Collections.emptyMap();
        }
        if (root == null) return Collections.emptyMap();
        JSONArray stats = root.getJSONArray("stat");
        if (stats == null || stats.isEmpty()) return Collections.emptyMap();

        // 第一遍按 email 把上下行收齐 (一条 stat 只带其中一个方向, 必须二次合并)
        Map<String, long[]> agg = new HashMap<>();
        for (int i = 0; i < stats.size(); i++) {
            JSONObject s = stats.getJSONObject(i);
            String name = s.getString("name");
            if (StrUtil.isBlank(name)) continue;
            // value 字段在 counter 为 0 时被 protobuf JSON 省略, getLongValue 默认就 0
            long value = s.getLongValue("value", 0L);
            String[] parts = name.split(">>>");
            // 严格匹配 user>>>EMAIL>>>traffic>>>{uplink|downlink}; 不符合不进 map
            if (parts.length != 4 || !"user".equals(parts[0]) || !"traffic".equals(parts[2])) continue;
            String email = parts[1];
            if (StrUtil.isBlank(email)) continue;
            long[] c = agg.computeIfAbsent(email, k -> new long[2]);
            if ("uplink".equals(parts[3])) c[0] = value;
            else if ("downlink".equals(parts[3])) c[1] = value;
        }

        Map<String, XrayUserTrafficSnapshot> out = new HashMap<>(agg.size() * 2);
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long[] c = e.getValue();
            // totalBytes / expiry / enabled 在 nook 模式下由业务侧维护, 远端不维护 (传 0 / true 占位)
            out.put(e.getKey(), new XrayUserTrafficSnapshot(e.getKey(), c[0], c[1], 0L, 0L, true));
        }
        return out;
    }
}

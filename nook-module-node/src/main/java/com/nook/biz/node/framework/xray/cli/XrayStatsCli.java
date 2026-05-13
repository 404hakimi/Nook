package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.framework.ssh.core.SshSession;
import com.nook.biz.node.framework.xray.cli.snapshot.XrayUserTrafficSnapshot;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Xray Stats CLI 客户端 (走 SSH + xray api stats / statsquery); 由 caller 传入已 acquire 的 session
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayStatsCli {

    /** xray stats 命名前缀: user 维度 counter 都以此开头, 与 inbound>>> / outbound>>> 区分 */
    private static final String USER_STAT_PREFIX = "user>>>";

    /** xray stats 命名段分隔符 (user>>>EMAIL>>>traffic>>>uplink) */
    private static final String STAT_SEPARATOR = ">>>";

    /** 预编译分隔符正则; Pattern.quote 防分隔符未来含正则元字符时静默错乱 */
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(Pattern.quote(STAT_SEPARATOR));

    // stat name 四段分量值, 跟 xray protobuf stats schema 对齐
    private static final String STAT_TYPE_USER = "user";
    private static final String STAT_KIND_TRAFFIC = "traffic";
    private static final String STAT_DIRECTION_UPLINK = "uplink";
    private static final String STAT_DIRECTION_DOWNLINK = "downlink";

    /**
     * 读单个 stat 字节数; 不存在 / SSH 抖动 / xray 没起 都视为 0, 静默兜底无 warn
     *
     * @param session  caller 已 acquire 的 SSH 会话
     * @param apiPort  xray 内置 api server 端口
     * @param statName Xray stat key
     * @param reset    读后是否清零
     * @return stat 字节数 (不存在 / 出错均返 0)
     */
    public long readStat(SshSession session, int apiPort, String statName, boolean reset) {
        // 远端用 grep 把 protobuf text 输出 "value: 12345" 提成纯数字, || echo 0 兜底未找到 stat
        // --reset 是 pflag boolean: 只接受 "--reset" (true) 或 "--reset=false"; "--reset false" (空格) 会被当成
        // "--reset" + 位置参数 "false", 实际 reset=true 每次清零! 所以 reset=false 时干脆不传 flag, 走默认
        String cmd = "xray api stats --server=127.0.0.1:" + apiPort
                + " --name " + ShellEscapeUtils.shellArg(statName)
                + (reset ? " --reset" : "")
                + " 2>/dev/null | grep -oP 'value: \\K[0-9]+' || echo 0";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout().trim();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] readStat 失败 server={} stat={}: {}",
                    session.serverId(), statName, e.getMessage());
            return 0L;
        }
        return NumberUtil.isLong(stdout) ? Long.parseLong(stdout) : 0L;
    }

    /**
     * 读单个 user 的上下行 + 可选清零; 走 statsquery 一次拿上下行两条 stat, 省一次 SSH 往返
     *
     * <p><b>注意</b>: reset=true 时若 SSH 中断, xray 可能已 reset 但增量未返回, 该周期数据丢失;
     * 30 min 一周期可接受, 极端环境调用方需自行权衡.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @param email   user email
     * @param reset   读后是否清零
     * @return XrayUserTrafficSnapshot (counter 不存在 / SSH 抖动均返 (0, 0) 占位)
     */
    public XrayUserTrafficSnapshot readUserTraffic(SshSession session, int apiPort, String email, boolean reset) {
        // pattern 末尾 STAT_SEPARATOR 保证 email 字段完整匹配, 不被前缀同名 email 误命中
        String pattern = USER_STAT_PREFIX + email + STAT_SEPARATOR;
        Map<String, XrayUserTrafficSnapshot> all = queryByPattern(session, apiPort, pattern, reset);
        XrayUserTrafficSnapshot s = all.get(email);
        // counter 没注册 (新 client 还没流量) 或 statsquery 失败时, 返 (0, 0) 占位
        return s != null ? s : new XrayUserTrafficSnapshot(email, 0L, 0L, 0L, 0L, true);
    }

    /**
     * 读该 server 上**全部 user** 的上下行 + 可选清零; 定时 sweep / 全量对账场景用
     *
     * <p><b>注意</b>: reset=true 时若 SSH 中断, 同 {@link #readUserTraffic} 警告.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @param reset   读后是否清零所有 user counter
     * @return Map&lt;email, snapshot&gt;; 没匹配 / SSH 抖动均返空 map (不抛错)
     */
    public Map<String, XrayUserTrafficSnapshot> readAllUserTraffics(SshSession session, int apiPort, boolean reset) {
        return queryByPattern(session, apiPort, USER_STAT_PREFIX, reset);
    }

    /** 内部统一查询: xray api statsquery --pattern; 失败返空 map 不抛错 */
    private Map<String, XrayUserTrafficSnapshot> queryByPattern(SshSession session, int apiPort,
                                                                String pattern, boolean reset) {
        // statsquery 没匹配也返 exit 0 + 空对象, 不需要 grep 兜底
        String cmd = "xray api statsquery --server=127.0.0.1:" + apiPort
                + " --pattern " + ShellEscapeUtils.shellArg(pattern)
                + (reset ? " --reset" : "");
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] statsquery 失败 server={} pattern={}: {}",
                    session.serverId(), pattern, e.getMessage());
            return Collections.emptyMap();
        }
        return parseUserStatsArray(stdout);
    }

    /** 解析 statsquery 输出: stat[].name 形如 user&gt;&gt;&gt;EMAIL&gt;&gt;&gt;traffic&gt;&gt;&gt;{uplink|downlink} */
    private Map<String, XrayUserTrafficSnapshot> parseUserStatsArray(String stdout) {
        if (StrUtil.isBlank(stdout)) return Collections.emptyMap();
        JSONObject root;
        try {
            root = JSON.parseObject(stdout);
        } catch (RuntimeException e) {
            log.warn("[xray-cli] statsquery 输出非 JSON: {}", StrUtil.maxLength(stdout, 200));
            return Collections.emptyMap();
        }
        if (root == null) return Collections.emptyMap();
        JSONArray stats = root.getJSONArray("stat");
        if (stats == null || stats.isEmpty()) return Collections.emptyMap();

        // 一条 stat 只带其中一个方向, 按 email 二次聚合上下行
        Map<String, long[]> agg = new HashMap<>();
        for (int i = 0; i < stats.size(); i++) {
            JSONObject s = stats.getJSONObject(i);
            String name = s.getString("name");
            if (StrUtil.isBlank(name)) continue;
            // protobuf JSON 把 int64 编码为字符串 ("12345"); 显式解析锁死契约, 不依赖 fastjson 隐式类型转换
            long value = parseLongLoose(s.get("value"));
            // limit=-1 保留尾部空串以便严格判定段数; SEPARATOR_PATTERN 已 Pattern.quote 防元字符歧义
            String[] parts = SEPARATOR_PATTERN.split(name, -1);
            // 严格匹配 user>>>EMAIL>>>traffic>>>{uplink|downlink}
            if (parts.length != 4 || !STAT_TYPE_USER.equals(parts[0]) || !STAT_KIND_TRAFFIC.equals(parts[2])) continue;
            String email = parts[1];
            if (StrUtil.isBlank(email)) continue;
            long[] c = agg.computeIfAbsent(email, k -> new long[2]);
            if (STAT_DIRECTION_UPLINK.equals(parts[3])) c[0] = value;
            else if (STAT_DIRECTION_DOWNLINK.equals(parts[3])) c[1] = value;
        }

        Map<String, XrayUserTrafficSnapshot> out = new HashMap<>(agg.size());
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long[] c = e.getValue();
            // totalBytes / expiry / enabled 由业务侧维护, 远端不维护 (传 0 / true 占位)
            out.put(e.getKey(), new XrayUserTrafficSnapshot(e.getKey(), c[0], c[1], 0L, 0L, true));
        }
        return out;
    }

    /** 把 protobuf JSON 的 value 字段 (Number / String / null) 显式解析为 long; 解析失败返 0 */
    private static long parseLongLoose(Object raw) {
        if (raw == null) return 0L;
        if (raw instanceof Number n) return n.longValue();
        String s = raw.toString().trim();
        return s.isEmpty() || !NumberUtil.isLong(s) ? 0L : Long.parseLong(s);
    }
}

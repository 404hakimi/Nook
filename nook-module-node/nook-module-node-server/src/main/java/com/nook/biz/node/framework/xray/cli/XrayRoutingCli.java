package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.api.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xray routing rules 动态增删 CLI (xray api adrules --append / rmrules).
 *
 * @author nook
 */
@Slf4j
@Component
public class XrayRoutingCli {

    /**
     * 加一条 routing rule (xray api adrules --append).
     *
     * @param session     caller 已 acquire 的 SSH 会话
     * @param apiPort     xray 内置 api server 端口
     * @param ruleTag     rule 唯一标识 (revoke 时按 tag 删)
     * @param userEmails  匹配的 user email 列表 (一般单元素)
     * @param outboundTag 路由命中后走的 outbound tag
     */
    public void addRule(SshSession session, String xrayBin, int apiPort, String ruleTag,
                        List<String> userEmails, String outboundTag) {
        String json = buildAddRuleJson(ruleTag, userEmails, outboundTag);
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        // --append 让 xray 把新规则 append 到现有 routing rules 末尾, 不替换全部;
        // stdin: 是 xray-core 文档化语法 (跟 adu/adi/ado 一致), 不依赖 adrules 在空 args 时的隐式 fallback.
        String cmd = "echo '" + b64 + "' | base64 -d | " + xrayBin + " api adrules --server=127.0.0.1:" + apiPort
                + " --append stdin:";
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] addRule server={} ruleTag={} users={} outbound={}",
                    session.serverId(), ruleTag, userEmails, outboundTag);
        } catch (BusinessException be) {
            String msg = StrUtil.blankToDefault(be.getMessage(), "");
            log.warn("[xray-cli] addRule 失败 server={} ruleTag={} stderr={}",
                    session.serverId(), ruleTag, msg);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                    session.serverId(), "addRule: " + StrUtil.maxLength(msg, 200));
        }
    }

    /**
     * 列远端全部 routing rule tag (xray api lsrules); 用于 sync-status 对账.
     *
     * <p>xray 内置 {@code api} 通道也是一条 rule, 调用方需自行过滤 (见 XrayConstants.BUILTIN_API_RULE_TAG).
     * 无 tag 的 rule 会被 jq 跳过, 不出现在结果里.
     *
     * @param session caller 已 acquire 的 SSH 会话
     * @param apiPort xray 内置 api server 端口
     * @return rule tag 集合
     * @throws BusinessException SSH / xray 不可用; 调用方应放弃本轮对账
     */
    public Set<String> listRuleTags(SshSession session, String xrayBin, int apiPort) {
        // 关键: 读 .ruleTag (规则身份, 我们 adrules 时设的), 不是 .tag (规则命中后的 outboundTag 引用).
        // 旧实现取 .tag 等价于把 outbound 当 rule 名, 跟 DB 期望的 rule_<clientId> 永远对不上, 误报缺 rule.
        // 内置 api routing rule 没 .ruleTag 字段, jq // empty 自然跳过, 不需要再业务侧过滤.
        String cmd = xrayBin + " api lsrules --server=127.0.0.1:" + apiPort
                + " | jq -r '.rules[]?.ruleTag // empty'";
        String stdout;
        try {
            stdout = session.ssh().exec(cmd).getStdout();
        } catch (RuntimeException e) {
            log.warn("[xray-cli] listRuleTags 失败 server={}: {}",
                    session.serverId(), e.getMessage());
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, e,
                    session.serverId(), "listRuleTags: " + StrUtil.maxLength(e.getMessage(), 200));
        }
        if (StrUtil.isBlank(stdout)) return Collections.emptySet();
        return Arrays.stream(stdout.split("\\R"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 删 routing rule (xray api rmrules <ruleTag>).
     * rule 不存在静默通过, 跟 outbound rmo 一致策略.
     */
    public void removeRule(SshSession session, String xrayBin, int apiPort, String ruleTag) {
        String cmd = xrayBin + " api rmrules --server=127.0.0.1:" + apiPort + " "
                + ShellEscapeUtils.shellArg(ruleTag);
        try {
            session.ssh().exec(cmd);
            log.info("[xray-cli] removeRule server={} ruleTag={}", session.serverId(), ruleTag);
        } catch (BusinessException be) {
            String msg = StrUtil.blankToDefault(be.getMessage(), "");
            if (StrUtil.containsAnyIgnoreCase(msg, "not found", "no such")) {
                log.info("[xray-cli] removeRule ruleTag={} 已不存在 (幂等通过)", ruleTag);
                return;
            }
            log.warn("[xray-cli] removeRule 失败 server={} ruleTag={} stderr={}",
                    session.serverId(), ruleTag, msg);
            throw new BusinessException(XrayErrorCode.BACKEND_OPERATION_FAILED, be,
                    session.serverId(), "removeRule: " + StrUtil.maxLength(msg, 200));
        }
    }

    /** 渲染 adrules 入参 JSON: {"routing": {"rules": [{ruleTag, user, outboundTag}]}}. */
    private String buildAddRuleJson(String ruleTag, List<String> userEmails, String outboundTag) {
        JSONArray users = new JSONArray();
        users.addAll(userEmails);

        JSONObject rule = new JSONObject();
        rule.put("ruleTag", ruleTag);
        rule.put("user", users);
        rule.put("outboundTag", outboundTag);

        JSONArray rules = new JSONArray();
        rules.add(rule);

        JSONObject routing = new JSONObject();
        routing.put("rules", rules);

        JSONObject config = new JSONObject();
        config.put("routing", routing);
        return config.toJSONString();
    }
}

package com.nook.biz.node.framework.xray.cli;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nook.biz.node.enums.XrayErrorCode;
import com.nook.biz.node.framework.xray.cli.utils.ShellEscapeUtils;
import com.nook.common.web.exception.BusinessException;
import com.nook.framework.ssh.core.SshSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Xray routing rules 动态增删 CLI (xray api adrules --append / rmrules).
 * 1:N 模型: provision 时按 user 加路由, revoke 时按 ruleTag 删.
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
    public void addRule(SshSession session, int apiPort, String ruleTag,
                        List<String> userEmails, String outboundTag) {
        String json = buildAddRuleJson(ruleTag, userEmails, outboundTag);
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        // --append 让 xray 把新规则 append 到现有 routing rules 末尾, 不替换全部
        String cmd = "echo '" + b64 + "' | base64 -d | xray api adrules --server=127.0.0.1:" + apiPort
                + " --append";
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
     * 删 routing rule (xray api rmrules <ruleTag>).
     * rule 不存在静默通过, 跟 outbound rmo 一致策略.
     */
    public void removeRule(SshSession session, int apiPort, String ruleTag) {
        String cmd = "xray api rmrules --server=127.0.0.1:" + apiPort + " "
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

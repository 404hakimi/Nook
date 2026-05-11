package com.nook.biz.system.enricher;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.operation.api.OpLogEnricher;
import com.nook.biz.operation.controller.vo.OpLogRespVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.biz.system.mapper.SystemUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 op_log.operator (system_user.id) 解析成 realName / username 回填到 operatorName.
 *
 * <p>"SYSTEM" / "SCHEDULER" 等系统调度占位符不查库, 直接展示原值.
 * 一次性 IN 查避免 N+1.
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class SystemUserOpLogEnricher implements OpLogEnricher {

    /** 系统侧占位 operator; 不进 DB 查询直接返字面值. */
    private static final Set<String> SYSTEM_PLACEHOLDERS = Set.of(
            "SYSTEM", "SCHEDULER", "ADMIN", "WORKER", "WATCHDOG");

    private final SystemUserMapper systemUserMapper;

    @Override
    public void enrich(List<OpLogRespVO> vos) {
        // 抽出需要查库的 id 集合 (跳过占位符 / 空 / 已 enrich 过)
        Set<String> ids = new HashSet<>();
        for (OpLogRespVO vo : vos) {
            String op = vo.getOperator();
            if (StrUtil.isBlank(op) || SYSTEM_PLACEHOLDERS.contains(op)) continue;
            if (StrUtil.isNotBlank(vo.getOperatorName())) continue;
            ids.add(op);
        }
        Map<String, String> idToName = new HashMap<>(ids.size() * 2);
        if (!ids.isEmpty()) {
            List<SystemUser> users = systemUserMapper.selectList(
                    Wrappers.<SystemUser>lambdaQuery().in(SystemUser::getId, ids));
            for (SystemUser u : users) {
                // realName 优先, 缺失退回 username (登录账号)
                String name = StrUtil.blankToDefault(u.getRealName(), u.getUsername());
                idToName.put(u.getId(), name);
            }
        }
        for (OpLogRespVO vo : vos) {
            String op = vo.getOperator();
            if (StrUtil.isBlank(op)) continue;
            if (SYSTEM_PLACEHOLDERS.contains(op)) {
                // 直接把占位符当 display name (UI 自己决定怎么展示中文)
                vo.setOperatorName(op);
                continue;
            }
            if (StrUtil.isBlank(vo.getOperatorName())) {
                vo.setOperatorName(idToName.getOrDefault(op, op));
            }
        }
    }
}

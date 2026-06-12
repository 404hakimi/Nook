package com.nook.biz.agent.api.impl;

import com.nook.biz.agent.api.AgentTokenApi;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Agent 鉴权 Token Api 实现类
 *
 * @author nook
 */
@Service
public class AgentTokenApiImpl implements AgentTokenApi {

    @Override
    public String generateToken() {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes()));
        } catch (Exception e) {
            // INTERNAL_ERROR 消息模板无 %s 占位, 详情走 cause 保留堆栈
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, e);
        }
    }
}

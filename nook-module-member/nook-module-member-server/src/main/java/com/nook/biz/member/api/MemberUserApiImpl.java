package com.nook.biz.member.api;

import cn.hutool.core.util.StrUtil;
import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link MemberUserApi} 实现.
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class MemberUserApiImpl implements MemberUserApi {

    /** member_user.status: 1=正常. */
    private static final Integer STATUS_ACTIVE = 1;

    private final MemberUserMapper memberUserMapper;

    @Override
    public MemberSubscriberDTO getActiveBySubToken(String subToken) {
        if (StrUtil.isBlank(subToken)) {
            return null;
        }
        MemberUser member = memberUserMapper.selectBySubToken(subToken);
        if (member == null || !STATUS_ACTIVE.equals(member.getStatus())) {
            return null;
        }
        MemberSubscriberDTO dto = new MemberSubscriberDTO();
        dto.setId(member.getId());
        dto.setEmail(member.getEmail());
        return dto;
    }

    @Override
    public Map<String, String> getEmailMap(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return memberUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MemberUser::getId, MemberUser::getEmail));
    }
}

package com.nook.biz.member.api;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.member.api.enums.MemberUserStatusEnum;
import com.nook.biz.member.entity.MemberUser;
import com.nook.biz.member.mapper.MemberUserMapper;
import com.nook.common.utils.collection.CollectionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会员 Api 实现类
 *
 * @author nook
 */
@Service
public class MemberUserApiImpl implements MemberUserApi {

    @Resource
    private MemberUserMapper memberUserMapper;

    @Override
    public MemberSubscriberDTO getActiveBySubToken(String subToken) {
        if (StrUtil.isBlank(subToken)) {
            return null;
        }
        // 按订阅 token 查会员
        MemberUser member = memberUserMapper.selectBySubToken(subToken);
        // token 无效或会员非正常状态都视为不可用
        if (ObjectUtil.isNull(member) || !MemberUserStatusEnum.NORMAL.matches(member.getStatus())) {
            return null;
        }
        // 转换返回
        return MemberUserApiConvert.INSTANCE.toSubscriberDTO(member);
    }

    @Override
    public Map<String, String> getEmailMap(Collection<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        // 批量查会员
        List<MemberUser> members = memberUserMapper.selectBatchIds(ids);
        // 提取会员ID → 邮箱
        return CollectionUtils.convertMap(members, MemberUser::getId, MemberUser::getEmail);
    }
}

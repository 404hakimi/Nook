package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.ResourceServerCredentialDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 服务器 SSH 凭据 Mapper
 *
 * @author nook
 */
@Mapper
public interface ResourceServerCredentialMapper extends BaseMapper<ResourceServerCredentialDO> {

    default int updateBySelective(ResourceServerCredentialDO patch) {
        return update(patch, Wrappers.<ResourceServerCredentialDO>lambdaUpdate()
                .set(ResourceServerCredentialDO::getUpdatedAt, LocalDateTime.now())
                .eq(ResourceServerCredentialDO::getServerId, patch.getServerId()));
    }
}

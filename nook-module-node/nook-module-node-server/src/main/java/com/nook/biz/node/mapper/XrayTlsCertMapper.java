package com.nook.biz.node.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.entity.XrayTlsCertDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xray TLS 证书 Mapper
 *
 * @author nook
 */
@Mapper
public interface XrayTlsCertMapper extends BaseMapper<XrayTlsCertDO> {

    default List<XrayTlsCertDO> selectExpiringBefore(LocalDateTime before) {
        return selectList(Wrappers.<XrayTlsCertDO>lambdaQuery()
                .isNotNull(XrayTlsCertDO::getNotAfter)
                .lt(XrayTlsCertDO::getNotAfter, before));
    }
}

package com.nook.biz.node.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.node.dal.dataobject.node.XrayConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Xray inbound 共享配置 Mapper
 *
 * @author nook
 */
@Mapper
public interface XrayConfigMapper extends BaseMapper<XrayConfigDO> {
}

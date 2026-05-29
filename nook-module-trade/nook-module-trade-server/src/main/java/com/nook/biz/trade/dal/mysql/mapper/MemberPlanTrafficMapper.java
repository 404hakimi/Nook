package com.nook.biz.trade.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.trade.dal.dataobject.MemberPlanTrafficDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅流量计量 Mapper.
 *
 * @author nook
 */
@Mapper
public interface MemberPlanTrafficMapper extends BaseMapper<MemberPlanTrafficDO> {
}

package com.nook.biz.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nook.biz.system.entity.SystemUser;
import org.apache.ibatis.annotations.Mapper;

/** SystemUser 数据访问层。 */
@Mapper
public interface SystemUserMapper extends BaseMapper<SystemUser> {
}

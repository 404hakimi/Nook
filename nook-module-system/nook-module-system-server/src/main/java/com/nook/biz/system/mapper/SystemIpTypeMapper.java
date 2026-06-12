package com.nook.biz.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.entity.SystemIpTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * IP 类型 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemIpTypeMapper extends BaseMapper<SystemIpTypeDO> {

    default List<SystemIpTypeDO> selectAllOrdered() {
        return selectList(Wrappers.<SystemIpTypeDO>lambdaQuery()
                .orderByAsc(SystemIpTypeDO::getSortOrder));
    }
}

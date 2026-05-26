package com.nook.biz.system.dal.mysql.mapper.iptype;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.system.dal.dataobject.iptype.SystemIpTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * IP 类型 Mapper
 *
 * @author nook
 */
@Mapper
public interface SystemIpTypeMapper extends BaseMapper<SystemIpTypeDO> {

    /** 全量列出, 按 sort_order 升序; 给运营在 IP 池录入 / 套餐配置时下拉用. */
    default List<SystemIpTypeDO> selectAllOrdered() {
        return selectList(Wrappers.<SystemIpTypeDO>lambdaQuery()
                .orderByAsc(SystemIpTypeDO::getSortOrder));
    }

    default SystemIpTypeDO selectByCode(String code) {
        return selectOne(Wrappers.<SystemIpTypeDO>lambdaQuery()
                .eq(SystemIpTypeDO::getCode, code)
                .last("LIMIT 1"));
    }
}

package com.nook.biz.operation.dal.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.operation.dal.dataobject.OpConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * op_config 数据访问
 *
 * @author nook
 */
@Mapper
public interface OpConfigMapper extends BaseMapper<OpConfigDO> {

    default OpConfigDO selectByOpType(String opType) {
        return selectOne(Wrappers.<OpConfigDO>lambdaQuery().eq(OpConfigDO::getOpType, opType));
    }

    default List<OpConfigDO> selectAllOrdered() {
        return selectList(Wrappers.<OpConfigDO>lambdaQuery().orderByAsc(OpConfigDO::getOpType));
    }
}

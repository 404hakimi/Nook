package com.nook.biz.operation.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.operation.api.OpStatus;
import com.nook.biz.operation.dal.dataobject.OpLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * op_log UI/后台查询; 跟 {@link OpLogMapper} 共表不同接口.
 *
 * <p>{@link OpLogMapper} 是 orchestrator 内核用的状态机 CAS 原语 (insert / status 切换 / cleanup);
 * 本 mapper 只放 list/detail/筛选 类查询, 业务表面用. 两边各干各的, 互不污染.
 *
 * @author nook
 */
@Mapper
public interface OpLogQueryMapper extends BaseMapper<OpLogDO> {

    /**
     * 分页查询: status / serverId / opType 都是可选过滤; 按 enqueued_at DESC 排序.
     *
     * @param page     分页参数
     * @param status   状态过滤; null = 不过滤
     * @param serverId server 过滤; blank = 不过滤
     * @param opType   操作类型过滤 (OpType.name() 字符串); blank = 不过滤
     * @return 分页结果
     */
    default IPage<OpLogDO> selectPageByQuery(IPage<OpLogDO> page,
                                           OpStatus status,
                                           String serverId,
                                           String opType) {
        return selectPage(page, Wrappers.<OpLogDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(status), OpLogDO::getStatus, status)
                .eq(StrUtil.isNotBlank(serverId), OpLogDO::getServerId, serverId)
                .eq(StrUtil.isNotBlank(opType), OpLogDO::getOpType, opType)
                .orderByDesc(OpLogDO::getEnqueuedAt));
    }
}

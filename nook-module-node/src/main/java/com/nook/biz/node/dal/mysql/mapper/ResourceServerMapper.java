package com.nook.biz.node.dal.mysql.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.node.controller.resource.server.vo.ResourceServerPageReqVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * ResourceServerDO 数据访问.
 *
 * @author nook
 */
@Mapper
public interface ResourceServerMapper extends BaseMapper<ResourceServerDO> {

    default boolean existsByName(String name) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getName, name));
    }

    default boolean existsByNameExcludingId(String name, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getName, name)
                .ne(ResourceServerDO::getId, excludeId));
    }

    default boolean existsByHost(String host) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getHost, host));
    }

    default boolean existsByHostExcludingId(String host, String excludeId) {
        return exists(Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ResourceServerDO::getHost, host)
                .ne(ResourceServerDO::getId, excludeId));
    }

    /** 分页; keyword 模糊匹 name/host; status / region 精确过滤; 按 createdAt 倒序. */
    default IPage<ResourceServerDO> selectPageByQuery(IPage<ResourceServerDO> page, ResourceServerPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceServerDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), ResourceServerDO::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceServerDO::getRegion, reqVO.getRegion())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(ResourceServerDO::getName, reqVO.getKeyword())
                        .or().like(ResourceServerDO::getHost, reqVO.getKeyword()))
                .orderByDesc(ResourceServerDO::getCreatedAt));
    }
}

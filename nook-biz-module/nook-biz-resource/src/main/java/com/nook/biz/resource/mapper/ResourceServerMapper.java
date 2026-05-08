package com.nook.biz.resource.mapper;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nook.biz.resource.controller.server.vo.ResourceServerPageReqVO;
import com.nook.biz.resource.entity.ResourceServer;
import org.apache.ibatis.annotations.Mapper;

/** ResourceServer 数据访问；查询/更新 Wrapper 收敛在此。 */
@Mapper
public interface ResourceServerMapper extends BaseMapper<ResourceServer> {

    default boolean existsByName(String name) {
        return exists(Wrappers.<ResourceServer>lambdaQuery()
                .eq(ResourceServer::getName, name));
    }

    default boolean existsByNameExcludingId(String name, String excludeId) {
        return exists(Wrappers.<ResourceServer>lambdaQuery()
                .eq(ResourceServer::getName, name)
                .ne(ResourceServer::getId, excludeId));
    }

    default boolean existsByHost(String host) {
        return exists(Wrappers.<ResourceServer>lambdaQuery()
                .eq(ResourceServer::getHost, host));
    }

    default boolean existsByHostExcludingId(String host, String excludeId) {
        return exists(Wrappers.<ResourceServer>lambdaQuery()
                .eq(ResourceServer::getHost, host)
                .ne(ResourceServer::getId, excludeId));
    }

    /** 列表分页：keyword 模糊匹配 name/host；status / region 精确过滤。 */
    default IPage<ResourceServer> selectPageByQuery(IPage<ResourceServer> page, ResourceServerPageReqVO reqVO) {
        return selectPage(page, Wrappers.<ResourceServer>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), ResourceServer::getStatus, reqVO.getStatus())
                .eq(StrUtil.isNotBlank(reqVO.getRegion()), ResourceServer::getRegion, reqVO.getRegion())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(ResourceServer::getName, reqVO.getKeyword())
                        .or().like(ResourceServer::getHost, reqVO.getKeyword()))
                .orderByDesc(ResourceServer::getCreatedAt));
    }
}

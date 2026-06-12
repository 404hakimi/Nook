package com.nook.biz.system.service;

import com.nook.biz.system.controller.domain.vo.SystemDomainCreateReqVO;
import com.nook.biz.system.controller.domain.vo.SystemDomainUpdateReqVO;
import com.nook.biz.system.entity.SystemDomainDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 系统域名 Service 接口
 *
 * @author nook
 */
public interface SystemDomainService {

    /**
     * 获得域名列表 (创建倒序)
     *
     * @return 域名列表
     */
    List<SystemDomainDO> getDomainList();

    /**
     * 获得域名详情
     *
     * @param id 域名ID
     * @return 域名信息
     */
    SystemDomainDO getDomain(String id);

    /**
     * 创建域名
     *
     * @param reqVO 创建信息
     * @return 域名ID
     */
    String createDomain(SystemDomainCreateReqVO reqVO);

    /**
     * 更新域名
     *
     * @param id    域名ID
     * @param reqVO 更新信息
     */
    void updateDomain(String id, SystemDomainUpdateReqVO reqVO);

    /**
     * 删除域名
     *
     * @param id 域名ID
     */
    void deleteDomain(String id);

    /**
     * 根据ID批量查询域名 (key=域名ID, value=根域名; 缺失不进 map)
     *
     * @param ids 域名ID集合
     * @return Map<String, String>
     */
    Map<String, String> getDomainMap(Collection<String> ids);
}

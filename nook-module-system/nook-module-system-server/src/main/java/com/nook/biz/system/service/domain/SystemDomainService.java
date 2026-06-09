package com.nook.biz.system.service.domain;

import com.nook.biz.system.controller.domain.vo.SystemDomainSaveReqVO;
import com.nook.biz.system.dal.dataobject.domain.SystemDomainDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 系统域名 Service 接口
 *
 * @author nook
 */
public interface SystemDomainService {

    /** 域名列表 (创建倒序). */
    List<SystemDomainDO> getDomainList();

    /** 按 id 获得域名; 不存在抛 BusinessException. */
    SystemDomainDO getDomain(String id);

    /** 创建域名 (域名全局唯一); 返回新 id. */
    String createDomain(SystemDomainSaveReqVO reqVO);

    /** 更新域名 (域名全局唯一, 排除自身). */
    void updateDomain(SystemDomainSaveReqVO reqVO);

    /** 删除域名. */
    void deleteDomain(String id);

    /** 批量 id→域名 (跨模块展示用). */
    Map<String, String> loadDomainMap(Collection<String> ids);
}

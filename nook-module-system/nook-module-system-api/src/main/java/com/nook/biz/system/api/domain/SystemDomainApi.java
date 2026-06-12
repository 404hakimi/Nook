package com.nook.biz.system.api.domain;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * 系统域名 Api 接口
 *
 * @author nook
 */
public interface SystemDomainApi {

    /**
     * 按 id 获得域名配置; 不存在抛异常
     *
     * @param id 域名ID
     * @return 域名配置
     */
    SystemDomainRespDTO getById(String id);

    /**
     * 根据ID批量查询域名 (key=域名ID, value=根域名; 缺失不进 map)
     *
     * @param ids 域名ID集合
     * @return Map<String, String>
     */
    Map<String, String> getDomainMap(Collection<String> ids);
}

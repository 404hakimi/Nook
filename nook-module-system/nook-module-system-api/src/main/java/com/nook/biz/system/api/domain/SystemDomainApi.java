package com.nook.biz.system.api.domain;

import com.nook.biz.system.api.domain.dto.SystemDomainRespDTO;

import java.util.Collection;
import java.util.Map;

/**
 * 系统域名 Api 接口 (node 跨模块: 装机取 domain + CF 配置, 展示取域名)
 *
 * @author nook
 */
public interface SystemDomainApi {

    /**
     * 按 id 获得域名配置 (含 cfApiToken, 装机用)
     *
     * @param id 域名编号
     * @return 域名配置; 不存在抛 BusinessException
     */
    SystemDomainRespDTO getById(String id);

    /**
     * 批量 id→域名 (展示用)
     *
     * @param ids 域名编号集合
     * @return key=id, value=域名; 缺失 id 不在 map 里
     */
    Map<String, String> getDomainMap(Collection<String> ids);
}

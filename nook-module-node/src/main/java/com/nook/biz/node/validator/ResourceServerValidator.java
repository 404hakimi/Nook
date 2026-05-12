package com.nook.biz.node.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.enums.ResourceErrorCode;
import com.nook.biz.node.dal.dataobject.resource.ResourceServerDO;
import com.nook.biz.node.dal.mysql.mapper.ResourceServerMapper;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务器资源业务校验.
 *
 * @author nook
 */
@Component
public class ResourceServerValidator {

    @Resource
    private ResourceServerMapper resourceServerMapper;

    /**
     * 校验服务器存在; 不存在抛 SERVER_NOT_FOUND.
     *
     * @param id resource_server.id
     * @return ResourceServerDO
     */
    public ResourceServerDO validateExists(String id) {
        ResourceServerDO e = resourceServerMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验别名全局唯一.
     *
     * @param id   当前行 id (Update 传, Create 传 null 表示不排除自身)
     * @param name 别名
     */
    public void validateNameUnique(String id, String name) {
        boolean dup = id == null
                ? resourceServerMapper.existsByName(name)
                : resourceServerMapper.existsByNameExcludingId(name, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_NAME_DUPLICATE, name);
        }
    }

    /**
     * 校验主机全局唯一.
     *
     * @param id   当前行 id (Update 传, Create 传 null 表示不排除自身)
     * @param host 主机
     */
    public void validateHostUnique(String id, String host) {
        boolean dup = id == null
                ? resourceServerMapper.existsByHost(host)
                : resourceServerMapper.existsByHostExcludingId(host, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_HOST_DUPLICATE, host);
        }
    }
}

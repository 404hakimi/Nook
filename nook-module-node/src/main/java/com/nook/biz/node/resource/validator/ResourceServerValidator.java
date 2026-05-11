package com.nook.biz.node.resource.validator;

import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.resource.constant.ResourceErrorCode;
import com.nook.biz.node.resource.entity.ResourceServer;
import com.nook.biz.node.resource.mapper.ResourceServerMapper;
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
     * 校验服务器存在.
     *
     * @param id resource_server.id
     * @return ResourceServer
     */
    public ResourceServer validateExists(String id) {
        ResourceServer e = resourceServerMapper.selectById(id);
        if (ObjectUtil.isNull(e)) {
            throw new BusinessException(ResourceErrorCode.SERVER_NOT_FOUND, id);
        }
        return e;
    }

    /**
     * 校验别名在全局唯一; id 传当前行 id 用于排除自身 (Update), Create 传 null.
     *
     * @param id   当前行 id (Create 传 null)
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
     * 校验主机 (host) 在全局唯一; id 用于排除自身 (Update), Create 传 null.
     *
     * @param id   当前行 id (Create 传 null)
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

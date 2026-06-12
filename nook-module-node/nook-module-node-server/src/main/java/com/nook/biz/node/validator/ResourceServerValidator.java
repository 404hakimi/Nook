package com.nook.biz.node.validator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.nook.biz.node.api.enums.ResourceErrorCode;
import com.nook.biz.node.api.enums.ResourceServerLifecycleEnum;
import com.nook.biz.node.api.enums.ResourceServerTypeEnum;
import com.nook.biz.node.entity.ResourceServerDO;
import com.nook.biz.node.mapper.ResourceServerMapper;
import com.nook.biz.trade.api.SubscriptionCertApi;
import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 服务器资源业务校验
 *
 * @author nook
 */
@Component
public class ResourceServerValidator {

    @Resource
    private ResourceServerMapper resourceServerMapper;
    @Resource
    private SubscriptionCertApi subscriptionCertApi;

    /**
     * 校验服务器存在
     *
     * @param id 服务器ID
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
     * 校验别名全局唯一
     *
     * @param id   当前服务器ID (更新时传, 新增传 null 表示不排除自身)
     * @param name 别名
     */
    public void validateNameUnique(String id, String name) {
        boolean dup = ObjectUtil.isNull(id)
                ? resourceServerMapper.existsByName(name)
                : resourceServerMapper.existsByNameExcludingId(name, id);
        if (dup) {
            throw new BusinessException(ResourceErrorCode.SERVER_NAME_DUPLICATE, name);
        }
    }

    /**
     * 校验服务器角色取值在枚举范围内
     *
     * @param serverType 服务器角色
     */
    public void validateServerType(String serverType) {
        if (ObjectUtil.isNull(ResourceServerTypeEnum.fromState(serverType))) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "未知 serverType: " + serverType);
        }
    }

    /**
     * 校验生命周期状态取值在枚举范围内
     *
     * @param state 生命周期状态
     */
    public void validateLifecycleState(String state) {
        if (ObjectUtil.isNull(ResourceServerLifecycleEnum.fromState(state))) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "未知 lifecycleState: " + state);
        }
    }

    /**
     * 校验服务器未被生效凭证绑定 (线路机看候选组含该机的凭证含备机, 落地机看 ip_id); 删服务器前置守卫.
     *
     * @param serverId 服务器ID
     */
    public void validateNoBoundClient(String serverId) {
        boolean bound = CollUtil.isNotEmpty(subscriptionCertApi.listActiveByServerInGroup(serverId))
                || ObjectUtil.isNotNull(subscriptionCertApi.getByIp(serverId));
        if (bound) {
            throw new BusinessException(ResourceErrorCode.SERVER_HAS_BOUND_CLIENT, serverId);
        }
    }

    /**
     * 校验区域可改: 仅装机中 / 待上线允许改区域; 上线后区域是套餐与机器的匹配依据, 锁定
     *
     * @param server    当前服务器
     * @param newRegion 目标区域码
     */
    public void validateRegionMutable(ResourceServerDO server, String newRegion) {
        // 区域未变化直接放行
        if (ObjectUtil.equal(server.getRegion(), newRegion)) {
            return;
        }
        // 仅装机中 / 待上线可改区域; 运行中 / 已退役锁定
        boolean mutable = ResourceServerLifecycleEnum.INSTALLING.matches(server.getLifecycleState())
                || ResourceServerLifecycleEnum.READY.matches(server.getLifecycleState());
        if (!mutable) {
            throw new BusinessException(ResourceErrorCode.SERVER_REGION_LOCKED);
        }
    }
}

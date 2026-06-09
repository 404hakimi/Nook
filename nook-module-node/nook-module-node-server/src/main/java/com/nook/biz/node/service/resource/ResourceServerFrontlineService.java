package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.frontline.ResourceServerPageReqVO;
import com.nook.biz.node.controller.resource.vo.frontline.ServerFrontlineListItemRespVO;
import com.nook.common.web.response.PageResult;

/**
 * 线路机扩展 Service 接口
 *
 * @author nook
 */
public interface ResourceServerFrontlineService {

    /**
     * 线路机分页 (连表出运行时聚合视图: 在线态 / 版本 / 配额 / 流量 / xray)
     *
     * @param reqVO 分页条件
     * @return 列表项视图分页
     */
    PageResult<ServerFrontlineListItemRespVO> getFrontlinePage(ResourceServerPageReqVO reqVO);

    /**
     * 服务器运行时详情视图 (线路机 / 落地机共用)
     *
     * @param serverId 服务器编号
     * @return 运行时详情视图 (不存在返 null)
     */
    ServerFrontlineListItemRespVO getServerRuntimeDetail(String serverId);

    /**
     * 切换线路机生命周期 (上线前置: 域名必填)
     *
     * @param id       线路机编号
     * @param newState 目标生命周期
     */
    void transitionLifecycle(String id, String newState);
}

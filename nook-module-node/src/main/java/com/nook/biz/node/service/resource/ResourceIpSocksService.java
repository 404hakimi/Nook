package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;

import java.util.function.Consumer;

/**
 * SOCKS5 落地节点 Service 接口
 *
 * <p>负责 SOCKS5 节点部署 / 拨号测试.
 *
 * @author nook
 */
public interface ResourceIpSocksService {

    /**
     * 流式部署 SOCKS5 (ad-hoc 凭据), 渲染模板 → 上传 → bash 执行 → stdout 每行回写
     *
     * @param reqVO    部署入参 (含 ad-hoc SSH 凭据 + SOCKS5 服务参数)
     * @param lineSink 每行 stdout 的消费回调
     */
    void installSocks5(ResourceIpSocksInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 拨号测试 IP 池条目对应的 SOCKS5
     *
     * <p>走该凭据访问 echo-IP 端点验证可达性 + 出网 IP, 失败也返回 success=false.
     *
     * @param ipId  resource_ip_pool.id
     * @param reqVO 测试入参; reqVO 或其 echoUrl 为空时走后端默认 echo-IP 端点
     * @return Socks5TestRespVO; echoUrl / rawResponse 始终回填便于前端展示
     */
    ResourceIpSocksTestRespVO testSocks5(String ipId, ResourceIpSocksTestReqVO reqVO);
}

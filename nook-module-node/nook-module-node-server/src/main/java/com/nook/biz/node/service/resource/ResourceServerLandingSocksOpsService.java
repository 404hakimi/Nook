package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.landing.ServerLandingDeployReqVO;
import com.nook.biz.node.controller.resource.vo.landing.Socks5TestRespVO;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * SOCKS5 落地节点 SSH 运维 Service 接口
 *
 * @author nook
 */
public interface ResourceServerLandingSocksOpsService {

    /**
     * 流式装机 SOCKS5
     *
     * @param serverId 落地节点编号
     * @param reqVO    装机配置 (install 路径 + 开关)
     * @return 流式响应
     */
    ResponseBodyEmitter installSocks5Stream(String serverId, ServerLandingDeployReqVO reqVO);

    /**
     * 拨号测试 SOCKS5
     *
     * @param serverId         落地节点编号
     * @param echoUrl          目标 HTTP(S) URL
     * @param connectTimeoutMs TCP 建连超时
     * @param readTimeoutMs    HTTP 读响应超时
     * @return 拨号探测结果
     */
    Socks5TestRespVO testSocks5(String serverId, String echoUrl, int connectTimeoutMs, int readTimeoutMs);


    /**
     * 获得 SOCKS5 journal 日志
     *
     * @param serverId 落地节点编号
     * @param lines    行数
     * @param level    级别过滤
     * @param keyword  关键词过滤
     * @return 日志
     */
    ServiceLogRespVO getJournalLog(String serverId, Integer lines, String level, String keyword);

    /**
     * 获得 SOCKS5 自身日志文件
     *
     * @param serverId 落地节点编号
     * @param lines    行数
     * @param keyword  关键词过滤
     * @return 日志
     */
    ServiceLogRespVO getFileLog(String serverId, Integer lines, String keyword);
}

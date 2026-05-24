package com.nook.biz.node.service.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpSocksInstallReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksSyncCredsReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestReqVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpSocksTestRespVO;
import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.resource.vo.Socks5StatusRespVO;

import java.util.function.Consumer;

/**
 * SOCKS5 落地节点 Service 接口
 *
 * @author nook
 */
public interface ResourceIpSocksService {

    /**
     * 流式装机 SOCKS5
     *
     * @param reqVO    装机入参
     * @param lineSink 每行 stdout 的消费回调
     */
    void installSocks5(ResourceIpSocksInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 拨号测试 SOCKS5
     *
     * @param ipId  IP 池编号
     * @param reqVO 测试入参
     * @return 测试结果
     */
    ResourceIpSocksTestRespVO testSocks5(String ipId, ResourceIpSocksTestReqVO reqVO);

    /**
     * 流式同步 SOCKS5 凭据
     *
     * @param ipId     IP 池编号
     * @param reqVO    同步入参
     * @param lineSink 每行 stdout 的消费回调
     */
    void syncSocks5Creds(String ipId, ResourceIpSocksSyncCredsReqVO reqVO, Consumer<String> lineSink);

    /**
     * 获得 SOCKS5 运行状态
     *
     * @param ipId IP 池编号
     * @return SOCKS5 状态
     */
    Socks5StatusRespVO getSocks5Status(String ipId);

    /**
     * 切换 SOCKS5 开机自启
     *
     * @param ipId    IP 池编号
     * @param enabled 是否开机自启
     */
    void setSocks5Autostart(String ipId, boolean enabled);

    /**
     * 获得 dante journal 日志
     *
     * @param ipId    IP 池编号
     * @param lines   读取行数
     * @param level   级别过滤
     * @param keyword 关键字过滤
     * @return 日志内容
     */
    ServiceLogRespVO getSocks5Log(String ipId, Integer lines, String level, String keyword);

    /**
     * 获得 dante 日志文件内容
     *
     * @param ipId    IP 池编号
     * @param lines   读取行数
     * @param keyword 关键字过滤
     * @return 日志内容
     */
    ServiceLogRespVO getSocks5LogFile(String ipId, Integer lines, String keyword);
}

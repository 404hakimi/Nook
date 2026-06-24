package com.nook.biz.node.service.xray.server;

import com.nook.biz.node.controller.resource.vo.ServiceLogRespVO;
import com.nook.biz.node.controller.xray.vo.ProtocolSchemaRespVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallReqVO;
import com.nook.biz.node.controller.xray.vo.XrayInstallRespVO;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Xray 线路服务器管理 Service 接口
 *
 * @author nook
 */
public interface XrayInstallManageService {

    /**
     * 流式装机 / 重装 xray
     *
     * @param serverId 服务器编号
     * @param reqVO    装机入参
     * @param lineSink 每行 stdout 的消费回调
     */
    void installStreaming(String serverId, XrayInstallReqVO reqVO, Consumer<String> lineSink);

    /**
     * 重启 xray 服务
     *
     * @param serverId 服务器编号
     * @return 远端 stdout
     */
    String restart(String serverId);

    /**
     * 切换 xray 开机自启
     *
     * @param serverId 服务器编号
     * @param enabled  是否开机自启
     * @return 远端 stdout
     */
    String setAutostart(String serverId, boolean enabled);

    /**
     * 获得 xray 日志文件内容
     *
     * @param serverId 服务器编号
     * @param variant  日志变体 (access / error)
     * @param lines    读取行数
     * @param keyword  关键字过滤
     * @return 日志内容
     */
    ServiceLogRespVO getXrayLogFile(String serverId, String variant, Integer lines, String keyword);

    /**
     * 获得 xray 实例详情 (含主机地址回填)
     *
     * @param serverId 服务器编号
     * @return xray 实例详情 VO
     */
    XrayInstallRespVO getXrayInstallDetail(String serverId);

    /**
     * 流式装机 / 重装 xray
     *
     * @param serverId 服务器编号
     * @param reqVO    装机入参
     * @return 流式响应
     */
    ResponseBodyEmitter installXrayStream(String serverId, XrayInstallReqVO reqVO);

    /**
     * 列出全部入站协议的装机表单 schema (前端动态渲染协议下拉 + 字段)
     *
     * @return 协议 schema 列表
     */
    List<ProtocolSchemaRespVO> listProtocolSchemas();
}

package com.nook.biz.xray.demo;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.time.Instant;
import java.util.UUID;

/**
 * 3x-ui demo 主入口。
 *
 * 运行方式：
 *   1) 复制 3xui-demo.properties.example 为 3xui-demo.properties，填好真实参数
 *   2) IDE: 直接 Run main，args 设为 properties 路径
 *      mvn:  mvn -pl nook-biz-module/nook-biz-xray -am exec:java \
 *              -Dexec.mainClass=com.nook.biz.xray.demo.ThreexUiDemoApp \
 *              -Dexec.args="3xui-demo.properties"
 *      也可全部走 -D：
 *      mvn ... exec:java -Dssh.host=1.2.3.4 -Dssh.user=root -Dssh.password=xxx \
 *              -Dpanel.baseUrl=http://1.2.3.4:2053/abc -Dpanel.username=admin -Dpanel.password=admin
 *
 * Demo 流程：
 *   ① SSH 连上去打印 x-ui 状态、Xray 进程、面板信息
 *   ② 登录面板，列 inbound
 *   ③ 给第一个 inbound 加一个测试客户端 → 查它的流量 → 删除它
 *   ④ 打印当前在线 client、服务器状态
 *
 * 这只是 demo，主要展示常用调用方式；接到 nook 业务里时把这些方法挪到 service。
 */
public class ThreexUiDemoApp {

    public static void main(String[] args) throws Exception {
        DemoConfig cfg = DemoConfig.load(args);
        System.out.println("== 3x-ui demo 起步 ==");
        System.out.println("ssh   = " + cfg.sshUser + "@" + cfg.sshHost + ":" + cfg.sshPort);
        System.out.println("panel = " + cfg.panelBaseUrl + " (user=" + cfg.panelUsername + ")");

        runSshSection(cfg);
        runPanelSection(cfg);

        System.out.println("== Demo 结束 ==");
    }

    private static void runSshSection(DemoConfig cfg) {
        System.out.println("\n--- [1] SSH 连接 ---");
        try (SshOps ssh = new SshOps(cfg)) {
            System.out.println("hostname: " + ssh.exec("hostname").trim());
            System.out.println("uname:    " + ssh.exec("uname -srm").trim());
            System.out.println();
            System.out.println("[x-ui status]\n" + ssh.xuiStatus());
            System.out.println("[xray 进程]\n" + ssh.xrayProcess());
            System.out.println("[panelInfo (x-ui setting -show)]\n" + ssh.panelInfo());
        } catch (Exception e) {
            System.err.println("SSH 部分失败: " + e.getMessage());
            // SSH 挂了不影响 panel 测试，继续往下走
        }
    }

    private static void runPanelSection(DemoConfig cfg) throws Exception {
        System.out.println("\n--- [2] 面板登录 ---");
        ThreexUiPanelClient panel = new ThreexUiPanelClient(cfg);
        panel.login();
        System.out.println("登录成功");

        System.out.println("\n--- [3] 列出 inbound ---");
        JSONArray inbounds = panel.listInbounds();
        if (inbounds == null || inbounds.isEmpty()) {
            System.out.println("当前没有 inbound，跳过添加客户端步骤。请先在 3x-ui 面板里建一个 inbound。");
        } else {
            for (int i = 0; i < inbounds.size(); i++) {
                JSONObject ib = inbounds.getJSONObject(i);
                System.out.printf("  #%d id=%d remark=%s protocol=%s port=%d enable=%s%n",
                        i, ib.getIntValue("id"), ib.getString("remark"),
                        ib.getString("protocol"), ib.getIntValue("port"), ib.getString("enable"));
            }

            JSONObject first = inbounds.getJSONObject(0);
            int targetInboundId = first.getIntValue("id");
            long ts = Instant.now().getEpochSecond();
            String testEmail = "demo-" + ts;
            String testUuid = UUID.randomUUID().toString();

            System.out.println("\n--- [4] 在 inbound#" + targetInboundId + " 添加测试客户端 " + testEmail + " ---");
            JSONObject addResp = panel.addClient(targetInboundId,
                    new ThreexUiPanelClient.ClientSpec(testEmail, testUuid, 0, 0, 0, "", null));
            System.out.println("addClient: success=" + addResp.getBooleanValue("success")
                    + " msg=" + addResp.getString("msg"));

            System.out.println("\n--- [5] 查测试客户端的流量 ---");
            JSONObject traffic = panel.getClientTraffic(testEmail);
            System.out.println("traffic: " + traffic);

            System.out.println("\n--- [6] 以 master 为模板克隆出新客户端 ---");
            String cloneSource = "master";
            String cloneTarget = "master-clone-" + ts;
            JSONObject masterTpl = panel.findClient(targetInboundId, cloneSource);
            if (masterTpl == null) {
                System.out.println("inbound#" + targetInboundId + " 下没有 email=" + cloneSource
                        + " 的客户端，跳过克隆。");
            } else {
                System.out.println("模板 client: id=" + masterTpl.getString("id")
                        + " totalGB=" + masterTpl.get("totalGB")
                        + " expiryTime=" + masterTpl.get("expiryTime")
                        + " limitIp=" + masterTpl.get("limitIp")
                        + " flow=" + masterTpl.getString("flow"));
                JSONObject cloneResp = panel.cloneClient(targetInboundId, cloneSource, cloneTarget);
                System.out.println("cloneClient: success=" + cloneResp.getBooleanValue("success")
                        + " msg=" + cloneResp.getString("msg"));
                System.out.println("  新 client: email=" + cloneResp.getString("newEmail")
                        + " uuid=" + cloneResp.getString("newUuid"));
            }
        }

        System.out.println("\n--- [7] 当前在线 ---");
        try {
            System.out.println("onlines: " + panel.onlines());
        } catch (Exception e) {
            System.out.println("onlines 接口可能在你版本不支持，跳过: " + e.getMessage());
        }

        System.out.println("\n--- [8] 服务器状态 ---");
        try {
            JSONObject status = panel.serverStatus();
            // 只取一些关键字段，原始对象很大
            System.out.printf("cpu=%s%% mem=%s/%s disk=%s/%s%n",
                    status.get("cpu"),
                    status.getJSONObject("mem") != null ? status.getJSONObject("mem").get("current") : "?",
                    status.getJSONObject("mem") != null ? status.getJSONObject("mem").get("total") : "?",
                    status.getJSONObject("disk") != null ? status.getJSONObject("disk").get("current") : "?",
                    status.getJSONObject("disk") != null ? status.getJSONObject("disk").get("total") : "?");
        } catch (Exception e) {
            System.out.println("serverStatus 失败: " + e.getMessage());
        }
    }
}

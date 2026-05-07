package com.nook.biz.xray.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 3x-ui demo 的运行参数。
 * 来源优先级：
 *   1) System property（-Dssh.host=... 等）
 *   2) properties 文件（main 第一个参数指定的路径，例如 3xui-demo.properties）
 *   3) 默认值/留空
 *
 * 必填字段：sshHost / sshUser / panelBaseUrl / panelUsername / panelPassword
 * 鉴权二选一：sshPassword 或 sshPrivateKeyPath
 */
public final class DemoConfig {

    public final String sshHost;
    public final int sshPort;
    public final String sshUser;
    /** 密码登录；与 sshPrivateKeyPath 二选一 */
    public final String sshPassword;
    /** 私钥文件路径；优先级高于密码 */
    public final String sshPrivateKeyPath;
    public final String sshPrivateKeyPassphrase;

    /** 形如 http://1.2.3.4:2053/abc 的面板入口；不带末尾斜杠；webBasePath 无配置则填到 :2053 */
    public final String panelBaseUrl;
    public final String panelUsername;
    public final String panelPassword;

    /** 自签 HTTPS 证书时跳过校验，方便 demo 跑通，**生产慎用** */
    public final boolean panelIgnoreTls;

    /** 整体网络超时（秒） */
    public final int timeoutSeconds;

    private DemoConfig(Properties p) {
        this.sshHost = required(p, "ssh.host");
        this.sshPort = parseInt(p.getProperty("ssh.port"), 22);
        this.sshUser = required(p, "ssh.user");
        this.sshPassword = p.getProperty("ssh.password");
        this.sshPrivateKeyPath = p.getProperty("ssh.privateKeyPath");
        this.sshPrivateKeyPassphrase = p.getProperty("ssh.privateKeyPassphrase");
        if ((isBlank(sshPassword)) && isBlank(sshPrivateKeyPath)) {
            throw new IllegalArgumentException("ssh.password 与 ssh.privateKeyPath 必须二选一");
        }
        this.panelBaseUrl = trimTrailingSlash(required(p, "panel.baseUrl"));
        this.panelUsername = required(p, "panel.username");
        this.panelPassword = required(p, "panel.password");
        this.panelIgnoreTls = Boolean.parseBoolean(p.getProperty("panel.ignoreTls", "false"));
        this.timeoutSeconds = parseInt(p.getProperty("timeoutSeconds"), 15);
    }

    /**
     * 加载顺序：
     *   1) 显式给 args[0] 时，按该路径读
     *   2) 否则按下面的候选名依次找：先工作目录文件系统、再 classpath
     *      - 3xui-demo.properties           (推荐：复制 example 后重命名)
     *      - 3xui-demo.properties.example   (兜底：不想改名也能跑)
     *   3) 最后用 -Dxxx 系统属性覆盖
     * 找到任何文件都会打印 [DemoConfig] 提示来源，找不到也会列出所有查过的路径。
     */
    public static DemoConfig load(String[] args) throws IOException {
        Properties p = new Properties();
        List<String> tried = new ArrayList<>();
        String loadedFrom = null;

        if (args != null && args.length >= 1 && !args[0].isBlank()) {
            Path path = Path.of(args[0]);
            tried.add(path.toAbsolutePath().toString());
            if (Files.exists(path)) {
                loadProperties(p, Files.newInputStream(path));
                loadedFrom = path.toAbsolutePath().toString();
            }
        } else {
            for (String name : new String[]{"3xui-demo.properties", "3xui-demo.properties.example"}) {
                Path local = Path.of(name);
                tried.add("(cwd) " + local.toAbsolutePath());
                if (Files.exists(local)) {
                    loadProperties(p, Files.newInputStream(local));
                    loadedFrom = local.toAbsolutePath().toString();
                    break;
                }
                tried.add("(classpath) " + name);
                InputStream cp = DemoConfig.class.getClassLoader().getResourceAsStream(name);
                if (cp != null) {
                    loadProperties(p, cp);
                    loadedFrom = "classpath:/" + name;
                    break;
                }
            }
        }

        // System properties 优先级最高，可单点覆盖文件值
        for (String key : new String[]{
                "ssh.host", "ssh.port", "ssh.user", "ssh.password",
                "ssh.privateKeyPath", "ssh.privateKeyPassphrase",
                "panel.baseUrl", "panel.username", "panel.password",
                "panel.ignoreTls", "timeoutSeconds"
        }) {
            String sys = System.getProperty(key);
            if (sys != null) p.setProperty(key, sys);
        }

        if (loadedFrom != null) {
            System.out.println("[DemoConfig] 已加载配置: " + loadedFrom);
        } else {
            System.out.println("[DemoConfig] 未发现 properties 文件，将仅使用 -D 系统属性。已尝试的位置:");
            tried.forEach(t -> System.out.println("  - " + t));
        }
        return new DemoConfig(p);
    }

    /** 用 UTF-8 显式读，避开 Properties.load(InputStream) 默认 ISO-8859-1 在中文注释/value 上的乱码风险。 */
    private static void loadProperties(Properties p, InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(stripBom(in), StandardCharsets.UTF_8)) {
            p.load(reader);
        }
    }

    /** 去掉 UTF-8 BOM(EF BB BF)，否则首行 key 会被当成 "﻿xxx"。 */
    private static InputStream stripBom(InputStream in) throws IOException {
        java.io.PushbackInputStream pis = new java.io.PushbackInputStream(in, 3);
        byte[] head = new byte[3];
        int read = pis.read(head, 0, 3);
        if (!(read == 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF)) {
            if (read > 0) pis.unread(head, 0, read);
        }
        return pis;
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (isBlank(v)) {
            throw new IllegalArgumentException("缺少必填配置: " + key
                    + "（请把 3xui-demo.properties 放工作目录或 resources/，或显式给 main 第一个参数指定路径，或用 -D"
                    + key + "=... 覆盖）");
        }
        return v.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int parseInt(String s, int fallback) {
        if (isBlank(s)) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

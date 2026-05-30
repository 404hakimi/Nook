package com.nook.biz.agent.controller;

import com.nook.biz.agent.api.enums.AgentRole;
import com.nook.biz.agent.framework.auth.AuthenticatedAgent;
import com.nook.biz.agent.framework.config.AgentProperties;
import com.nook.biz.agent.framework.binary.AgentBinaryResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Agent binary / 源码下载 Controller
 *
 * @author nook
 */
@Slf4j
@RestController
@RequestMapping("/admin/agent-dist")
public class AgentSourceDownloadController {

    @Resource
    private AgentBinaryResolver agentBinaryResolver;
    @Resource
    private AgentProperties agentProperties;

    /**
     * 装机 / 升级时 agent 回拉 binary; AgentBinaryResolver 按 role/os/arch 解析 nook.agent.bin-dir 下最新文件.
     *
     * @param serverId 已认证 server id (鉴权用, 仅日志)
     * @param role     frontline / landing (默认 frontline)
     * @param os       操作系统 (默认 linux; 当前只发 linux)
     * @param arch     CPU 架构 (默认 amd64)
     * @param response 流式写入 binary; 含 X-Bin-Version / X-Bin-Sha256 头给 agent 校验
     */
    @GetMapping("/bin")
    public void downloadBin(@AuthenticatedAgent String serverId,
                            @RequestParam(required = false, defaultValue = AgentRole.Codes.FRONTLINE) String role,
                            @RequestParam(required = false, defaultValue = "linux") String os,
                            @RequestParam(required = false, defaultValue = "amd64") String arch,
                            HttpServletResponse response) throws IOException {
        AgentBinaryResolver.AgentBinary bin = agentBinaryResolver.resolve(role, os, arch);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + bin.path().getFileName() + "\"");
        response.setHeader("X-Bin-Version", bin.role() + "-" + bin.version());
        response.setHeader("X-Bin-Sha256", bin.sha256());
        response.setContentLengthLong(bin.sizeBytes());
        // 分块写 + 每块 flush; 防 natapp / 长链路下 Tomcat output buffer 攒满阻塞写,
        // 上游 idle 检测把 socket RST 掉 (Files.copy 整段写完才 flush, 跨境隧道场景容易触发).
        try (InputStream in = Files.newInputStream(bin.path());
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                out.flush();
            }
        }
        log.info("[downloadBin] serverId={} role={} file={} bytes={}",
                serverId, role, bin.path().getFileName(), bin.sizeBytes());
    }

    /**
     * 开发期下源码 tar.gz; 走 admin sa-token 拦截.
     *
     * @param response 流式写入 tar.gz (Content-Type: application/gzip)
     */
    @GetMapping("/agent-src.tar.gz")
    public void downloadAgentSrc(HttpServletResponse response) throws IOException {
        Path srcDir = resolveSrcDir();
        if (!srcDir.toFile().isDirectory()) {
            log.error("[downloadAgentSrc] 源码目录不存在: {}", srcDir.toAbsolutePath());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "agent 源码目录未找到");
            return;
        }
        response.setContentType("application/gzip");
        response.setHeader("Content-Disposition", "attachment; filename=\"agent-src.tar.gz\"");

        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-czf", "-",
                "-C", srcDir.getParent().toAbsolutePath().toString(),
                "--exclude=.git",
                srcDir.getFileName().toString()
        );
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        try (InputStream tarOut = proc.getInputStream(); OutputStream resp = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = tarOut.read(buf)) > 0) resp.write(buf, 0, n);
            resp.flush();
        }
        try {
            int exit = proc.waitFor();
            if (exit != 0) log.warn("[downloadAgentSrc] tar exit={}", exit);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析 agentSrcDir 为绝对路径; 顺序: 绝对路径直用 → CWD 下找 → CWD 父目录下找 → 兜底 CWD 下.
     *
     * @return 解析后的源码目录 Path (不保证一定存在, 调用方自检)
     */
    private Path resolveSrcDir() {
        String srcDir = agentProperties.getSrcDir();
        File f = new File(srcDir);
        if (f.isAbsolute()) return f.toPath();
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path direct = cwd.resolve(srcDir);
        if (direct.toFile().isDirectory()) return direct;
        Path parent = cwd.getParent() == null ? direct : cwd.getParent().resolve(srcDir);
        return parent.toFile().isDirectory() ? parent : direct;
    }
}

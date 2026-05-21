package com.nook.biz.node.controller.agent;

import com.nook.biz.node.service.agent.AgentAuthService;
import com.nook.biz.node.service.agent.AgentBinaryResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/** Agent binary / 源码下载: /bin 走 X-Agent-Token, /agent-src.tar.gz 走 admin sa-token. */
@Slf4j
@RestController
@RequestMapping("/admin/agent-dist")
@RequiredArgsConstructor
public class AgentSourceDownloadController {

    private static final String TOKEN_HEADER = "X-Agent-Token";

    /** Agent 源码根目录 (相对 backend CWD); 默认 nook-agent. */
    @Value("${nook.agent.src-dir:nook-agent}")
    private String agentSrcDir;

    private final AgentBinaryResolver agentBinaryResolver;
    private final AgentAuthService agentAuthService;

    /**
     * 拉 binary; agent 装机 / 升级 task 用. X-Agent-Token Header 鉴权.
     *
     * <pre>
     *   curl -fSL -H "X-Agent-Token: $TOKEN" "$URL/admin/agent-dist/bin"
     * </pre>
     *
     * <p>os / arch 默认 linux/amd64; 当前只跑 linux. 文件由 AgentBinaryResolver 从 nook.agent.bin-dir
     * 解析 (按 mtime 取最新 nook-agent-*-os-arch).
     */
    @GetMapping("/bin")
    public void downloadBin(@RequestParam(required = false, defaultValue = "frontline") String role,
                            @RequestParam(required = false, defaultValue = "linux") String os,
                            @RequestParam(required = false, defaultValue = "amd64") String arch,
                            HttpServletRequest req,
                            HttpServletResponse response) throws IOException {
        agentAuthService.verifyAndGetServer(req.getHeader(TOKEN_HEADER));
        AgentBinaryResolver.AgentBinary bin = agentBinaryResolver.resolve(role, os, arch);

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + bin.path().getFileName() + "\"");
        response.setHeader("X-Bin-Version", bin.role() + "-" + bin.version());
        response.setHeader("X-Bin-Sha256", bin.sha256());
        response.setContentLengthLong(bin.sizeBytes());
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(bin.path(), out);
            out.flush();
        }
        log.info("[downloadBin] downloadBin role={} {} ({} bytes)", role, bin.path().getFileName(), bin.sizeBytes());
    }

    /** 开发期下源码 tar.gz; 走 admin sa-token 拦截. */
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

    private Path resolveSrcDir() {
        File f = new File(agentSrcDir);
        if (f.isAbsolute()) return f.toPath();
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path direct = cwd.resolve(agentSrcDir);
        if (direct.toFile().isDirectory()) return direct;
        Path parent = cwd.getParent() == null ? direct : cwd.getParent().resolve(agentSrcDir);
        return parent.toFile().isDirectory() ? parent : direct;
    }
}

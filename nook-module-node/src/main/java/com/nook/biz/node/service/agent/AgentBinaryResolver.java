package com.nook.biz.node.service.agent;

import com.nook.common.web.error.CommonErrorCode;
import com.nook.common.web.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 nook.agent.bin-dir 目录按 nook-{role}-{ver}-{os}-{arch} 文件名解析当前 agent binary, mtime 最新优先. */
@Slf4j
@Component
public class AgentBinaryResolver {

    /** agent binary 所在目录; 相对路径以 backend 启动 CWD 为基准. */
    @Value("${nook.agent.bin-dir:agent}")
    private String binDir;

    /** 文件名 pattern: nook-{role}-{version}-{os}-{arch}; role = frontline / landing. */
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "^nook-(?<role>frontline|landing)-(?<version>.+)-(?<os>linux|darwin|windows)-(?<arch>amd64|arm64)$");

    public record AgentBinary(Path path, String role, String version, String os, String arch, long sizeBytes, String sha256) {}

    /**
     * 找指定 role + os/arch 的当前 binary; 多个匹配按 mtime 取最新.
     *
     * @param role frontline / landing (跟 binary 文件名 + agent 自报的 version prefix 一致)
     */
    public AgentBinary resolve(String role, String os, String arch) {
        if (role == null || role.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "role 不能为空");
        }
        File dir = resolveDir();
        if (!dir.isDirectory()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "agent binary 目录不存在: " + dir.getAbsolutePath() + " (配 nook.agent.bin-dir 指向部署位置)");
        }
        String prefix = "nook-" + role + "-";
        String suffix = "-" + os + "-" + arch;
        File[] candidates = dir.listFiles((f, name) -> name.startsWith(prefix) && name.endsWith(suffix));
        if (candidates == null || candidates.length == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "agent binary 未部署: 目录 " + dir.getAbsolutePath() + " 下找不到 " + prefix + "*" + suffix);
        }
        if (candidates.length > 1) {
            log.warn("[resolve] role={} 有 {} 个候选, 按 mtime 取最新 (其他: {})",
                    role, candidates.length,
                    Arrays.stream(candidates).map(File::getName).toList());
        }
        Arrays.sort(candidates, Comparator.comparingLong(File::lastModified).reversed());
        File chosen = candidates[0];
        Matcher m = FILE_PATTERN.matcher(chosen.getName());
        if (!m.matches()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "文件名不符合 pattern (nook-{role}-{ver}-{os}-{arch}): " + chosen.getName());
        }
        return new AgentBinary(chosen.toPath(), m.group("role"), m.group("version"),
                m.group("os"), m.group("arch"), chosen.length(), sha256(chosen.toPath()));
    }

    /** 从 agentVersion 字符串 (e.g., "frontline-0.7.0") 提取 role; 未知 / null 返默认 frontline. */
    public static String extractRole(String agentVersion) {
        if (agentVersion == null || agentVersion.isBlank()) return "frontline";
        int i = agentVersion.indexOf('-');
        if (i <= 0) return "frontline";
        String r = agentVersion.substring(0, i);
        return ("frontline".equals(r) || "landing".equals(r)) ? r : "frontline";
    }

    /** 解析 binDir; 绝对路径直接用, 相对路径找 CWD 或上一级 (兼容 IntelliJ 在子 module 跑). */
    private File resolveDir() {
        File f = new File(binDir);
        if (f.isAbsolute()) return f;
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path direct = cwd.resolve(binDir);
        if (direct.toFile().isDirectory()) return direct.toFile();
        Path parent = cwd.getParent() == null ? direct : cwd.getParent().resolve(binDir);
        return parent.toFile().isDirectory() ? parent.toFile() : direct.toFile();
    }

    private static String sha256(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(p)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "算 sha256 失败: " + e.getMessage());
        }
    }
}

package com.nook.biz.system.api.domain;

/**
 * 域名工具; 与具体业务无关的域名字符串处理 (与 system_domain 根域同源, 故置于 domain 包)
 *
 * @author nook
 */
public final class DomainUtils {

    private DomainUtils() {
    }

    /**
     * 拼完整 FQDN: 二级标签 + 根域 (如 frontline-jp-1 + karsu.cc → frontline-jp-1.karsu.cc); 标签空则直接用根域
     *
     * @param subdomain  二级域名标签 (可空)
     * @param rootDomain 根域
     * @return 完整 FQDN
     */
    public static String buildFqdn(String subdomain, String rootDomain) {
        String sub = (subdomain == null) ? "" : subdomain.trim();
        String root = (rootDomain == null) ? "" : rootDomain.trim();
        return sub.isEmpty() ? root : sub + "." + root;
    }
}

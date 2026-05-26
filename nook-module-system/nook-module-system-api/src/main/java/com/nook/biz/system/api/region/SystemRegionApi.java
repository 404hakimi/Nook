package com.nook.biz.system.api.region;

import java.util.Collection;
import java.util.Map;

/**
 * 区域字典 Api 接口
 *
 * @author nook
 */
public interface SystemRegionApi {

    /**
     * 校验区域 code 是否存在
     *
     * @param code 区域 code
     * @return 存在则 true
     */
    boolean exists(String code);

    /**
     * 按 code 批量拉区域展示名
     *
     * @param codes 区域 code 集合
     * @return key=code, value=展示名; 缺失 code 不在 map 里
     */
    Map<String, String> getRegionDisplayNameMap(Collection<String> codes);
}

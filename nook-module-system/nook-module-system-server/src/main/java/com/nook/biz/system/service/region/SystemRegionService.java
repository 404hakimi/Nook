package com.nook.biz.system.service.region;

import com.nook.biz.system.dal.dataobject.region.SystemRegionDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 区域字典 Service 接口
 *
 * @author nook
 */
public interface SystemRegionService {

    /**
     * 获得已启用区域列表
     *
     * @return 已启用区域列表
     */
    List<SystemRegionDO> listEnabled();

    /**
     * 获得区域列表
     *
     * @param keyword 关键字
     * @param enabled 启用状态
     * @return 区域列表
     */
    List<SystemRegionDO> list(String keyword, Integer enabled);

    /**
     * 按 code 获得区域
     *
     * @param code 区域 code
     * @return 区域
     */
    SystemRegionDO getByCode(String code);

    /**
     * 校验 code 是否存在
     *
     * @param code 区域 code
     * @return 存在则 true
     */
    boolean exists(String code);

    /**
     * 按 code 批量拉区域展示名
     *
     * @param codes 区域 code 集合
     * @return key=code, value=显示名
     */
    Map<String, String> loadDisplayNameMap(Collection<String> codes);
}

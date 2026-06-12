package com.nook.biz.system.service;

import com.nook.biz.system.controller.region.vo.SystemRegionCreateReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionRecodeReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionUpdateReqVO;
import com.nook.biz.system.entity.SystemRegionDO;

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
     * 创建区域
     *
     * @param reqVO 创建信息
     * @return 区域码
     */
    String create(SystemRegionCreateReqVO reqVO);

    /**
     * 更新区域展示信息 (区域码不可改)
     *
     * @param code  区域码
     * @param reqVO 更新信息
     */
    void update(String code, SystemRegionUpdateReqVO reqVO);

    /**
     * 更正区域码并级联迁移引用 (新旧码相同时仅更新展示字段)
     *
     * @param reqVO 更正信息
     */
    void recode(SystemRegionRecodeReqVO reqVO);

    /**
     * 启用 / 停用区域
     *
     * @param code    区域码
     * @param enabled 是否启用
     */
    void toggleEnabled(String code, boolean enabled);

    /**
     * 获得已启用区域列表
     *
     * @return 已启用区域列表
     */
    List<SystemRegionDO> listEnabled();

    /**
     * 获得区域列表
     *
     * @param keyword 关键字 (模糊匹配)
     * @param enabled 启用状态过滤; null=不过滤
     * @return 区域列表
     */
    List<SystemRegionDO> list(String keyword, Integer enabled);

    /**
     * 区域码是否存在
     *
     * @param code 区域码
     * @return 是否存在
     */
    boolean exists(String code);

    /**
     * 按区域码批量查询展示名 (key=区域码, value=展示名; 缺失不进 map)
     *
     * @param codes 区域码集合
     * @return Map<String, String>
     */
    Map<String, String> getDisplayNameMap(Collection<String> codes);
}

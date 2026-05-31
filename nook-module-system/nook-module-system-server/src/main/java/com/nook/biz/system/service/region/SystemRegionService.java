package com.nook.biz.system.service.region;

import com.nook.biz.system.controller.region.vo.SystemRegionRecodeReqVO;
import com.nook.biz.system.controller.region.vo.SystemRegionSaveReqVO;
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
     * 新增区域
     *
     * @param req 区域信息
     * @return 区域码
     */
    String create(SystemRegionSaveReqVO req);

    /**
     * 编辑区域 (区域码不可改)
     *
     * @param req 区域信息
     */
    void update(SystemRegionSaveReqVO req);

    /**
     * 更正区域码 (改主键 + 级联迁移引用该码的机器 / 套餐); oldCode==新码时仅更新其余字段
     *
     * @param req 含 oldCode (原码) + 新码及展示字段
     */
    void recode(SystemRegionRecodeReqVO req);

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

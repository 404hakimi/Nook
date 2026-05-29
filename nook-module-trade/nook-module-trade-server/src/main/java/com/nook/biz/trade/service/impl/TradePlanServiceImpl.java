package com.nook.biz.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.trade.controller.vo.TradePlanPageReqVO;
import com.nook.biz.trade.controller.vo.TradePlanSaveReqVO;
import com.nook.biz.trade.convert.TradePlanConvert;
import com.nook.biz.trade.dal.dataobject.TradePlanDO;
import com.nook.biz.trade.dal.mysql.mapper.TradePlanMapper;
import com.nook.biz.trade.service.TradePlanService;
import com.nook.biz.trade.validator.TradePlanValidator;
import com.nook.common.web.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 套餐管理 Service 实现. 返回 DO; VO 转换由 controller + convert 层处理.
 *
 * <p>套餐 = 区域 + 规格 (不绑具体资源); 下单时 allocator 按区域 + IP 类型自动匹配落地机 + 线路机。
 * 容量由 controller 调 node landingApi.countCapacityForPlan 算 (落地机池 = node 域).
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class TradePlanServiceImpl implements TradePlanService {

    private final TradePlanMapper planMapper;
    private final TradePlanValidator planValidator;

    @Override
    public PageResult<TradePlanDO> getPlanPage(TradePlanPageReqVO req) {
        IPage<TradePlanDO> page = planMapper.selectPageByQuery(
                Page.of(req.getPageNo(), req.getPageSize()),
                req.getRegionCode(), req.getIpTypeId(), req.getEnabled(), req.getKeyword());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public TradePlanDO getPlan(String id) {
        return planValidator.validateExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPlan(TradePlanSaveReqVO req) {
        planValidator.validateCodeUnique(req.getCode(), null);
        TradePlanDO e = TradePlanConvert.INSTANCE.toDO(req);
        e.setId(null);
        e.setEnabled(0);
        planMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(TradePlanSaveReqVO req) {
        TradePlanDO existing = planValidator.validateExists(req.getId());
        // 仅可变字段; 已售卖核心字段 (区域/IP类型/流量/带宽/周期/价格) 忽略 (改了破历史订阅)
        existing.setName(req.getName());
        existing.setRemark(req.getRemark());
        planMapper.updateById(existing);
    }

    @Override
    public void toggleEnabled(String id, boolean enabled) {
        planValidator.validateExists(id);
        TradePlanDO patch = new TradePlanDO();
        patch.setId(id);
        patch.setEnabled(enabled ? 1 : 0);
        planMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(String id) {
        planValidator.validateExists(id);
        planValidator.validateNoActiveSub(id);
        planMapper.deleteById(id);
    }
}

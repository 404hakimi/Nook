package com.nook.biz.trade.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nook.biz.member.api.MemberUserApi;
import com.nook.biz.node.api.resource.ResourceServerLandingApi;
import com.nook.biz.node.api.resource.dto.LandingSummaryDTO;
import com.nook.biz.trade.controller.admin.vo.TradeSubscriptionChangeLogPageReqVO;
import com.nook.biz.trade.controller.admin.vo.TradeSubscriptionChangeLogRespVO;
import com.nook.biz.trade.convert.TradeSubscriptionChangeLogConvert;
import com.nook.biz.trade.dal.dataobject.TradeSubscriptionChangeLogDO;
import com.nook.biz.trade.dal.mysql.mapper.TradeSubscriptionChangeLogMapper;
import com.nook.biz.trade.event.SubscriptionMachineChangeEvent;
import com.nook.biz.trade.service.TradeSubscriptionChangeLogService;
import com.nook.common.utils.collection.CollectionUtils;
import com.nook.common.web.response.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅换机历史日志 Service 实现类
 *
 * @author nook
 */
@Service
public class TradeSubscriptionChangeLogServiceImpl implements TradeSubscriptionChangeLogService {

    @Resource
    private TradeSubscriptionChangeLogMapper changeLogMapper;
    @Resource
    private ResourceServerLandingApi landingApi;
    @Resource
    private MemberUserApi memberUserApi;

    @Override
    public void record(SubscriptionMachineChangeEvent event) {
        TradeSubscriptionChangeLogDO log = new TradeSubscriptionChangeLogDO();
        log.setSubscriptionId(event.getSubscriptionId());
        log.setMemberUserId(event.getMemberUserId());
        log.setChangeType(event.getChangeType().getState());
        log.setOldServerId(event.getOldServerId());
        log.setNewServerId(event.getNewServerId());
        log.setReason(event.getReason().getState());
        log.setOperator(event.getOperator());
        changeLogMapper.insert(log);
    }

    @Override
    public PageResult<TradeSubscriptionChangeLogRespVO> getPage(TradeSubscriptionChangeLogPageReqVO req) {
        IPage<TradeSubscriptionChangeLogDO> page = changeLogMapper.selectPage(
                Page.of(req.getPageNo(), req.getPageSize()),
                new LambdaQueryWrapper<TradeSubscriptionChangeLogDO>()
                        .eq(StrUtil.isNotBlank(req.getSubscriptionId()), TradeSubscriptionChangeLogDO::getSubscriptionId, req.getSubscriptionId())
                        .eq(StrUtil.isNotBlank(req.getMemberUserId()), TradeSubscriptionChangeLogDO::getMemberUserId, req.getMemberUserId())
                        .eq(StrUtil.isNotBlank(req.getChangeType()), TradeSubscriptionChangeLogDO::getChangeType, req.getChangeType())
                        .eq(StrUtil.isNotBlank(req.getReason()), TradeSubscriptionChangeLogDO::getReason, req.getReason())
                        .orderByDesc(TradeSubscriptionChangeLogDO::getCreatedAt));
        return PageResult.of(page.getTotal(), enrich(page.getRecords()));
    }

    @Override
    public List<TradeSubscriptionChangeLogRespVO> getBySubscription(String subscriptionId) {
        List<TradeSubscriptionChangeLogDO> logs = changeLogMapper.selectList(
                new LambdaQueryWrapper<TradeSubscriptionChangeLogDO>()
                        .eq(TradeSubscriptionChangeLogDO::getSubscriptionId, subscriptionId)
                        .orderByDesc(TradeSubscriptionChangeLogDO::getCreatedAt));
        return enrich(logs);
    }

    /** 换机日志补会员邮箱 + 机器出网 IP, 转 VO. */
    private List<TradeSubscriptionChangeLogRespVO> enrich(List<TradeSubscriptionChangeLogDO> logs) {
        if (CollUtil.isEmpty(logs)) {
            return Collections.emptyList();
        }
        // 机器出网 IP: 收集所有 old/new server id 一次查回
        Set<String> serverIds = new HashSet<>();
        for (TradeSubscriptionChangeLogDO log : logs) {
            CollectionUtils.addIfNotNull(serverIds, log.getOldServerId());
            CollectionUtils.addIfNotNull(serverIds, log.getNewServerId());
        }
        Map<String, String> serverIpMap = CollectionUtils.convertMap(
                landingApi.listSummaryByServerIds(serverIds),
                LandingSummaryDTO::getServerId, LandingSummaryDTO::getIpAddress);
        // 会员邮箱
        Map<String, String> emailMap = memberUserApi.getEmailMap(
                CollectionUtils.convertSet(logs, TradeSubscriptionChangeLogDO::getMemberUserId));
        return TradeSubscriptionChangeLogConvert.INSTANCE.convertList(logs, serverIpMap, emailMap);
    }
}

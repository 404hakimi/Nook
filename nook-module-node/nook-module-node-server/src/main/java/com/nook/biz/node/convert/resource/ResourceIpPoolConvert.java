package com.nook.biz.node.convert.resource;

import com.nook.biz.node.controller.resource.vo.ResourceIpPoolRespVO;
import com.nook.biz.node.controller.resource.vo.ResourceIpTypeRespVO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolBillingDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolCredentialDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolRuntimeDO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpPoolSocks5DO;
import com.nook.biz.node.dal.dataobject.resource.ResourceIpTypeDO;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

/**
 * IP 池 / IP 类型 Convert
 *
 * @author nook
 */
@Mapper
public interface ResourceIpPoolConvert {

    ResourceIpPoolConvert INSTANCE = Mappers.getMapper(ResourceIpPoolConvert.class);

    /** 主表 → RespVO (仅主表字段; 子表字段需走 enrich). */
    ResourceIpPoolRespVO convert(ResourceIpPoolDO entity);

    List<ResourceIpPoolRespVO> convertList(List<ResourceIpPoolDO> entities);

    /**
     * 单 IP 详情: 主 + 4 子表 → RespVO. 子表行允许为 null (新建未填), 缺失字段保持 null.
     */
    default ResourceIpPoolRespVO convertWithSubtables(ResourceIpPoolDO main,
                                                     ResourceIpPoolCredentialDO cred,
                                                     ResourceIpPoolBillingDO bill,
                                                     ResourceIpPoolSocks5DO socks5,
                                                     ResourceIpPoolRuntimeDO runtime) {
        ResourceIpPoolRespVO vo = convert(main);
        enrichCredential(vo, cred);
        enrichBilling(vo, bill);
        enrichSocks5(vo, socks5);
        enrichRuntime(vo, runtime);
        return vo;
    }

    /**
     * 列表分页: 主表 list + 4 子表 Map (ipId → 子 DO) → RespVO list.
     */
    default PageResult<ResourceIpPoolRespVO> convertPageWithSubtables(
            PageResult<ResourceIpPoolDO> page,
            Map<String, ResourceIpPoolCredentialDO> credMap,
            Map<String, ResourceIpPoolBillingDO> billMap,
            Map<String, ResourceIpPoolSocks5DO> socks5Map,
            Map<String, ResourceIpPoolRuntimeDO> runtimeMap) {
        List<ResourceIpPoolRespVO> records = convertList(page.getRecords());
        for (ResourceIpPoolRespVO vo : records) {
            enrichCredential(vo, credMap == null ? null : credMap.get(vo.getId()));
            enrichBilling(vo, billMap == null ? null : billMap.get(vo.getId()));
            enrichSocks5(vo, socks5Map == null ? null : socks5Map.get(vo.getId()));
            enrichRuntime(vo, runtimeMap == null ? null : runtimeMap.get(vo.getId()));
        }
        return PageResult.of(page.getTotal(), records);
    }

    static void enrichCredential(ResourceIpPoolRespVO vo, ResourceIpPoolCredentialDO cred) {
        if (vo == null || cred == null) return;
        vo.setSshHost(cred.getSshHost());
        vo.setSshPort(cred.getSshPort());
        vo.setSshUser(cred.getSshUser());
        vo.setSshPassword(cred.getSshPassword());
    }

    static void enrichBilling(ResourceIpPoolRespVO vo, ResourceIpPoolBillingDO bill) {
        if (vo == null || bill == null) return;
        vo.setBandwidthMbps(bill.getBandwidthMbps());
        vo.setTrafficQuotaGb(bill.getTrafficQuotaGb());
        vo.setCostMonthlyUsd(bill.getCostMonthlyUsd());
        vo.setBillingCycleDay(bill.getBillingCycleDay());
        vo.setExpiresAt(bill.getExpiresAt());
    }

    static void enrichSocks5(ResourceIpPoolRespVO vo, ResourceIpPoolSocks5DO socks5) {
        if (vo == null || socks5 == null) return;
        vo.setSocks5Port(socks5.getSocks5Port());
        vo.setSocks5Username(socks5.getSocks5Username());
        vo.setSocks5Password(socks5.getSocks5Password());
        vo.setLogLevel(socks5.getLogLevel());
        vo.setLogPath(socks5.getLogPath());
        vo.setAutostartEnabled(socks5.getAutostartEnabled());
        vo.setFirewallEnabled(socks5.getFirewallEnabled());
        vo.setFirewallAllowFrom(socks5.getFirewallAllowFrom());
        vo.setInstallDir(socks5.getInstallDir());
    }

    static void enrichRuntime(ResourceIpPoolRespVO vo, ResourceIpPoolRuntimeDO runtime) {
        if (vo == null || runtime == null) return;
        vo.setLastHealthAt(runtime.getLastHealthAt());
    }

    /** IP 类型 → RespVO. */
    ResourceIpTypeRespVO convertType(ResourceIpTypeDO entity);

    List<ResourceIpTypeRespVO> convertTypeList(List<ResourceIpTypeDO> entities);
}

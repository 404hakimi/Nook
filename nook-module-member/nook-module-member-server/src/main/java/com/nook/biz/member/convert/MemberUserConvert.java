package com.nook.biz.member.convert;

import com.nook.biz.member.controller.admin.vo.AdminMemberRespVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 会员 Convert
 *
 * @author nook
 */
@Mapper
public interface MemberUserConvert {

    MemberUserConvert INSTANCE = Mappers.getMapper(MemberUserConvert.class);

    /** entity → 会员自查 VO; passwordHash / deleted 等敏感/内部字段自然被忽略. */
    PortalMemberRespVO convertPortal(MemberUser entity);

    /** entity → 管理后台 VO; 含 lastLoginIp / remark 等管理字段. */
    AdminMemberRespVO convertAdmin(MemberUser entity);

    /** entity 列表 → 管理后台 VO 列表. */
    List<AdminMemberRespVO> convertAdminList(List<MemberUser> entities);

    /** 分页结果转换; total 透传, records 经 convertAdminList 转换. */
    default PageResult<AdminMemberRespVO> convertAdminPage(PageResult<MemberUser> page) {
        return PageResult.of(page.getTotal(), convertAdminList(page.getRecords()));
    }
}

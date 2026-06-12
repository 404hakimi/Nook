package com.nook.biz.member.convert.admin;

import com.nook.biz.member.controller.admin.vo.MemberRespVO;
import com.nook.biz.member.entity.MemberUser;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface MemberUserConvert {

    MemberUserConvert INSTANCE = Mappers.getMapper(MemberUserConvert.class);

    MemberRespVO convert(MemberUser entity);

    List<MemberRespVO> convertList(List<MemberUser> entities);

    default PageResult<MemberRespVO> convertPage(PageResult<MemberUser> page) {
        List<MemberRespVO> list = this.convertList(page.getRecords());
        return PageResult.of(page.getTotal(), list);
    }
}

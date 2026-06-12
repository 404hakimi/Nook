package com.nook.biz.member.convert.portal;

import com.nook.biz.member.controller.portal.vo.PortalAuthLoginRespVO;
import com.nook.biz.member.controller.portal.vo.PortalMemberRespVO;
import com.nook.biz.member.entity.MemberUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PortalMemberUserConvert {

    PortalMemberUserConvert INSTANCE = Mappers.getMapper(PortalMemberUserConvert.class);

    PortalMemberRespVO convert(MemberUser entity);

    @Mapping(target = "token", source = "token")
    @Mapping(target = "expiresIn", source = "expiresIn")
    @Mapping(target = "member", source = "member")
    PortalAuthLoginRespVO toLoginRespVO(String token, long expiresIn, MemberUser member);
}

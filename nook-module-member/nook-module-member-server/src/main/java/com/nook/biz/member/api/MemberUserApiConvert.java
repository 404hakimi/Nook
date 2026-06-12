package com.nook.biz.member.api;

import com.nook.biz.member.api.dto.MemberSubscriberDTO;
import com.nook.biz.member.entity.MemberUser;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MemberUserApiConvert {

    MemberUserApiConvert INSTANCE = Mappers.getMapper(MemberUserApiConvert.class);

    MemberSubscriberDTO toSubscriberDTO(MemberUser entity);
}

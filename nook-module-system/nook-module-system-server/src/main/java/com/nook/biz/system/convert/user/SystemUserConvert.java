package com.nook.biz.system.convert.user;

import com.nook.biz.system.controller.auth.vo.AuthLoginRespVO;
import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface SystemUserConvert {

    SystemUserConvert INSTANCE = Mappers.getMapper(SystemUserConvert.class);

    SystemUserRespVO convert(SystemUser entity);

    List<SystemUserRespVO> convertList(List<SystemUser> entities);

    default PageResult<SystemUserRespVO> convertPage(PageResult<SystemUser> page) {
        List<SystemUserRespVO> list = this.convertList(page.getRecords());
        return PageResult.of(page.getTotal(), list);
    }

    @Mapping(target = "token", source = "token")
    @Mapping(target = "expiresIn", source = "expiresIn")
    @Mapping(target = "user", source = "user")
    AuthLoginRespVO toLoginRespVO(String token, long expiresIn, SystemUser user);
}

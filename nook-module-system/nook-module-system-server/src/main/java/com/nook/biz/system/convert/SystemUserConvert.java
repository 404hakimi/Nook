package com.nook.biz.system.convert;

import com.nook.biz.system.controller.user.vo.SystemUserRespVO;
import com.nook.biz.system.entity.SystemUser;
import com.nook.common.web.response.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * SystemUser 实体 ↔ VO 之间的对象转换；MapStruct 在编译期生成实现。
 * 业务侧通过静态字段 {@link #INSTANCE} 调用，例如 {@code SystemUserConvert.INSTANCE.convert(user)}，不走 Spring 注入。
 */
@Mapper
public interface SystemUserConvert {

    SystemUserConvert INSTANCE = Mappers.getMapper(SystemUserConvert.class);

    /** entity → RespVO；密码哈希、deleted 等敏感/内部字段不会出现在 RespVO 中，自然被忽略。 */
    SystemUserRespVO convert(SystemUser entity);

    /** entity 列表 → RespVO 列表。 */
    List<SystemUserRespVO> convertList(List<SystemUser> entities);

    /** 分页结果转换：total 透传，records 经 convertList 转换。 */
    default PageResult<SystemUserRespVO> convertPage(PageResult<SystemUser> page) {
        return PageResult.of(page.getTotal(), convertList(page.getRecords()));
    }
}

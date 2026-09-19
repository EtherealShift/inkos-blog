package com.inkos.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.inkos.framework.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 *
 * <p>{@code BaseEntity} 上的 createBy/createTime/updateBy/updateTime 由本类统一写入，
 * 业务代码不再关心这些字段，也就不可能漏填。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_BY = "createBy";
    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_BY = "updateBy";
    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = SecurityUtils.getUsernameOrSystem();

        // strictInsertFill 只在字段为 null 时填充，不会覆盖调用方显式指定的值
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, CREATE_BY, String.class, operator);
        strictInsertFill(metaObject, UPDATE_BY, String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, UPDATE_BY, String.class, SecurityUtils.getUsernameOrSystem());
    }
}

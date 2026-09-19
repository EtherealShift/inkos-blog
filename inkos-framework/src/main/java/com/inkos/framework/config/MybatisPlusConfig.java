package com.inkos.framework.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p>注意顺序：分页拦截器必须最后添加，否则多插件叠加时 SQL 改写会错乱。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 单页最大条数上限，防止 {@code pageSize=100000} 这类请求打爆数据库。
     */
    private static final long MAX_PAGE_SIZE = 500L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁：配合实体上的 @Version 字段使用
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 防全表更新/删除：拦截没有 WHERE 条件的 update/delete
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页：不指定 DbType，由 MP 从 DataSource 自动识别，
        // 这样 dev(H2) 与 prod(MySQL) 可以共用同一份代码。
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}

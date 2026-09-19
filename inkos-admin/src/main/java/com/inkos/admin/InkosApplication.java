package com.inkos.admin;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 *
 * <p>{@code scanBasePackages} 覆盖整个 {@code com.inkos}：各层的 {@code @Component} /
 * {@code @Configuration} 分散在不同模块，靠包扫描统一装配，无需为 framework 单独写自动配置。
 *
 * <p>{@code @MapperScan} 显式限定只注册标注了 {@code @Mapper} 的接口。
 * 若不加 {@code annotationClass}，包扫描会把 {@code AuthorNameResolver} 这类业务端口接口
 * 也误注册成 MyBatis Mapper 而导致启动失败。
 */
@SpringBootApplication(scanBasePackages = "com.inkos")
@MapperScan(basePackages = "com.inkos", annotationClass = Mapper.class)
public class InkosApplication {

    public static void main(String[] args) {
        SpringApplication.run(InkosApplication.class, args);
    }
}

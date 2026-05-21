package com.nook.framework.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局序列化定制.
 *
 * <p>Spring Boot 默认用 Jackson, 这里只补 LocalDateTime / LocalDate 格式 (跟前端 formatDateTime 字符串切片对齐).
 * 之前用 fastjson2 接管 HTTP 消息转换 (FastJson2Config), 但对某些 RequestBody 有兼容性问题,
 * 已删, 回到 Jackson 默认.
 *
 * @author nook
 */
@Configuration
public class JacksonConfig {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(DATE_PATTERN);
        return builder -> {
            SimpleModule m = new SimpleModule();
            m.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtf));
            m.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtf));
            m.addSerializer(LocalDate.class, new LocalDateSerializer(df));
            m.addDeserializer(LocalDate.class, new LocalDateDeserializer(df));
            builder.modulesToInstall(m);
        };
    }
}

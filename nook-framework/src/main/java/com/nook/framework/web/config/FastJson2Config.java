package com.nook.framework.web.config;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 用 fastjson2 替换默认 Jackson 作为 Web MVC 的 JSON 序列化器。 */
@Configuration
public class FastJson2Config implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, new MediaType("application", "*+json")));
        converter.setFastJsonConfig(buildFastJsonConfig());
        // 放在 0 位，优先于 Jackson；Jackson 转换器保留作兜底以兼容个别框架内部反序列化
        converters.add(0, converter);
    }

    private FastJsonConfig buildFastJsonConfig() {
        FastJsonConfig config = new FastJsonConfig();
        // 全局日期格式: 同时作用于 Date / LocalDateTime / LocalDate
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");
        config.setWriterFeatures(
                // 输出 null 字段，避免前端字段缺失需做兼容
                JSONWriter.Feature.WriteMapNullValue,
                // null 的 List 输出为 []，方便前端直接遍历
                JSONWriter.Feature.WriteNullListAsEmpty,
                // 大数(long)序列化为字符串避免精度丢失；UUID 主键是 String 这里其实只防御性
                JSONWriter.Feature.BrowserCompatible
        );
        config.setReaderFeatures(
                // 字段名宽松匹配(下划线/驼峰互通)
                JSONReader.Feature.SupportSmartMatch
        );
        return config;
    }
}

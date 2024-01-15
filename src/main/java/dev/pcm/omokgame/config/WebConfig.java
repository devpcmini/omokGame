package dev.pcm.omokgame.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


//@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Value("${pcm.server.ip}")
    private String serverIp;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // 허용할 도메인
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 허용할 HTTP 메서드
                .allowedHeaders("Content-Type") // 허용할 헤더
                .allowCredentials(true);
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 자원에 대한 핸들러 추가
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
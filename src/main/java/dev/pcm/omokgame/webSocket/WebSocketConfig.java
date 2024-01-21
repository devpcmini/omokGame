package dev.pcm.omokgame.webSocket;

import dev.pcm.omokgame.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserService userService;

    public WebSocketConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler(), "/ws").setAllowedOrigins("*"); // WebSocket 엔드포인트 등록
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new WebSocketHandler(userService);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(9999999);
        container.setMaxBinaryMessageBufferSize(9999999);
        return container;
    }
}
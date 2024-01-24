package dev.pcm.omokgame.webSocket;

import dev.pcm.omokgame.fcm.FCMService;
import dev.pcm.omokgame.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserService userService;
    private final FCMService fCMService;

    public WebSocketConfig(UserService userService,
                           FCMService fCMService) {
        this.userService = userService;
        this.fCMService = fCMService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler(), "/ws").setAllowedOrigins("*"); // WebSocket 엔드포인트 등록
    }

    @Bean
    public WebSocketHandler webSocketHandler() {
        return new WebSocketHandler(userService, fCMService);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(9999999);
        container.setMaxBinaryMessageBufferSize(9999999);
        return container;
    }
}
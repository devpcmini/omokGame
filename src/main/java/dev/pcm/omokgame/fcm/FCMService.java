package dev.pcm.omokgame.fcm;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class FCMService{
    //Firebase Cloud Messaging
    public void sendNotification(String token,String body) throws ExecutionException, InterruptedException {
        Message message = Message.builder()
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle("Omok Game")
                                .setBody(body)
                                .build())
                        .build())
                .setToken(token)
                .build();

        FirebaseMessaging.getInstance().sendAsync(message).get();
    }
}

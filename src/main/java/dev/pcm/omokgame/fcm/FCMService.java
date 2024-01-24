package dev.pcm.omokgame.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class FCMService{

    @Value("${pcm.fcm.accountKeyPath}")
    private String accountKeyPath;

    public void sendPush(String token) throws IOException {
        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (apps.isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(getCredentials(accountKeyPath)) // 3번에서 발급받은 비공개 키
//                    .setDatabaseUrl(databaseUrl) // 없어도 간다.
                    .build();
            FirebaseApp.initializeApp(options);
        }

        Map<String, String> test = new HashMap<String, String>();
        test.put("title", "안녕");
        test.put("message", "반갑습니다.");

        Message message = Message.builder()
                .setToken(token)
                .putAllData(test)
                .build();
        try {
            log.info(ReflectionToStringBuilder.toString(message, ToStringStyle.SIMPLE_STYLE));
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            log.error(e.toString());
        }
    }

    private GoogleCredentials getCredentials(String accountKeyPath) throws IOException {
        String accountKeyFullPath =  accountKeyPath; // 3번에서 발급받은 비공개 키파일
        InputStream serviceAccountKey = new FileInputStream(accountKeyFullPath);
        return GoogleCredentials.fromStream(serviceAccountKey);
    }
}

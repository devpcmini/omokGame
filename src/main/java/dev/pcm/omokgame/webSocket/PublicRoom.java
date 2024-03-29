package dev.pcm.omokgame.webSocket;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class PublicRoom {
    String name;
    String blackPlayer = "";
    String whitePlayer = "";
    List<Takes> takes = new ArrayList<>();
}


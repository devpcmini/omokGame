package dev.pcm.omokgame.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private static Set<WebSocketSession> sessions = new HashSet<>();
    private static Set<GameRoom> gameRooms = new HashSet<>();
    private static ObjectMapper objectMapper = new ObjectMapper();;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 클라이언트 연결이 성공했을 때의 동작
        log.info("session add ==> {}", session );
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 클라이언트로부터 메시지를 받았을 때의 동작
        String payload = message.getPayload();
        log.info("payload ==> {}", payload);
        // 받은 메시지를 다른 클라이언트들에게 브로드캐스트
        handleMessage(session,payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 클라이언트 연결이 종료되었을 때의 동작
        log.info("session remove ==> {}", session );
        sessions.remove(session);
        // 연결이 종료된 세션을 모든 게임방에서 제거
        for (GameRoom gameRoom : gameRooms) {
            gameRoom.removePlayer(session);
        }
    }

    private void handleMessage(WebSocketSession session, String payload) throws IOException {
        // 클라이언트로부터 받은 메시지를 처리
        JsonNode jsonNode = objectMapper.readTree(payload);

        String type = jsonNode.get("type").asText();

        switch (type) {
            case "create":
                createRoom(session, jsonNode);
                break;
            case "join":
                joinRoom(session, jsonNode);
                break;
            case "login":
                session.sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(jsonNode)));
                break;
        }
    }

    private void createRoom(WebSocketSession session, JsonNode jsonNode) throws IOException {
        GameRoom gameRoom = new GameRoom(jsonNode.get("roomName").asText());
        gameRoom.addPlayer(session);
        gameRooms.add(gameRoom);

        // 방 목록을 모든 클라이언트에게 전송
        broadcastRoomList();
    }

    private void joinRoom(WebSocketSession session, JsonNode jsonNode) throws IOException {
        for (GameRoom gameRoom : gameRooms) {
            if (gameRoom.getRoomName().equals(jsonNode.get("roomName").asText())) {
                gameRoom.addPlayer(session);
                // 방 참가 결과를 해당 세션에 전송
                session.sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(jsonNode)));
                // 방 목록을 모든 클라이언트에게 전송
                broadcastRoomList();
                return;
            }
        }
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "create", "roomName", null, "ErrorCode", "fail")
        );
        // 해당 이름의 방이 없을 경우 에러 메시지 전송
        session.sendMessage(new TextMessage(jsonPayload));
    }

    private void broadcastRoomList() throws IOException {
        Set<GameRoom.RoomInfo> roomInfos = gameRooms.stream()
                .map(GameRoom::getRoomInfo)
                .collect(Collectors.toSet());;
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "rooms", "roomInfos", roomInfos)
        );
        log.info("roomInfos ===> {}", jsonPayload);
        for (WebSocketSession session : sessions) {
            session.sendMessage(new TextMessage(jsonPayload));
        }
    }
}


package dev.pcm.omokgame.webSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Session added ==> {}", session);
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Received payload ==> {}", payload);

        try {
            handleMessage(session, payload);
        } catch (IOException e) {
            log.error("Error handling message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        synchronized (sessions) {
            log.info("Session removed ==> {}", session);
            getOutTheRoom(session);
            sessions.remove(session);
        }
    }

    private void getOutTheRoom(WebSocketSession session) throws IOException {
        GameRoom.PlayerInfo player = (GameRoom.PlayerInfo) session.getAttributes().get("playerInfo");
        if (player != null) {
            GameRoom room = getRoomByPlayer(player);
            if (room != null) {
                room.removePlayer(player.getSession());
                if (room.getPlayers().isEmpty()) {
                    gameRooms.remove(room);
                }
            }
        }
        broadcastRoomList(session);
    }

    private void handleMessage(WebSocketSession session, String payload) throws IOException {
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
                handleLogin(session, jsonNode);
                break;
            case "placingStone":
                handlePlacingStone(session, jsonNode);
                break;
            case "getOut" :
                getOutTheRoom(session);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(jsonNode)));
                break;
            default:
                log.warn("Unhandled message type: {}", type);
        }
    }

    private void handleLogin(WebSocketSession session, JsonNode jsonNode) throws IOException {
        session.getAttributes().put("nickname", jsonNode.get("nickname").asText());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(jsonNode)));
        broadcastRoomList(null);
    }

    private void handlePlacingStone(WebSocketSession session, JsonNode jsonNode) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(jsonNode)));
    }

    private void createRoom(WebSocketSession session, JsonNode jsonNode) throws IOException {
        GameRoom gameRoom = new GameRoom(jsonNode.get("roomName").asText());
        gameRoom.addPlayer(session, session.getAttributes().get("nickname").toString(),
                jsonNode.get("stoneColor").asText());
        gameRooms.add(gameRoom);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(jsonNode)));
        broadcastRoomList(null);
    }

    private void joinRoom(WebSocketSession session, JsonNode jsonNode) throws IOException {
        String roomName = jsonNode.get("roomName").asText();

        for (GameRoom gameRoom : gameRooms) {
            if (gameRoom.getRoomName().equals(roomName)) {
                GameRoom.PlayerInfo playerInfo = gameRoom.getPlayers().iterator().next();
                String stoneColor = playerInfo.getStoneColor() == "black" ? "white"
                        : playerInfo.getStoneColor();
                gameRoom.addPlayer(session, session.getAttributes().get("nickname").toString(),stoneColor);
                sendJoinResult(session, gameRoom);
                broadcastRoomList(null);
                return;
            }
        }

        sendJoinResult(session, null);
    }

    private void sendJoinResult(WebSocketSession session, GameRoom gameRoom) throws IOException {
        if (gameRoom != null) {
            Set<GameRoom.RoomInfo> roomInfos = gameRooms.stream()
                    .map(GameRoom::getRoomInfo)
                    .collect(Collectors.toSet());

            GameRoom.RoomInfo findRoom = roomInfos.stream()
                    .filter(info -> gameRoom.getRoomName().equals(info.getRoomName()))
                    .findFirst()
                    .orElse(null);

            String jsonPayload = objectMapper.writeValueAsString(
                    Map.of("type", "join", "roomInfos", findRoom)
            );

            session.sendMessage(new TextMessage(jsonPayload));
        } else {
            String jsonPayload = objectMapper.writeValueAsString(
                    Map.of("type", "join", "roomName", "empty", "errorCode", "fail")
            );

            session.sendMessage(new TextMessage(jsonPayload));
        }
    }

    private void broadcastRoomList(WebSocketSession session) throws IOException {
        Set<GameRoom.RoomInfo> roomInfos = gameRooms.stream()
                .map(GameRoom::getRoomInfo)
                .collect(Collectors.toSet());

        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "rooms", "roomInfos", roomInfos)
        );

        log.info("Broadcasting room list ==> {}", jsonPayload);

        for (WebSocketSession sess : sessions) {
            if(session != null) {
                if (!sess.getId().equals(session.getId())) {
                    sess.sendMessage(new TextMessage(jsonPayload));
                }
            } else {
                sess.sendMessage(new TextMessage(jsonPayload));
            }
        }
    }

    private GameRoom getRoomByPlayer(GameRoom.PlayerInfo player) {
        return gameRooms.stream()
                .filter(room -> room.getPlayers().contains(player))
                .findFirst()
                .orElse(null);
    }
}



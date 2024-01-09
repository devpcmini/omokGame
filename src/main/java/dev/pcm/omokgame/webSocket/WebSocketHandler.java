package dev.pcm.omokgame.webSocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private static Set<WebSocketSession> sessions = new HashSet<>();
    private static Set<PublicRoom> publicRoom = new HashSet<>();
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
        log.info("Session removed ==> {}", session);
        sessions.remove(session);
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
            case "placingStone":
                handlePlacingStone(session, jsonNode);
                break;
            case "room_new" :
                newRoom(session, jsonNode.get("name").asText());
                break;
            case "room_leave" :
                leaveRoom(session);
                String jsonPayload = objectMapper.writeValueAsString(
                        Map.of("type", "room_leave")
                );
                session.sendMessage(new TextMessage(jsonPayload));
                sendRoomList();
                break;
            default:
                log.warn("Unhandled message type: {}", type);
        }
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

    private void newRoom(WebSocketSession session,String name) throws IOException {
        name = name.trim();
        log.info("Socket " + session.getId() + "is creating room " + name + ".");

        log.info(""+session.getAttributes().get("rooms"));
        //Socket은 ID와 같은 Room을 Default로 갖고 있음
        if (session.getAttributes().get("channel") != null) {
            log.info("Socket " + session.getId() + "is already in room.");
            log.info(""+session.getAttributes().get("channel"));
            session.sendMessage(new TextMessage("이미 다른 방에 참가중입니다."));
            return;
        }

        //동일한 방이 존재할 경우
        if (!checkDuplicateRoomName(name)) {
            log.info("Room name" + name + "already exists.");
            session.sendMessage(new TextMessage("동일한 방이 이미 존재합니다."));
            return;
        }

        PublicRoom roomInfo = new PublicRoom();
        roomInfo.setName(name);

        publicRoom.add(roomInfo);
        enterRoom(session,name);
        sendRoomList();
    }

    //중복된 이름의 방이 존재할 경우 false, 없을 경우 true
    private boolean checkDuplicateRoomName(String name) {
        return publicRoom.stream()
                .noneMatch(room -> room.getName().equals(name));
    }

    private void enterRoom(WebSocketSession session, String name) throws IOException {
        PublicRoom room = getPublicRoom(name);
        log.info("Socket " + session.getId() + "is entering room " + name);

        if (room == null) {
            session.sendMessage(new TextMessage("정상적인 방이 아닙니다."));
            return;
        }

        session.getAttributes().put("channel", name);
        broadcastMessage(session,session.getId() + "님이 입장하셨습니다.");
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "room_enter", "data", room)
        );
        session.sendMessage(new TextMessage(jsonPayload));

    }

    private PublicRoom getPublicRoom(String name) {
        for (PublicRoom room : publicRoom) {
            if (room.getName().equals(name)) {
                return room;
            }
        }
        return null;
    }

    private void broadcastMessage(WebSocketSession session, String message) {
        // 특정 채널에 속한 클라이언트들에게 메시지 브로드캐스트
        String sessionChannel = (String) session.getAttributes().get("channel");

        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && sessionChannel.equals(sess.getAttributes().get("channel"))) {
                try {
                    sess.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void leaveRoom(WebSocketSession session) throws IOException {
        String name = String.valueOf(session.getAttributes().get("channel"));
        log.info("Socket " + session.getId() + "is leaving room " + name);

        if (name != null) {
            if (countRoom(name)) {
                log.info("Remove room " + name);
                Iterator<PublicRoom> iterator = publicRoom.iterator();
                while (iterator.hasNext()) {
                    PublicRoom room = iterator.next();
                    if (room.getName().equals(name)) {
                        iterator.remove();
                    }
                }
                sendRoomList();
            } else {
                PublicRoom room = getPublicRoom(String.valueOf(session.getAttributes().get("channel")));
                if (room.blackPlayer.equals(session.getId())) {
                    room.blackPlayer = "";
                    emitPlayerChange(room);
                } else if (room.whitePlayer.equals(session.getId())) {
                    room.whitePlayer = "";
                    emitPlayerChange(room);
                }

                broadcastMessage(session,session.getId() + "님이 퇴장하셨습니다.");
            }
            session.getAttributes().remove("channel");
        }
    }

    //이름이 name인 방에 속한 Socket 개수 반환
    private boolean countRoom(String name) {
        for (PublicRoom room : publicRoom) {
            if (room.getName().equals(name)) {
                if((room.getBlackPlayer() == null || "".equals(room.getBlackPlayer()))
                    && (room.getWhitePlayer() == null || "".equals(room.getWhitePlayer()))){
                    return true;
                }
            }
        }
        return false;
    }

    private void emitPlayerChange(PublicRoom room) throws JsonProcessingException {
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "player_change", "data", room)
        );
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && room.getName().equals(sess.getAttributes().get("channel"))) {
                try {
                    sess.sendMessage(new TextMessage(jsonPayload));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!"".equals(room.getBlackPlayer()) && !"".equals(room.getWhitePlayer())) {
            room.setTakes(null);
            String playerSelect = objectMapper.writeValueAsString(
                    Map.of("type", "player_select")
            );
            for (WebSocketSession sess : sessions) {
                if (sess.isOpen() && sess.getId().equals(room.getBlackPlayer())) {
                    try {
                        sess.sendMessage(new TextMessage(playerSelect));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void sendRoomList() throws IOException {
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "room_list", "data", publicRoom)
        );
        for (WebSocketSession sess : sessions) {
            sess.sendMessage(new TextMessage(jsonPayload));
        }
    }
}



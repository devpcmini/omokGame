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
        leaveRoom(session);
        sessions.remove(session);
    }

    private void handleMessage(WebSocketSession session, String payload) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(payload);
        String type = jsonNode.get("type").asText();
        String jsonPayload;
        switch (type) {
            case "room_new" :
                newRoom(session, jsonNode.get("name").asText());
                break;
            case "room_leave" :
                leaveRoom(session);
                jsonPayload = objectMapper.writeValueAsString(
                        Map.of("type", "room_leave")
                );
                session.sendMessage(new TextMessage(jsonPayload));
                sendRoomList();
                break;
            case "player_change" :
                String roomName = String.valueOf(session.getAttributes().get("channel"));
                PublicRoom room = getPublicRoom(roomName);
                String color = jsonNode.get("data").asText();
                if ("black".equals(color)) {
                    if (!("".equals(room.getBlackPlayer()) || room.getBlackPlayer() == null)) {
                        jsonPayload = objectMapper.writeValueAsString(
                                Map.of("type", "error", "msg", "다른 플레이어가 참가중입니다.")
                        );
                        session.sendMessage(new TextMessage(jsonPayload));
                        return;
                    } else {
                        if (session.getId().equals(room.getWhitePlayer())) {
                            room.setWhitePlayer("");
                        }
                        room.setBlackPlayer(session.getId());
                    }
                } else if ("white".equals(color)) {
                    if (!("".equals(room.getWhitePlayer()) || room.getWhitePlayer() == null)) {
                        jsonPayload = objectMapper.writeValueAsString(
                                Map.of("type", "error", "msg", "다른 플레이어가 참가중입니다.")
                        );
                        session.sendMessage(new TextMessage(jsonPayload));
                        return;
                    } else {
                        if (session.getId().equals(room.getBlackPlayer())) {
                            room.setBlackPlayer("");
                        }
                        room.setWhitePlayer(session.getId());
                    }
                } else if ("spectator".equals(color)) {
                    if (session.getId().equals(room.getBlackPlayer())) {
                        room.setBlackPlayer("");
                    } else if (session.getId().equals(room.getWhitePlayer())) {
                        room.setWhitePlayer("");
                    } else {
                        return;
                    }
                }
                emitPlayerChange(room);
                break;
            case "room_enter" :
                if (session.getAttributes().get("channel") != null) {
                    log.info("Socket " + session.getId() + "is already in room.");
                    session.sendMessage(new TextMessage("이미 다른 방에 참가중입니다."));
                    return;
                }
                enterRoom(session,jsonNode.get("name").asText());
                break;
            default:
                log.warn("Unhandled message type: {}", type);
        }
    }

    //방 만들기 시 실행되는 메소드
    private void newRoom(WebSocketSession session,String name) throws IOException {
        name = name.trim();
        log.info("Socket " + session.getId() + "is creating room " + name + ".");

        log.info(""+session.getAttributes().get("rooms"));
        
        //연결되어있는 채널이 있는지 확인
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

    //방 입장하기 메소드
    private void enterRoom(WebSocketSession session, String name) throws IOException {
        PublicRoom room = getPublicRoom(name);
        log.info("Socket " + session.getId() + "is entering room " + name);

        if (room == null) {
            session.sendMessage(new TextMessage("정상적인 방이 아닙니다."));
            return;
        }

        session.getAttributes().put("channel", name);
        String broadMessage = objectMapper.writeValueAsString(
                Map.of("type", "message", "data", "[입장] => " + session.getId())
                );
        broadcastMessage(session,broadMessage);
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "room_enter", "data", room)
        );
        session.sendMessage(new TextMessage(jsonPayload));
    }

    //name에 해당하는 방 return
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

    //방 떠나기
    private void leaveRoom(WebSocketSession session) throws IOException {
        String name = session.getAttributes().get("channel") == null ? null : String.valueOf(session.getAttributes().get("channel"));
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
                if (session.getId().equals(room.getBlackPlayer())) {
                    room.setBlackPlayer("");
                    emitPlayerChange(room);
                } else if (session.getId().equals(room.getWhitePlayer())) {
                    room.setWhitePlayer("");
                    emitPlayerChange(room);
                }

                String broadMessage = objectMapper.writeValueAsString(
                        Map.of("type", "message", "data", "[퇴장] => " + session.getId())
                );
                broadcastMessage(session,broadMessage);
            }
            session.getAttributes().remove("channel");
        }
    }

    //이름이 name인 방이 없다면 true 있으면 false
    private boolean countRoom(String name) {
        for (WebSocketSession sess : sessions) {
            if (String.valueOf(sess.getAttributes().get("channel")).equals(name)) {
                return false;
            }
        }
        return true;
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
            if(sess.isOpen()) {
                sess.sendMessage(new TextMessage(jsonPayload));
            }
        }
    }
}



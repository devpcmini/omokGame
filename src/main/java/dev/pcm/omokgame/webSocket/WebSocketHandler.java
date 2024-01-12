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
import java.util.*;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private static Set<WebSocketSession> sessions = new HashSet<>();
    private static Set<PublicRoom> publicRoom = new HashSet<>();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Session added ==> {}", session);
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
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
        String roomName = String.valueOf(session.getAttributes().get("channel"));
        PublicRoom room = getPublicRoom(roomName);
        switch (type) {
            case "start" :
                jsonPayload = objectMapper.writeValueAsString(
                        Map.of("type", "start")
                );
                broadcastMessage(roomName,jsonPayload);
                break;
            case "createRoom" :
                newRoom(session, jsonNode.get("name").asText());
                break;
            case "leaveRoom" :
                leaveRoom(session);
                jsonPayload = objectMapper.writeValueAsString(
                        Map.of("type", "leaveRoom")
                );
                session.sendMessage(new TextMessage(jsonPayload));
                sendRoomList();
                break;
            case "changeRole" :
                String color = jsonNode.get("data").asText();
                if ("black".equals(color)) {
                    if(room != null) {
                        if (!"".equals(room.getBlackPlayer())) {
                            jsonPayload = objectMapper.writeValueAsString(
                                    Map.of("type", "error", "msg", "다른 플레이어가 참가중입니다." )
                            );
                            session.sendMessage(new TextMessage(jsonPayload));
                            return;
                        } else {
                            if (session.getId().equals(room.getWhitePlayer())) {
                                room.setWhitePlayer("");
                            }
                            room.setBlackPlayer(session.getId());
                        }
                    }
                } else if ("white".equals(color)) {
                    if(room != null) {
                        if (!"".equals(room.getWhitePlayer())) {
                            jsonPayload = objectMapper.writeValueAsString(
                                    Map.of("type", "error", "msg", "다른 플레이어가 참가중입니다." )
                            );
                            session.sendMessage(new TextMessage(jsonPayload));
                            return;
                        } else {
                            if (session.getId().equals(room.getBlackPlayer())) {
                                room.setBlackPlayer("");
                            }
                            room.setWhitePlayer(session.getId());
                        }
                    }
                } else if ("viewer".equals(color)) {
                    if(room != null) {
                        if (session.getId().equals(room.getBlackPlayer())) {
                            room.setBlackPlayer("");
                        } else if (session.getId().equals(room.getWhitePlayer())) {
                            room.setWhitePlayer("");
                        } else {
                            return;
                        }
                    } else {
                        return;
                    }
                }
                emitPlayerChange(room);
                break;
            case "joinRoom" :
                if (session.getAttributes().get("channel") != null) {
                    log.info("Socket " + session.getId() + "is already in room.");
                    String errorMessage = objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "이미 다른 방에 참가중입니다.")
                    );
                    session.sendMessage(new TextMessage(errorMessage));
                    return;
                }
                enterRoom(session,jsonNode.get("name").asText());
                break;
            case "roomList" :
                sendRoomList();
                break;
            case "move" :
                String broadMessage;
                String errorMessage;
                String playerSelect = objectMapper.writeValueAsString(
                        Map.of("type", "player_select")
                );
                if (room == null) {
                    log.info("Room " + roomName + "is not existing.");
                    return;
                }

                boolean isBlackTurn = room.getTakes().size() % 2 == 0;

                if (isBlackTurn) {
                    //흑돌
                    if (!session.getId().equals(room.getBlackPlayer())) {
                        errorMessage = objectMapper.writeValueAsString(
                                Map.of("type", "error", "data", "흑돌 플레이어가 아닙니다.")
                        );
                        session.sendMessage(new TextMessage(errorMessage));
                        return;
                    }
                } else {
                    //백돌
                    if (!session.getId().equals(room.getWhitePlayer())) {
                        errorMessage = objectMapper.writeValueAsString(
                                Map.of("type", "error", "data", "백돌 플레이어가 아닙니다.")
                        );
                        session.sendMessage(new TextMessage(errorMessage));
                        return;
                    }
                }

                if ("".equals(room.getBlackPlayer()) ||
                      "".equals(room.getWhitePlayer())) {
                    errorMessage = objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "상대가 존재하지 않습니다.")
                    );
                    session.sendMessage(new TextMessage(errorMessage));
                    return;
                }

                if (room.getTakes().stream()
                        .filter(t -> t.getX() == jsonNode.get("data").get("x").asInt()
                                && t.getY() == jsonNode.get("data").get("y").asInt())
                        .findFirst()
                        .orElse(null) != null) {
                    errorMessage = objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "이미 다른 돌이 위치하고 있습니다.")
                    );
                    session.sendMessage(new TextMessage(errorMessage));
                    session.sendMessage(new TextMessage(playerSelect));
                    return;
                }

                Takes takes = new Takes();
                takes.setX(jsonNode.get("data").get("x").asInt());
                takes.setY(jsonNode.get("data").get("y").asInt());
                room.getTakes().add(takes);
                broadMessage = objectMapper.writeValueAsString(
                        Map.of("type", "move", "data", jsonNode.get("data"))
                );
                broadcastMessage(roomName,broadMessage);

                if (checkOmokCompleted(jsonNode.get("data"), room.getTakes())) {
                    log.info("Omok completed!");
                    broadMessage = objectMapper.writeValueAsString(
                            Map.of("type", "end", "data", isBlackTurn ? "black" : "white")
                    );
                    broadcastMessage(roomName,broadMessage);
                    broadMessage = objectMapper.writeValueAsString(
                            Map.of("type", "message", "data", "[승리] => " +session.getId())
                    );
                    broadcastMessage(roomName,broadMessage);
                    room.setBlackPlayer("");
                    room.setWhitePlayer("");
                    emitPlayerChange(room);
                    return;
                }
                if (isBlackTurn) {
                    sendMessageToSession(room.getWhitePlayer(),playerSelect);
                } else {
                    sendMessageToSession(room.getBlackPlayer(),playerSelect);
                }
                break;
            default:
                log.warn("Unhandled message type: {}", type);
        }
    }

    //오목 완성 판별
    private boolean checkOmokCompleted(JsonNode coord, List<Takes> takes) {
        return checkWin(takes, coord);
    }

    private boolean checkWin(List<Takes> takes, JsonNode coord) {
        List<Offset> offsets = List.of(
                new Offset(1, 0),   // 가로
                new Offset(1, 1),   // 대각선1
                new Offset(0, 1),   // 세로
                new Offset(-1, 1)   // 대각선2
        );

        return offsets.stream().anyMatch(dir -> {
            int streak = 1;
            int type = (takes.size() - 1) % 2;

            // 정방향
            for (int x = coord.get("x").asInt() + dir.getX(), y = coord.get("y").asInt() + dir.getY();
                 x > 0 && x < 19 && y > 0 && y < 19;
                 x += dir.getX(), y += dir.getY()) {
                int currentX = x;
                int currentY = y;

                if (takes.stream().anyMatch(t -> t.getX() == currentX && t.getY() == currentY && takes.indexOf(t) % 2 == type)) {
                    streak++;
                } else {
                    break;
                }
            }

            // 반대방향
            for (int x = coord.get("x").asInt() - dir.getX(), y = coord.get("y").asInt() - dir.getY();
                 x > 0 && x < 19 && y > 0 && y < 19;
                 x -= dir.getX(), y -= dir.getY()) {
                int currentX = x;
                int currentY = y;

                if (takes.stream().anyMatch(t -> t.getX() == currentX && t.getY() == currentY && takes.indexOf(t) % 2 == type)) {
                    streak++;
                } else {
                    break;
                }
            }

            return streak == 5;
        });
    }

    //방 만들기 시 실행되는 메소드
    private void newRoom(WebSocketSession session,String name) throws IOException {
        name = name.trim();
        log.info("Socket " + session.getId() + "is creating room " + name + ".");

        log.info(""+session.getAttributes().get("rooms"));
        String errorMessage;
        //연결되어있는 채널이 있는지 확인
        if (session.getAttributes().get("channel") != null) {
            log.info("Socket " + session.getId() + "is already in room.");
            log.info(""+session.getAttributes().get("channel"));
            errorMessage = objectMapper.writeValueAsString(
                    Map.of("type", "error", "data", "이미 다른 방에 참가중입니다.")
            );
            session.sendMessage(new TextMessage(errorMessage));
            return;
        }

        //동일한 방이 존재할 경우
        if (!checkDuplicateRoomName(name)) {
            log.info("Room name" + name + "already exists.");
            errorMessage = objectMapper.writeValueAsString(
                    Map.of("type", "error", "data", "동일한 방이 이미 존재합니다.")
            );
            session.sendMessage(new TextMessage(errorMessage));
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
        String errorMessage = objectMapper.writeValueAsString(
                Map.of("type", "error", "data", "정상적인 방이 아닙니다.")
        );
        if (room == null) {
            session.sendMessage(new TextMessage(errorMessage));
            return;
        }

        session.getAttributes().put("channel", name);
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "joinRoom", "data", room)
        );
        session.sendMessage(new TextMessage(jsonPayload));
        String broadMessage = objectMapper.writeValueAsString(
                Map.of("type", "message", "data", "[입장] => " + session.getId())
        );
        broadcastMessage(name,broadMessage);
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


    private void broadcastMessage(String sessionChannel, String message) {
        // 특정 채널에 속한 클라이언트들에게 메시지 브로드캐스트

        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && (sessionChannel.equals(sess.getAttributes().get("channel")))) {
                try {
                    sess.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendMessageToSession(String id, String message){
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && id.equals(sess.getId())) {
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
        session.getAttributes().remove("channel");
        if (name != null) {
            if (countRoom(name)) {
                Iterator<PublicRoom> iterator = publicRoom.iterator();
                while (iterator.hasNext()) {
                    PublicRoom room = iterator.next();
                    if (room.getName().equals(name)) {
                        iterator.remove();
                    }
                }
                sendRoomList();
            } else {
                PublicRoom room = getPublicRoom(name);
                if(room != null) {
                    if (session.getId().equals(room.getBlackPlayer())) {
                        room.setBlackPlayer("" );
                        emitPlayerChange(room);
                    } else if (session.getId().equals(room.getWhitePlayer())) {
                        room.setWhitePlayer("" );
                        emitPlayerChange(room);
                    }
                }

                String broadMessage = objectMapper.writeValueAsString(
                        Map.of("type", "message", "data", "[퇴장] => " + session.getId())
                );
                broadcastMessage(name,broadMessage);
            }
        }
    }

    //이름이 name인 방이 없다면 true 있으면 false
    private boolean countRoom(String name) {
        int count = 0;
        for (WebSocketSession sess : sessions) {
            if(sess.getAttributes().get("channel") != null) {
                if (String.valueOf(sess.getAttributes().get("channel")).equals(name)) {
                    count++;
                }
            }
        }
        return count == 0;
    }


    private void emitPlayerChange(PublicRoom room) throws JsonProcessingException {
        String jsonPayload = objectMapper.writeValueAsString(
                Map.of("type", "changeRole", "data", room)
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

        if (!"".equals(room.getBlackPlayer()) || !"".equals(room.getWhitePlayer())) {
            room.setTakes(new ArrayList<>());
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
                Map.of("type", "roomList", "data", publicRoom)
        );
        for (WebSocketSession sess : sessions) {
            if(sess.isOpen()) {
                sess.sendMessage(new TextMessage(jsonPayload));
            }
        }
    }
}



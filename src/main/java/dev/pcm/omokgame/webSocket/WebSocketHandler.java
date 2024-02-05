package dev.pcm.omokgame.webSocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pcm.omokgame.entity.UserEntity;
import dev.pcm.omokgame.fcm.FCMService;
import dev.pcm.omokgame.kafka.KafkaProducerService;
import dev.pcm.omokgame.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private final UserService userService;
    private final FCMService fCMService;
    private final KafkaProducerService kafkaProducerService;

    public WebSocketHandler(UserService userService,
                            FCMService fCMService,
                            KafkaProducerService kafkaProducerService) {
        this.userService = userService;
        this.fCMService = fCMService;
        this.kafkaProducerService = kafkaProducerService;
    }

    private static Set<WebSocketSession> sessions = new HashSet<>();
    private static Set<PublicRoom> publicRoom = new HashSet<>();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Value("${pcm.fcm.accountKeyPath}")
    private String accountKeyPath;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
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
        if(session.getAttributes().get("userId") != null) {
            UserEntity user = new UserEntity();
            String userId = String.valueOf(session.getAttributes().get("userId"));
            user.setUserId(userId);
            user.setSessionid(null);
            userService.updateUser(user,true);
        }
        sessions.remove(session);
    }

    private void handleMessage(WebSocketSession session, String payload) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(payload);
        String type = jsonNode.get("type").asText();
        UserEntity user = new UserEntity();
        String roomName = String.valueOf(session.getAttributes().get("channel"));
        PublicRoom room = getPublicRoom(roomName);
        String userId = null;
        String password = null;
        String email = null            ;
        if(type.equals("login") || type.equals("signUp")) {
            userId = jsonNode.get("userId").asText();
            password = jsonNode.get("password").asText();
        }
        switch (type) {
            case "idChk" :
                userId = jsonNode.get("userId").asText();
                email = jsonNode.get("email").asText();
                user.setUserId(userId);
                user.setEmail(email);
                UserEntity idChk = userService.findByUserIdAndEmail(user);
                if(idChk != null) {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "idChk", "data", "Success")
                    ));
                } else {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "idChk","data", "입력한 정보에 해당하는 사용자가 없습니다.")
                    ));
                }
                break;
            case "savePwd" :
                userId = jsonNode.get("userId").asText();
                email = jsonNode.get("email").asText();
                password = jsonNode.get("password").asText();
                user.setUserId(userId);
                user.setEmail(email);
                user.setPassword(password);

                UserEntity result = userService.findByUserIdAndEmail(user);
                if(result != null) {
                    user.setId(result.getId());
                    userService.updateUser(user,false);
                    fcmSendMessage(String.valueOf(session.getAttributes().get("fcmToken")),"비밀번호가 변경되었습니다.");
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "savePwd", "data", "비밀번호가 변경되었습니다.")
                    ));
                } else {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "savePwd","data", "입력한 정보에 해당하는 사용자가 없습니다.")
                    ));
                }
                break;
            case "connect" :
                String token = jsonNode.get("token").asText();
                session.getAttributes().put("fcmToken",token);
                break;
            case "login" :
                userId = jsonNode.get("userId").asText();
                password = jsonNode.get("password").asText();
                user.setUserId(userId);
                user.setPassword(password);

                UserEntity loginResult = userService.findByUserIdAndPassword(user);
                if(loginResult != null) {
                    session.getAttributes().put("userId",userId);
                    user.setSessionid(session.getId());
                    user.setEmail(loginResult.getEmail());
                    user.setId(loginResult.getId());
                    userService.updateUser(user,false);
                    fcmSendMessage(String.valueOf(session.getAttributes().get("fcmToken")),"로그인");
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "login", "data", userId)
                    ));
                } else {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "login","data", "입력한 정보가 올바르지 않습니다.")
                    ));
                }
                sendRoomList();
                break;
            case "signUp" :
                email = jsonNode.get("email").asText();

                user.setUserId(userId);
                user.setPassword(password);
                user.setEmail(email);
                UserEntity signUpresult = userService.saveUser(user);
                if(signUpresult != null){
                    session.getAttributes().put("userId",userId);
                    fcmSendMessage(String.valueOf(session.getAttributes().get("fcmToken")),"회원가입을 축하합니다.");
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "signUp", "data", "회원가입되었습니다. 가입하신 아이디로 로그인해주세요.")
                    ));
                } else {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "signUp", "data", "이미 존재하는 회원입니다.")
                    ));
                }
                break;
            case "start" :
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "start")
                ));
                break;
            case "createRoom" :
                newRoom(session, jsonNode.get("name").asText());
                break;
            case "leaveRoom" :
                leaveRoom(session);
                SessionSendMessage(session,objectMapper.writeValueAsString(
                        Map.of("type", "leaveRoom")
                ));
                sendRoomList();
                break;
            case "changeRole" :
                String color = jsonNode.get("data").asText();
                if ("black".equals(color)) {
                    if(room != null) {
                        if (!"".equals(room.getBlackPlayer())) {
                            SessionSendMessage(session,objectMapper.writeValueAsString(
                                    Map.of("type", "error", "data", "다른 플레이어가 참가중입니다." )
                            ));
                            return;
                        } else {
                            if (session.getAttributes().get("userId").equals(room.getWhitePlayer())) {
                                room.setWhitePlayer("");
                            }
                            room.setBlackPlayer(String.valueOf(session.getAttributes().get("userId")));
                        }
                    }
                } else if ("white".equals(color)) {
                    if(room != null) {
                        if (!"".equals(room.getWhitePlayer())) {
                            SessionSendMessage(session,objectMapper.writeValueAsString(
                                    Map.of("type", "error", "data", "다른 플레이어가 참가중입니다." )
                            ));
                            return;
                        } else {
                            if (session.getAttributes().get("userId").equals(room.getBlackPlayer())) {
                                room.setBlackPlayer("");
                            }
                            room.setWhitePlayer(String.valueOf(session.getAttributes().get("userId")));
                        }
                    }
                } else if ("viewer".equals(color)) {
                    if(room != null) {
                        if (session.getAttributes().get("userId").equals(room.getBlackPlayer())) {
                            room.setBlackPlayer("");
                        } else if (session.getAttributes().get("userId").equals(room.getWhitePlayer())) {
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
                    log.info("Socket " + session.getAttributes().get("userId") + "is already in room.");
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "이미 다른 방에 참가중입니다.")
                    ));
                    return;
                }
                enterRoom(session,jsonNode.get("name").asText());
                break;
            case "roomList" :
                sendRoomList();
                break;
            case "undoMove" :
                room.getTakes().remove(room.getTakes().size() - 1);
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "undoMove","data", room.getTakes())
                ));
                break;
            case "giveUp" :
                String giveUpColor = String.valueOf(jsonNode.get("color").asText());
                String giveUpUser = room.getBlackPlayer().equals(String.valueOf(jsonNode.get("userId").asText())) ? room.getBlackPlayer() : room.getWhitePlayer();
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "end", "data", giveUpColor.equals("black") ? "white" : "black")
                ));
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "message", "data", "<b class='systemMessage'>[항복] '" + giveUpUser + "'님이 항복하여서 '"
                                + (giveUpColor.equals("black") ? room.getWhitePlayer() : room.getBlackPlayer())  + "'님이 승리하셨습니다. </b>")
                ));
                room.setBlackPlayer("");
                room.setWhitePlayer("");
                emitPlayerChange(room);
                break;
            case "timeOut" :
                String timeOutColor = String.valueOf(jsonNode.get("color").asText());
                String timeOutUser = room.getBlackPlayer().equals(String.valueOf(jsonNode.get("userId").asText())) ? room.getBlackPlayer() : room.getWhitePlayer();
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "end", "data", timeOutColor.equals("black") ? "white" : "black")
                ));
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "message", "data", "<b class='systemMessage'>[시간 초과] 시간 초과로 인해 '" + timeOutUser + "'님이 승리하셨습니다.</b>")
                ));
                room.setBlackPlayer("");
                room.setWhitePlayer("");
                emitPlayerChange(room);
                break;
            case "move" :
                if (room == null) {
                    log.info("Room " + roomName + "is not existing.");
                    return;
                }

                boolean isBlackTurn = room.getTakes().size() % 2 == 0;

                if (isBlackTurn) {
                    //흑돌
                    if (!session.getAttributes().get("userId").equals(room.getBlackPlayer())) {
                        SessionSendMessage(session,objectMapper.writeValueAsString(
                                Map.of("type", "error", "data", "흑돌 플레이어가 아닙니다.")
                        ));
                        return;
                    }
                } else {
                    //백돌
                    if (!session.getAttributes().get("userId").equals(room.getWhitePlayer())) {
                        SessionSendMessage(session,objectMapper.writeValueAsString(
                                Map.of("type", "error", "data", "백돌 플레이어가 아닙니다.")
                        ));
                        return;
                    }
                }

                if ("".equals(room.getBlackPlayer()) ||
                      "".equals(room.getWhitePlayer())) {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "상대가 존재하지 않습니다.")
                    ));
                    return;
                }

                if (room.getTakes().stream()
                        .filter(t -> t.getX() == jsonNode.get("data").get("x").asInt()
                                && t.getY() == jsonNode.get("data").get("y").asInt())
                        .findFirst()
                        .orElse(null) != null) {
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "error", "data", "이미 다른 돌이 위치하고 있습니다.")
                    ));
                    SessionSendMessage(session,objectMapper.writeValueAsString(
                            Map.of("type", "player_select")
                    ));
                    return;
                }

                Takes takes = new Takes();
                takes.setX(jsonNode.get("data").get("x").asInt());
                takes.setY(jsonNode.get("data").get("y").asInt());
                room.getTakes().add(takes);
                if(room.getTakes().size() % 2 == 1) {
                    //쌍삼 체크
                    log.info("omok33Rule ==> {}",omok33Rule(room.getTakes(), jsonNode.get("data")));
                    if (omok33Rule(room.getTakes(), jsonNode.get("data"))) {
                        room.getTakes().remove(room.getTakes().size() - 1);
                        broadcastMessage(roomName, objectMapper.writeValueAsString(
                                Map.of("type", "message", "data", "<b class='systemMessage'>[쌍삼] '쌍삼'으로 인해 해당 수를 둘 수 없습니다. 다른 수를 선택해주세요.</b>")
                        ));
                        return;
                    }
                    //44 체크
                    log.info("omok44Rule ==> {}",omok44Rule(room.getTakes(), jsonNode.get("data")));
                    if (omok44Rule(room.getTakes(), jsonNode.get("data"))) {
                        room.getTakes().remove(room.getTakes().size() - 1);
                        broadcastMessage(roomName, objectMapper.writeValueAsString(
                                Map.of("type", "message", "data", "<b class='systemMessage'>[사사] '사사' 금수 상황이 있습니다. 해당 수를 두면 안 됩니다.</b>")
                        ));
                        return;
                    }
                    //장목 체크
                    log.info("omokJangmokRule ==> {}",omokJangmokRule(room.getTakes(), jsonNode.get("data")));
                    if (omokJangmokRule(room.getTakes(), jsonNode.get("data"))) {
                        room.getTakes().remove(room.getTakes().size() - 1);
                        broadcastMessage(roomName, objectMapper.writeValueAsString(
                                Map.of("type", "message", "data", "<b class='systemMessage'>[장목] '장목' 금수 상황이 있습니다. 해당 수를 두면 안 됩니다.</b>")
                        ));
                        return;
                    }
                }
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "move", "data", jsonNode.get("data"))
                ));
                //완성 체크
                if (checkWin(room.getTakes(),jsonNode.get("data"))) {
                    log.info("Omok completed!");
                    broadcastMessage(roomName,objectMapper.writeValueAsString(
                            Map.of("type", "end", "data", isBlackTurn ? "black" : "white")
                    ));
                    broadcastMessage(roomName,objectMapper.writeValueAsString(
                            Map.of("type", "message", "data", "<b class='systemMessage'>[승리] '" +session.getAttributes().get("userId") + "'님이 오목을 완료하여 승리하셨습니다.</b>")
                    ));
                    room.setBlackPlayer("");
                    room.setWhitePlayer("");
                    emitPlayerChange(room);
                    return;
                }

                if (isBlackTurn) {
                    sendMessageToSession(room.getWhitePlayer(),objectMapper.writeValueAsString(
                            Map.of("type", "player_select")
                    ));
                } else {
                    sendMessageToSession(room.getBlackPlayer(),objectMapper.writeValueAsString(
                            Map.of("type", "player_select")
                    ));
                }
                break;
            case "sendMessage" :
                broadcastMessage(roomName,objectMapper.writeValueAsString(
                        Map.of("type", "sendMessage", "data", "<b class='userMessage'> [" + session.getAttributes().get("userId")  + "] => " + jsonNode.get("message").asText() + "</b>")
                ));
                break;
            case "availUsers" :
                SessionSendMessage(session,objectMapper.writeValueAsString(
                        Map.of("type", "availUsers", "data", findNonParticipants().stream().collect(Collectors.joining(",")))
                ));
                break;
            case "invite" :
                inviteNonParticipants(session,jsonNode.get("data").asText());
                break;
            default:
                log.warn("Unhandled message type: {}", type);
        }
    }

    //오목 완성 판별
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
        log.info("Socket " + session.getAttributes().get("userId") + "is creating room " + name + ".");

        //연결되어있는 채널이 있는지 확인
        if (session.getAttributes().get("channel") != null) {
            log.info("Socket " + session.getAttributes().get("userId") + "is already in room.");
            log.info(""+session.getAttributes().get("channel"));
            SessionSendMessage(session,objectMapper.writeValueAsString(
                    Map.of("type", "error", "data", "이미 다른 방에 참가중입니다.")
            ));
            return;
        }

        //동일한 방이 존재할 경우
        if (!checkDuplicateRoomName(name)) {
            log.info("Room name" + name + "already exists.");
            SessionSendMessage(session,objectMapper.writeValueAsString(
                    Map.of("type", "error", "data", "동일한 방이 이미 존재합니다.")
            ));
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
        log.info("Socket " + session.getAttributes().get("userId") + "is entering room " + name);
        if (room == null) {
            SessionSendMessage(session,objectMapper.writeValueAsString(
                    Map.of("type", "error", "data", "정상적인 방이 아닙니다.")
            ));
            return;
        }

        session.getAttributes().put("channel", name);
        SessionSendMessage(session,objectMapper.writeValueAsString(
                Map.of("type", "joinRoom", "data", room)
        ));
        broadcastMessage(name,objectMapper.writeValueAsString(
                Map.of("type", "message", "data", "<b class='systemMessage'>[입장] '" + session.getAttributes().get("userId") + "'님이 입장하셨습니다.</b>")
        ));
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

    // 특정 채널에 속한 클라이언트들에게 메시지 전달
    private void broadcastMessage(String sessionChannel, String message) {
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && (sessionChannel.equals(sess.getAttributes().get("channel")))) {
                try {
                    SessionSendMessage(sess,message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //존재하는 세션 중에 방에 입장하지 않은 유저중에서 id 찾아서 초대보내기
    private void inviteNonParticipants(WebSocketSession session,String userId) throws JsonProcessingException {
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && sess.getAttributes().get("channel") == null && sess.getAttributes().get("userId").equals(userId)) {
                try {
                    SessionSendMessage(sess,objectMapper.writeValueAsString(
                            Map.of("type", "invite", "data",
                                    session.getAttributes().get("userId") + "님이 '" + session.getAttributes().get("channel") + "'에서 당신을 초대합니다.")
                    ));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //채널이 없는 유저목록 조회
    private List<String> findNonParticipants(){
        List<String> result = new ArrayList<>();
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && sess.getAttributes().get("channel") == null) {
                result.add(String.valueOf(sess.getAttributes().get("userId")));
            }
        }
        return result;
    }

    //세션아이디에 해당하는 세션에 메세지 전송
    private void sendMessageToSession(String id, String message){
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && id.equals(sess.getAttributes().get("userId"))) {
                try {
                    SessionSendMessage(sess,message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //방 떠나기
    private void leaveRoom(WebSocketSession session) throws IOException {
        String name = session.getAttributes().get("channel") == null ? null : String.valueOf(session.getAttributes().get("channel"));
        log.info("Socket " + session.getAttributes().get("userId") + "is leaving room " + name);
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
                    if (session.getAttributes().get("userId").equals(room.getBlackPlayer())) {
                        room.setBlackPlayer("");
                        emitPlayerChange(room);
                    } else if (session.getAttributes().get("userId").equals(room.getWhitePlayer())) {
                        room.setWhitePlayer("");
                        emitPlayerChange(room);
                    }
                }

                broadcastMessage(name,objectMapper.writeValueAsString(
                        Map.of("type", "message", "data", "<b class='systemMessage'>[퇴장] '" + session.getAttributes().get("userId") + "'님이 퇴장하셨습니다. </b>")
                ));
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


    //해당 방에 있는 유저들에게 흑돌, 백돌, 관전 전달
    private void emitPlayerChange(PublicRoom room) throws JsonProcessingException {
        for (WebSocketSession sess : sessions) {
            if (sess.isOpen() && room.getName().equals(sess.getAttributes().get("channel"))) {
                try {
                    SessionSendMessage(sess,objectMapper.writeValueAsString(Map.of("type", "changeRole", "data", room)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!"".equals(room.getBlackPlayer()) || !"".equals(room.getWhitePlayer())) {
            room.setTakes(new ArrayList<>());
            for (WebSocketSession sess : sessions) {
                if (sess.isOpen() && sess.getAttributes().get("userId") != null && sess.getAttributes().get("userId").equals(room.getBlackPlayer())) {
                    try {
                        SessionSendMessage(sess,objectMapper.writeValueAsString(Map.of("type", "player_select")));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //방 목록 전달
    private void sendRoomList() throws IOException {
        for (WebSocketSession sess : sessions) {
            if(sess.isOpen()) {
                SessionSendMessage(sess,objectMapper.writeValueAsString(Map.of("type", "roomList", "data", publicRoom)));
            }
        }
    }

    private void fcmSendMessage(String token, String message){
        try {
            fCMService.sendNotification(token,message);
        } catch (ExecutionException e) {
            log.error("sendMessage ExecutionException ==> {}", e.toString());
        } catch (InterruptedException e) {
            log.error("sendMessage InterruptedException ==> {}", e.toString());
        }
    }


    private void SessionSendMessage(WebSocketSession session, String message) throws IOException {
        kafkaProducerService.sendKafkaMessage(message);
        session.sendMessage(new TextMessage(message));
    }

    /*************************************************************33***************************************************/
    //열린 3이 2개이상이면 쌍삼으로 간주하여 true리턴
    private boolean omok33Rule(List<Takes> takes, JsonNode coord) {
        int count = 0;
        count += validateDoubleThrees1(takes,coord);
        count += validateDoubleThrees2(takes,coord);
        count += validateDoubleThrees3(takes,coord);
        count += validateDoubleThrees4(takes,coord);

        if(count >= 2) {
            return true;
        } else {
            return false;
        }
    }

    // ← → 탐색
    // ─ 탐색 : 열린3이 되면 1을 리턴 아니면 0 리턴
    private int validateDoubleThrees1(List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        //열린 3인지 체크하기위한것..
        int blink1 = 1;

        //blink2 는 blink1 과 같음 중간에서넣어줄거임.
        // ←
        int xx = coord.get("x").asInt()-1; //달라지는 좌표
        boolean check = false;
        while(true){
            //좌표끝도달
            if(xx == -1) {
                break;
            }
            //check를 false로 바꿈으로 두번연속으로 만나는지 확인할수있게.
            if(checkValidMove(takes,coord.get("y").asInt(),xx,0)) {
                check = false;
                stone1++;
            }
            //상대돌을 만나면 탐색중지
            if(checkValidMove(takes,coord.get("y").asInt(),xx,1)) {
                break;
            }
            if(!checkValidMove(takes,coord.get("y").asInt(),xx,null)) {
                //처음 빈공간을만나 check가 true가 됬는데
                //연달아 빈공간을만나면 탐색중지
                //두번연속으로 빈공간만날시 blink카운트를 되돌림.
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                } else {
                    break; //빈공간을만났으나 빈공간을 두번만나면 끝임
                }
            }
            //계속탐색
            xx--;
        }
        // →
        xx = coord.get("x").asInt()+1; //달라지는 좌표
        int blink2 = blink1; //blink1남은거만큼 blink2,
        if(blink1 == 1) {//빈공간을 만나지않은경우 없었음을기록
            blink1 = 0;
        }
        check = false;
        while(true){
            //좌표끝도달
            if(xx == 16) {
                break;
            }
            if(checkValidMove(takes,coord.get("y").asInt(),xx,0)) {
                check = false;
                stone2++;
            }
            //상대돌을 만나면 탐색중지
            if(checkValidMove(takes,coord.get("y").asInt(),xx,1)) {
                break;
            }
            if(!checkValidMove(takes,coord.get("y").asInt(),xx,null)) {
                //두번연속으로 빈공간만날시 blink카운트를 되돌림.
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break; //빈공간을만났으나 빈공간을 두번만나면 끝임
                }
            }
            xx++;
        }

        allStone = stone1 + stone2;
        //삼삼이므로 돌갯수가 2 + 1(현재돌)이아니면 0리턴
        //이부분이 43을 허용하게해줌. 33만 찾게됨
        if(allStone != 2) {
            return 0;
        }
        //돌갯수가 3이면 열린 3인지 파악.

        int left = (stone1 + blink1);
        int right = (stone2 + blink2);

        //벽으로 막힌경우 - 열린3이 아님
        if((coord.get("x").asInt() - left) == 0 || (coord.get("x").asInt() + right) == 15) {
            return 0;
        }else {//상대돌로 막힌경우 - 열린3이 아님
            if (takes.stream().anyMatch(t ->  t.getY() == coord.get("y").asInt() &&
                        t.getX() == (coord.get("x").asInt() - left - 1) && takes.indexOf(t) % 2 == 1) ||
                    takes.stream().anyMatch(t -> t.getY() == coord.get("y").asInt() &&
                        t.getX() == (coord.get("x").asInt() + right + 1) && takes.indexOf(t) % 2 == 1)) {
                return 0;
            } else {
                return 1; //열린3 일때 1 리턴
            }
        }
    }
    // ↖ ↘ 탐색
    private int validateDoubleThrees2(List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;

        // ↖
        int xx = coord.get("x").asInt()-1;
        int yy = coord.get("y").asInt()-1;
        boolean check = false;
        while(true){
            if(xx == -1 || yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)){
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                } else {
                    break;
                }
            }
            xx--;
            yy--;
        }

        // ↘
        int blink2 = blink1;
        blink1 = blink1 == 1 ? 0 : blink1;
        xx = coord.get("x").asInt()+1;
        yy = coord.get("y").asInt()+1;
        check = false;
        while(true) {
            if(xx == 16 || yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break;
                }
            }
            xx++;
            yy++;
        }

        allStone = stone1 + stone2;
        if(allStone != 2) {
            return 0;
        }

        int leftUp = (stone1 + blink1);
        int rightDown = (stone2 + blink2);

        if((coord.get("y").asInt() - leftUp) == 0 || (coord.get("x").asInt() - leftUp) == 0
                || (coord.get("y").asInt() + rightDown) == 15 || (coord.get("x").asInt() + rightDown) == 15) {
            return 0;
        }else if(takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() - leftUp -1)
                && take.getX() == (coord.get("x").asInt() - leftUp - 1) && takes.indexOf(take) % 2 == 1)
                || takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() + rightDown + 1)
                && take.getX() == (coord.get("x").asInt() + rightDown + 1) && takes.indexOf(take) % 2 == 1)){
            return 0;
        } else {
            return 1;
        }
    }
    // ↑ ↓ 탐색
    private int validateDoubleThrees3(List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;

        // ↑
        int yy = coord.get("y").asInt()-1;
        boolean check = false;
        while(true) {
            if(yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,coord.get("x").asInt(),0)) {
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,coord.get("x").asInt(),1)) {
                break;
            }

            if(!checkValidMove(takes,yy,coord.get("x").asInt(),null)) {
                if(check == false) {
                    check = true;
                } else {
                    blink1++;
                    break;
                }

                if(blink1 == 1) {
                    blink1--;
                }else {
                    break;
                }
            }
            yy--;
        }

        // ↓
        int blink2 = blink1;
        blink1 = blink1 == 1 ? 0 : blink1;
        yy = coord.get("y").asInt() + 1;
        check = false;
        while(true) {
            if(yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,coord.get("x").asInt(),0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,yy,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,coord.get("x").asInt(),null)) {
                if(check == false) {
                    check = true;
                } else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                } else {
                    break;
                }
            }
            yy++;
        }

        allStone = stone1 + stone2;
        if(allStone != 2) {
            return 0;
        }

        int up = (stone1 + blink1);
        int down = (stone2 + blink2);

        if((coord.get("y").asInt() - up) == 0 || (coord.get("y").asInt() + down) == 15) {
            return 0;
        } else {
            if(takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() - up - 1)
                    && take.getX() == coord.get("x").asInt() && takes.indexOf(take) % 2 == 1)
                    || takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() + down + 1)
                    && take.getX() == coord.get("x").asInt() && takes.indexOf(take) % 2 == 1)){
                return 0;
            } else {
                return 1;
            }
        }
    }
    // ／ 탐색
    // ↙ ↗ 탐색
    private int validateDoubleThrees4(List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;

        // ↙
        int xx = coord.get("x").asInt()-1;
        int yy = coord.get("y").asInt()+1;
        boolean check = false;
        while(true) {
            if(xx == -1 || yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                } else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                }else {
                    break;
                }
            }
            xx--;
            yy++;
        }

        // ↗
        int blink2 = blink1;
        blink1 = blink1 == 1 ? 0 : blink1;
        xx = coord.get("x").asInt() + 1;
        yy = coord.get("y").asInt() - 1;
        check = false;
        while(true) {
            if(xx == 16 || yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                } else {
                    blink2++;
                    break;
                }

                if(blink2 == 1) {
                    blink2--;
                } else {
                    break;
                }
            }
            xx++;
            yy--;
        }

        allStone = stone1 + stone2;
        if (allStone != 2) {
            return 0;
        }

        int leftDown = (stone1 + blink1);
        int rightUp = (stone2 + blink2);

        if(coord.get("x").asInt() - leftDown == 0 || coord.get("y").asInt() - rightUp == 0
                || coord.get("y").asInt() + leftDown == 15 || coord.get("x").asInt() + rightUp == 15) {
            return 0;
        }else {
            if(takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() + leftDown + 1)
                    && take.getX() == (coord.get("x").asInt() - leftDown - 1) && takes.indexOf(take) % 2 == 1)
                    || takes.stream().anyMatch(take -> take.getY() == (coord.get("y").asInt() - rightUp - 1)
                    && take.getX() == (coord.get("x").asInt() + rightUp + 1) && takes.indexOf(take) % 2 == 1)){
                return 0;
            } else {
                return 1;
            }
        }
    }

    private boolean checkValidMove(List<Takes> takes, int y, int x, Integer type){
        return takes.stream().anyMatch(take -> take.getX() == x
                && take.getY() == y
                && (type == null || takes.indexOf(take) % 2 == type));
    }

    /********************************************************************************************************************/


    /***********************************************************44*******************************************************/
    //열리는건 문제 x 그냥 4의 갯수를 담는변수가 2개이상이면 44
    //똑같이 빈공간은 하나만 허용
    // 4가지 부분으로 로나누어 풀수있음
    private boolean omok44Rule(List<Takes> takes, JsonNode coord) {
        int count = 0;
        count += validateDoubleFours1(1,takes,coord);
        count += validateDoubleFours2(1,takes,coord);
        count += validateDoubleFours3(1,takes,coord);
        count += validateDoubleFours4(1,takes,coord);

        if(count >= 2) {
            return true;
        } else {
            return false;
        }
    }


    // ← → 탐색
    private int validateDoubleFours1(int trigger,List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        //열린4인지는 상관은없음. 다만 코드상 빈공간만을 의미.
        int blink1 = 1;
        blink1 = trigger == 3 ? 0 : blink1; // 5목달성조건은 빈공간없이 5개가 이어져야함.

        // ←  탐색
        int yy = coord.get("y").asInt();
        int xx = coord.get("x").asInt() - 1;
        boolean check = false;
        while(true) {
            if(xx == -1) {
                break;
            }

            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone1++;
            }

            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }

            if(!checkValidMove(takes,yy,xx,null)) {
                //두번연속으로 빈공간만날시 blink카운트를 되돌림.
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                }else {
                    break; //빈공간을만났으나 빈공간을 두번만나면 끝임
                }
            }
            xx--;
        }

        // → 탐색
        xx = coord.get("x").asInt() + 1;
        yy = coord.get("y").asInt();
        int blink2 = blink1;
        check = false;
        while(true) {
            if(xx == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break;
                }
            }
            xx++;
        }

        allStone = stone1 + stone2;

        //사사찾는 트리거
        if (trigger == 1) {
            if (allStone != 3) {
                return 0; //놓은돌제외 3개아니면 4가아니니까.
            } else {
                return 1; //놓은돌제외 3개면 4임. 닫히고 열린지는 상관없음.
            }
        }

        //장목찾는 트리거
        if (trigger == 2) {
            //현재놓은돌 +1 +5 => 6목이상은 장목. 여기서 놓은돌기준 두방향모두 돌이있어야 장목
            if(allStone >= 5 && stone1 != 0 && stone2 != 0) {
                return 1;
            } else {
                return 0;
            }
        }

        if(trigger == 3) {
            //놓은돌포함 5개의돌이완성되면.
            if(allStone == 4) {
                return 1;
            } else {
                return 0;
            }
        }
        //그럴일을없지만 1 도 2도아니면 0리턴
        return 0;
    }

    // ↖ ↘ 탐색
    private int validateDoubleFours2(int trigger,List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;
        blink1 = trigger == 3 ? 0 : blink1;

        // ↖  탐색
        int yy = coord.get("y").asInt() - 1;
        int xx = coord.get("x").asInt() - 1;
        boolean check = false;
        while(true) {
            if(xx == -1 || yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }

                if(blink1 == 1) {
                    blink1--;
                }else {
                    break;
                }
            }
            xx--;
            yy--;
        }

        // ↘  탐색
        yy = coord.get("y").asInt() + 1;
        xx = coord.get("x").asInt() + 1;
        check = false;
        int blink2 = blink1;
        while(true) {
            if(xx == 16 || yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break;
                }
            }
            xx++;
            yy++;
        }

        allStone = stone1 + stone2;

        if (trigger == 1) {
            if (allStone != 3) {
                return 0;
            } else {
                return 1;
            }
        }

        if (trigger == 2) {
            if(allStone >= 5 && stone1 != 0 && stone2 != 0) {
                return 1;
            } else {
                return 0;
            }
        }

        if(trigger == 3) {
            if(allStone == 4) {
                return 1;
            } else {
                return 0;
            }
        }
        return 0;
    }
    // ↑ ↓ 탐색
    private int validateDoubleFours3(int trigger,List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;
        blink1 = trigger == 3 ? 0 : blink1;

        // ↑  탐색
        int yy = coord.get("y").asInt() - 1;
        int xx = coord.get("x").asInt();
        boolean check = false;
        while(true) {
            if(yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                }else {
                    break;
                }
            }
            yy--;
        }

        // ↓  탐색
        yy = coord.get("y").asInt() + 1;
        xx = coord.get("x").asInt();
        check = false;
        int blink2 = blink1;
        while(true) {
            if(yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break;
                }
            }
            yy++;
        }

        allStone = stone1 + stone2;

        if (trigger == 1) {
            if (allStone != 3) {
                return 0;
            } else {
                return 1;
            }
        }

        if (trigger == 2) {
            if(allStone >= 5 && stone1 != 0 && stone2 != 0) {
                return 1;
            } else {
                return 0;
            }
        }
        if(trigger == 3) {
            if(allStone == 4) {
                return 1;
            } else {
                return 0;
            }
        }

        return 0;
    }

    // ↗ ↙ 탐색
    private int validateDoubleFours4(int trigger ,List<Takes> takes, JsonNode coord) {
        int stone1 = 0;
        int stone2 = 0;
        int allStone = 0;
        int blink1 = 1;
        blink1 = trigger == 3 ? 0 : blink1;

        // ↗ 탐색
        int yy = coord.get("y").asInt() - 1;
        int xx = coord.get("x").asInt() + 1;
        boolean check = false;
        while(true) {
            if(xx == 16 || yy == -1) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone1++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink1++;
                    break;
                }
                if(blink1 == 1) {
                    blink1--;
                }else {
                    break;
                }
            }
            xx++;
            yy--;
        }

        // ↙ 탐색
        yy = coord.get("y").asInt() + 1;
        xx = coord.get("x").asInt() - 1;
        check = false;
        int blink2 = blink1;
        while(true) {
            if(xx == -1 || yy == 16) {
                break;
            }
            if(checkValidMove(takes,yy,xx,0)) {
                check = false;
                stone2++;
            }
            if(checkValidMove(takes,yy,xx,1)) {
                break;
            }
            if(!checkValidMove(takes,yy,xx,null)) {
                if(check == false) {
                    check = true;
                }else {
                    blink2++;
                    break;
                }
                if(blink2 == 1) {
                    blink2--;
                }else {
                    break;
                }
            }
            xx--;
            yy++;
        }

        allStone = stone1 + stone2;

        if (trigger == 1) {
            if (allStone != 3) {
                return 0;
            } else {
                return 1;
            }
        }
        if (trigger == 2) {
            if(allStone >= 5 && stone1 != 0 && stone2 != 0) {
                return 1;
            } else {
                return 0;
            }
        }
        if(trigger == 3) {
            if(allStone == 4) {
                return 1;
            } else {
                return 0;
            }
        }
        return 0;
    }
    /********************************************************************************************************************/


    /***********************************************************장목*******************************************************/
    private boolean omokJangmokRule(List<Takes> takes, JsonNode coord) {
        int result = 0;
        result += validateDoubleFours1(2,takes,coord);
        result += validateDoubleFours2(2,takes,coord);
        result += validateDoubleFours3(2,takes,coord);
        result += validateDoubleFours4(2,takes,coord);

        if(result >= 1) {//하나라도 장목수가있으면
            return true;
        }
        return false;
    }
    /********************************************************************************************************************/
}








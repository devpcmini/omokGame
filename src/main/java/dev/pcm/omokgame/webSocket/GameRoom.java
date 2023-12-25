package dev.pcm.omokgame.webSocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;

public class GameRoom {

    private String roomName;
    private Set<PlayerInfo> players;

    public GameRoom(String roomName) {
        this.roomName = roomName;
        this.players = new HashSet<>();
    }

    public String getRoomName() {
        return roomName;
    }
    public Set<PlayerInfo> getPlayers() {
        return players;
    }

    public void addPlayer(WebSocketSession session, String nickname, String stoneColor) {
        PlayerInfo playerInfo = new PlayerInfo(session, nickname, stoneColor);
        players.add(playerInfo);
        session.getAttributes().put("playerInfo", playerInfo);
    }

    public void removePlayer(WebSocketSession session) {
        PlayerInfo playerInfo = (PlayerInfo) session.getAttributes().get("playerInfo");
        if (playerInfo != null) {
            players.remove(playerInfo);
        }
    }

    // 방 정보를 얻어오는 메서드
    public RoomInfo getRoomInfo() {
        Set<String> nicknames = new HashSet<>();
        for (PlayerInfo player : players) {
            nicknames.add(player.getNickname());
        }
        return new RoomInfo(this.roomName, this.players.size(), nicknames);
    }

    // 내부 클래스로 RoomInfo 추가
    public static class RoomInfo {
        private String roomName;
        private int playerCount;
        private Set<String> nicknames;

        public RoomInfo(String roomName, int playerCount, Set<String> nicknames) {
            this.roomName = roomName;
            this.playerCount = playerCount;
            this.nicknames = nicknames;
        }

        public String getRoomName() {
            return roomName;
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public Set<String> getNicknames() {
            return nicknames;
        }
    }

    // 내부 클래스로 PlayerInfo 추가
    public static class PlayerInfo {
        private WebSocketSession session;
        private String nickname;
        private String stoneColor;

        public PlayerInfo(WebSocketSession session, String nickname, String stoneColor) {
            this.session = session;
            this.nickname = nickname;
            this.stoneColor = stoneColor;
        }

        public WebSocketSession getSession() {
            return session;
        }

        public String getNickname() {
            return nickname;
        }
        public String getStoneColor() {
            return stoneColor;
        }
    }
}


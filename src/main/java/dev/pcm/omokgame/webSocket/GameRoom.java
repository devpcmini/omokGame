package dev.pcm.omokgame.webSocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;

public class GameRoom {

    private String roomName;
    private Set<WebSocketSession> players;

    public GameRoom(String roomName) {
        this.roomName = roomName;
        this.players = new HashSet<>();
    }

    public String getRoomName() {
        return roomName;
    }

    public Set<WebSocketSession> getPlayers() {
        return players;
    }

    public void addPlayer(WebSocketSession player) {
        players.add(player);
    }

    public void removePlayer(WebSocketSession player) {
        players.remove(player);
    }

    // 추가: 방 정보를 얻어오는 메서드
    public RoomInfo getRoomInfo() {
        return new RoomInfo(this.roomName, this.players.size());
    }

    // 내부 클래스로 RoomInfo 추가
    public static class RoomInfo {
        private String roomName;
        private int playerCount;

        public RoomInfo(String roomName, int playerCount) {
            this.roomName = roomName;
            this.playerCount = playerCount;
        }

        public String getRoomName() {
            return roomName;
        }

        public int getPlayerCount() {
            return playerCount;
        }
    }
}


package com.eos.lss.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import com.eos.lss.dto.PlayerDto;
import com.fasterxml.jackson.databind.ObjectMapper;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private String id;
    private String entryCode;
    private String presidentId;
    private LocalDateTime createdAt;
    private String gameState; // WAITING_ROOM, ORDER_REGISTER, GAME_PLAYING, LEE_SOON_SIN
    private String playersJson; // 참여한 플레이어 (순서 무관)
    private String orderedPlayersJson; // 순서 등록된 플레이어 (순서 엄격히 관리)
    
    // 편의 메서드: JSON <-> List 변환
    public List<PlayerDto> getPlayers() {
        if (playersJson == null || playersJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(playersJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, PlayerDto.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void setPlayers(List<PlayerDto> players) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.playersJson = mapper.writeValueAsString(players);
        } catch (Exception e) {
            this.playersJson = "[]";
        }
    }
    
    public List<PlayerDto> getOrderedPlayers() {
        if (orderedPlayersJson == null || orderedPlayersJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(orderedPlayersJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, PlayerDto.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void setOrderedPlayers(List<PlayerDto> orderedPlayers) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.orderedPlayersJson = mapper.writeValueAsString(orderedPlayers);
        } catch (Exception e) {
            this.orderedPlayersJson = "[]";
        }
    }
    
    private int currentPlayerIndex = 0;
    private boolean isClockWise = true;
    private CoinState firstCoinState;
    private CoinState secondCoinState;
    private LocalDateTime gameEndTime; // 게임 마감 시간 (순서 등록 후 10분)
    private Boolean isLeeSoonSinByTimeExpired; // 이순신 상태가 시간 초과로 인한 것인지 구분
} 
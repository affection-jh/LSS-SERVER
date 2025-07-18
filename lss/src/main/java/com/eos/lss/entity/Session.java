package com.eos.lss.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import com.eos.lss.entity.Player;
import com.fasterxml.jackson.databind.ObjectMapper;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @Id
    private String id;
    
    @Column(nullable = false, unique = true)
    private String entryCode;
    
    @Column(nullable = false)
    private String presidentId;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    
    @Column(columnDefinition = "JSON", nullable = false)
    private String playersJson; // 참여한 플레이어 (순서 무관)
    
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "session_ordered_players", 
               joinColumns = @JoinColumn(name = "session_id"),
               inverseJoinColumns = @JoinColumn(name = "player_user_id"))
    @OrderColumn(name = "player_order") // 순서 엄격히 관리
    private List<Player> orderedPlayers;
    
    // 편의 메서드: JSON <-> List 변환
    public List<Player> getPlayers() {
        if (playersJson == null || playersJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(playersJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, Player.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void setPlayers(List<Player> players) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.playersJson = mapper.writeValueAsString(players);
        } catch (Exception e) {
            this.playersJson = "[]";
        }
    }
    

    
    @Column(nullable = false)
    private int currentPlayerIndex = 0;
    
    @Column(nullable = false)
    private boolean isClockWise = true;
    
    @Enumerated(EnumType.STRING)
    private CoinState firstCoinState;
    
    @Enumerated(EnumType.STRING)
    private CoinState secondCoinState;
    
    @Column
    private LocalDateTime gameEndTime; // 게임 마감 시간 (순서 등록 후 10분)
    
    @Column
    private Boolean isLeeSoonSinByTimeExpired; // 이순신 상태가 시간 초과로 인한 것인지 구분
} 
package com.eos.lss.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    @Id
    private String userId;
    
    @Column(nullable = false)
    private String name;
    
    @Column
    private String profileImageUrl; // 프로필 이미지 URL (없으면 null)
} 
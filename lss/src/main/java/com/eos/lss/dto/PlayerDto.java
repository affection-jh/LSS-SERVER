package com.eos.lss.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDto {
    private String userId;
    private String name;
    private String profileImageUrl; // 프로필 이미지 URL (없으면 null)
} 
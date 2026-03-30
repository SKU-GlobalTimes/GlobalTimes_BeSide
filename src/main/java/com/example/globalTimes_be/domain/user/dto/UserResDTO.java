package com.example.globalTimes_be.domain.user.dto;

import com.example.globalTimes_be.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResDTO {

    private Long userId;
    private String email;
    private String nickname;
    private String provider;

    public static UserResDTO from(User user) {
        return UserResDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .build();
    }
}

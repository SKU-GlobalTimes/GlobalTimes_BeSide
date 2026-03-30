package com.example.globalTimes_be.global.security.oauth2;

import com.example.globalTimes_be.domain.user.entity.User;
import com.example.globalTimes_be.domain.user.repository.UserRepository;
import com.example.globalTimes_be.global.security.oauth2.userinfo.GoogleOAuth2UserInfo;
import com.example.globalTimes_be.global.security.oauth2.userinfo.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId(); // "google"
        OAuth2UserInfo userInfo = resolveUserInfo(provider, oAuth2User.getAttributes());

        User user = saveOrUpdate(provider, userInfo);

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("userId", user.getId());
        attributes.put("email", user.getEmail());

        return new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "sub"
        );
    }

    private OAuth2UserInfo resolveUserInfo(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 provider: " + provider);
        };
    }

    private User saveOrUpdate(String provider, OAuth2UserInfo userInfo) {
        return userRepository.findByProviderAndProviderId(provider, userInfo.getProviderId())
                .map(existing -> {
                    existing.updateNickname(userInfo.getNickname());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        User.create(userInfo.getEmail(), userInfo.getNickname(), provider, userInfo.getProviderId())
                ));
    }
}

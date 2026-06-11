package com.example.system.dto;

import com.example.system.model.UserAccount;

public record AuthUserResponse(
        String id,
        String username,
        String displayName,
        String bio,
        String avatarDataUrl,
        String language,
        String fontSize) {

    public static AuthUserResponse from(UserAccount user) {
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername()
                : user.getDisplayName();
        String bio = user.getBio() == null ? "" : user.getBio();
        String avatarDataUrl = user.getAvatarDataUrl() == null ? "" : user.getAvatarDataUrl();
        String language = user.getLanguage() == null || user.getLanguage().isBlank() ? "zh-CN" : user.getLanguage();
        String fontSize = user.getFontSize() == null || user.getFontSize().isBlank() ? "standard" : user.getFontSize();
        return new AuthUserResponse(user.getId(), user.getUsername(), displayName, bio, avatarDataUrl, language, fontSize);
    }
}

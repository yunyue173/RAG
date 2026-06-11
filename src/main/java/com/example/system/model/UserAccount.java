package com.example.system.model;

import java.time.Instant;

public class UserAccount {

    private String id;
    private String username;
    private String displayName;
    private String bio;
    private String avatarDataUrl;
    private String passwordHash;
    private String salt;
    private String language;
    private String fontSize;
    private Instant createdAt;
    private Instant lastLoginAt;

    public UserAccount() {
    }

    public UserAccount(String id, String username, String displayName, String bio, String avatarDataUrl,
            String passwordHash, String salt, String language, String fontSize, Instant createdAt, Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.avatarDataUrl = avatarDataUrl;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.language = language;
        this.fontSize = fontSize;
        this.createdAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarDataUrl() {
        return avatarDataUrl;
    }

    public void setAvatarDataUrl(String avatarDataUrl) {
        this.avatarDataUrl = avatarDataUrl;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}

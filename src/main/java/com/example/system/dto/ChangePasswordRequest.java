package com.example.system.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}

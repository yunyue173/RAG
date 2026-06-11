package com.example.system.service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.system.dto.AuthUserResponse;
import com.example.system.dto.ChangePasswordRequest;
import com.example.system.dto.PreferenceRequest;
import com.example.system.dto.UpdateProfileRequest;
import com.example.system.model.UserAccount;
import com.example.system.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

    public static final String SESSION_USER_ID = "LOGIN_USER_ID";
    public static final String SESSION_USERNAME = "LOGIN_USERNAME";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{3,20}$");
    private static final int MAX_AVATAR_DATA_URL_LENGTH = 500_000;

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public AuthService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public AuthUserResponse register(String username, String password, HttpSession session) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername);
        validatePassword(password);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String salt = passwordService.newSalt();
        UserAccount user = new UserAccount(
                UUID.randomUUID().toString(),
                normalizedUsername,
                normalizedUsername,
                "",
                "",
                passwordService.hash(password, salt),
                salt,
                "zh-CN",
                "standard",
                Instant.now(),
                Instant.now());
        userRepository.save(user);
        loginSession(session, user);
        return AuthUserResponse.from(user);
    }

    public AuthUserResponse login(String username, String password, HttpSession session) {
        String normalizedUsername = normalizeUsername(username);
        UserAccount user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordService.matches(password == null ? "" : password, user.getSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        userRepository.markLogin(user);
        loginSession(session, user);
        return AuthUserResponse.from(user);
    }

    public AuthUserResponse currentUser(HttpSession session) {
        UserAccount user = currentAccount(session);
        return user == null ? null : AuthUserResponse.from(user);
    }

    public AuthUserResponse updateProfile(UpdateProfileRequest request, HttpSession session) {
        UserAccount user = requireCurrentAccount(session);
        String displayName = request == null ? "" : normalizeText(request.displayName());
        String bio = request == null ? "" : normalizeText(request.bio());
        String avatarDataUrl = request == null ? null : normalizeText(request.avatarDataUrl());
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (displayName.length() > 20) {
            throw new IllegalArgumentException("昵称最多 20 个字符");
        }
        if (bio.length() > 80) {
            throw new IllegalArgumentException("简介最多 80 个字符");
        }
        user.setDisplayName(displayName);
        user.setBio(bio);
        updateAvatar(user, avatarDataUrl);
        userRepository.save(user);
        return AuthUserResponse.from(user);
    }

    public AuthUserResponse updatePreferences(PreferenceRequest request, HttpSession session) {
        UserAccount user = requireCurrentAccount(session);
        String language = request == null ? "zh-CN" : normalizeText(request.language());
        String fontSize = request == null ? "standard" : normalizeText(request.fontSize());
        if (!language.equals("zh-CN") && !language.equals("en-US")) {
            throw new IllegalArgumentException("不支持的语言设置");
        }
        if (!fontSize.equals("small") && !fontSize.equals("standard")
                && !fontSize.equals("large") && !fontSize.equals("x-large")) {
            throw new IllegalArgumentException("不支持的文字大小设置");
        }
        user.setLanguage(language);
        user.setFontSize(fontSize);
        userRepository.save(user);
        return AuthUserResponse.from(user);
    }

    public void changePassword(ChangePasswordRequest request, HttpSession session) {
        UserAccount user = requireCurrentAccount(session);
        String currentPassword = request == null ? "" : request.currentPassword();
        String newPassword = request == null ? "" : request.newPassword();
        if (!passwordService.matches(currentPassword == null ? "" : currentPassword, user.getSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        validatePassword(newPassword);
        String salt = passwordService.newSalt();
        user.setSalt(salt);
        user.setPasswordHash(passwordService.hash(newPassword, salt));
        userRepository.save(user);
    }

    public UserAccount currentAccount(HttpSession session) {
        if (session == null) {
            return null;
        }
        String userId = (String) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private void loginSession(HttpSession session, UserAccount user) {
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_USERNAME, user.getUsername());
    }

    private UserAccount requireCurrentAccount(HttpSession session) {
        UserAccount user = currentAccount(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return user;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void updateAvatar(UserAccount user, String avatarDataUrl) {
        if (avatarDataUrl == null) {
            return;
        }
        if (avatarDataUrl.isBlank()) {
            user.setAvatarDataUrl("");
            return;
        }
        validateAvatarDataUrl(avatarDataUrl);
        user.setAvatarDataUrl(avatarDataUrl);
    }

    private void validateAvatarDataUrl(String avatarDataUrl) {
        if (avatarDataUrl.length() > MAX_AVATAR_DATA_URL_LENGTH) {
            throw new IllegalArgumentException("头像图片过大，请选择较小的图片");
        }
        String lower = avatarDataUrl.toLowerCase();
        boolean supported = lower.startsWith("data:image/png;base64,")
                || lower.startsWith("data:image/jpeg;base64,")
                || lower.startsWith("data:image/jpg;base64,")
                || lower.startsWith("data:image/webp;base64,");
        if (!supported) {
            throw new IllegalArgumentException("头像只支持 PNG、JPG 或 WEBP 图片");
        }
    }

    private void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名需为 3-20 位中文、字母、数字或下划线");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new IllegalArgumentException("密码需为 6-32 位");
        }
    }
}

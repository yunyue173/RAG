package com.example.system.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.system.config.RagProperties;
import com.example.system.model.UserAccount;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.annotation.PostConstruct;

@Repository
public class UserRepository {

    private final RagProperties properties;
    private final ObjectMapper mapper;
    private final Map<String, UserAccount> usersByName = new LinkedHashMap<>();

    public UserRepository(RagProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder().findAndAddModules().build();
    }

    @PostConstruct
    public void load() {
        try {
            Files.createDirectories(properties.storagePath());
            Path path = file();
            if (Files.exists(path)) {
                List<UserAccount> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {
                });
                loaded.forEach(user -> usersByName.put(normalize(user.getUsername()), user));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化用户存储", ex);
        }
    }

    public synchronized Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(usersByName.get(normalize(username)));
    }

    public synchronized Optional<UserAccount> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return usersByName.values().stream()
                .filter(user -> id.equals(user.getId()))
                .findFirst();
    }

    public synchronized boolean existsByUsername(String username) {
        return usersByName.containsKey(normalize(username));
    }

    public synchronized UserAccount save(UserAccount user) {
        usersByName.put(normalize(user.getUsername()), user);
        persist();
        return user;
    }

    public synchronized void markLogin(UserAccount user) {
        UserAccount stored = usersByName.get(normalize(user.getUsername()));
        if (stored != null) {
            stored.setLastLoginAt(Instant.now());
            persist();
        }
    }

    private String normalize(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT).trim();
    }

    private Path file() {
        return properties.storagePath().resolve("users.json");
    }

    private void persist() {
        try {
            Files.createDirectories(file().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file().toFile(), usersByName.values().stream().toList());
        } catch (IOException ex) {
            throw new IllegalStateException("写入用户存储失败", ex);
        }
    }
}

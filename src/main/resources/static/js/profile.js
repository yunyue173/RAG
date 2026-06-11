const profileState = {
    user: null,
    language: "zh-CN",
    fontSize: "standard",
    avatarDataUrl: "",
};

const profileEls = {
    backBtn: document.querySelector("#backBtn"),
    avatar: document.querySelector("#profilePageAvatar"),
    avatarInput: document.querySelector("#avatarInput"),
    clearAvatarBtn: document.querySelector("#clearAvatarBtn"),
    displayNameText: document.querySelector("#profileDisplayName"),
    profileForm: document.querySelector("#profileForm"),
    displayNameInput: document.querySelector("#displayNameInput"),
    bioInput: document.querySelector("#bioInput"),
    profileMessage: document.querySelector("#profileMessage"),
    passwordForm: document.querySelector("#passwordForm"),
    currentPasswordInput: document.querySelector("#currentPasswordInput"),
    newPasswordInput: document.querySelector("#newPasswordInput"),
    passwordMessage: document.querySelector("#passwordMessage"),
    languageLabel: document.querySelector("#languageLabel"),
    fontSizeLabel: document.querySelector("#fontSizeLabel"),
    preferenceMessage: document.querySelector("#preferenceMessage"),
    logoutBtn: document.querySelector("#logoutBtn"),
};

const i18n = {
    "zh-CN": {
        profileTitle: "个人中心",
        profileInfo: "个人资料",
        accountSecurity: "账号安全",
        preferences: "偏好设置",
        displayName: "昵称",
        bio: "简介",
        saveProfile: "保存资料",
        currentPassword: "当前密码",
        newPassword: "新密码",
        changePassword: "修改密码",
        language: "语言切换",
        fontSize: "文字大小",
        small: "小",
        standard: "标准",
        large: "大",
        xLarge: "超大",
        preview: "预览：文档问答结果和历史消息会按照当前文字大小显示。",
        clearAvatar: "移除头像",
        avatarSelected: "头像已选择，点击保存资料后生效",
        avatarCleared: "头像已移除，点击保存资料后生效",
        avatarTypeError: "请选择 PNG、JPG 或 WEBP 图片",
        avatarSizeError: "头像图片过大，请选择 5MB 以内的图片",
        logout: "退出登录",
        zh: "简体中文",
        en: "English",
        saved: "已保存",
        passwordChanged: "密码已修改",
    },
    "en-US": {
        profileTitle: "Profile",
        profileInfo: "Personal Info",
        accountSecurity: "Account Security",
        preferences: "Preferences",
        displayName: "Display name",
        bio: "Bio",
        saveProfile: "Save Profile",
        currentPassword: "Current password",
        newPassword: "New password",
        changePassword: "Change Password",
        language: "Language",
        fontSize: "Text Size",
        small: "Small",
        standard: "Standard",
        large: "Large",
        xLarge: "Extra Large",
        preview: "Preview: document answers and chat history will use the selected text size.",
        clearAvatar: "Remove avatar",
        avatarSelected: "Avatar selected. Save your profile to apply it.",
        avatarCleared: "Avatar removed. Save your profile to apply it.",
        avatarTypeError: "Please choose a PNG, JPG, or WEBP image.",
        avatarSizeError: "Avatar image is too large. Please choose an image under 5MB.",
        logout: "Log Out",
        zh: "简体中文",
        en: "English",
        saved: "Saved",
        passwordChanged: "Password changed",
    },
};

document.addEventListener("DOMContentLoaded", initProfile);

async function initProfile() {
    bindProfileEvents();
    await loadProfile();
}

function bindProfileEvents() {
    profileEls.backBtn.addEventListener("click", () => {
        window.location.href = "/";
    });
    profileEls.avatar.addEventListener("click", () => profileEls.avatarInput.click());
    profileEls.avatarInput.addEventListener("change", handleAvatarChange);
    profileEls.clearAvatarBtn.addEventListener("click", clearAvatar);
    profileEls.profileForm.addEventListener("submit", saveProfile);
    profileEls.passwordForm.addEventListener("submit", changePassword);
    document.querySelectorAll("[data-language]").forEach(button => {
        button.addEventListener("click", () => savePreferences(button.dataset.language, profileState.fontSize));
    });
    document.querySelectorAll("[data-font-size]").forEach(button => {
        button.addEventListener("click", () => savePreferences(profileState.language, button.dataset.fontSize));
    });
    profileEls.logoutBtn.addEventListener("click", logout);
}

async function loadProfile() {
    try {
        const user = await apiGet("/api/auth/me");
        if (!user?.username) {
            window.location.href = "/login";
            return;
        }
        applyUser(user);
    } catch (error) {
        window.location.href = "/login";
    }
}

function applyUser(user) {
    profileState.user = user;
    profileState.language = user.language || "zh-CN";
    profileState.fontSize = user.fontSize || "standard";
    profileState.avatarDataUrl = user.avatarDataUrl || "";
    const displayName = user.displayName || user.username;
    profileEls.displayNameText.textContent = displayName;
    profileEls.displayNameInput.value = displayName;
    profileEls.bioInput.value = user.bio || "";
    renderAvatar(displayName);
    applyLanguage(profileState.language);
    applyFontSize(profileState.fontSize);
    renderChoices();
}

async function saveProfile(event) {
    event.preventDefault();
    setMessage(profileEls.profileMessage, "保存中...");
    try {
        const user = await apiPut("/api/auth/profile", {
            displayName: profileEls.displayNameInput.value.trim(),
            bio: profileEls.bioInput.value.trim(),
            avatarDataUrl: profileState.avatarDataUrl,
        });
        applyUser(user);
        setMessage(profileEls.profileMessage, t("saved"));
    } catch (error) {
        setMessage(profileEls.profileMessage, error.message, true);
    }
}

async function handleAvatarChange(event) {
    const file = event.target.files[0];
    event.target.value = "";
    if (!file) {
        return;
    }
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
        setMessage(profileEls.profileMessage, t("avatarTypeError"), true);
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        setMessage(profileEls.profileMessage, t("avatarSizeError"), true);
        return;
    }
    try {
        profileState.avatarDataUrl = await resizeAvatar(file);
        renderAvatar(profileEls.displayNameInput.value.trim() || profileState.user?.username || "用");
        setMessage(profileEls.profileMessage, t("avatarSelected"));
    } catch (error) {
        setMessage(profileEls.profileMessage, error.message, true);
    }
}

function clearAvatar() {
    profileState.avatarDataUrl = "";
    renderAvatar(profileEls.displayNameInput.value.trim() || profileState.user?.username || "用");
    setMessage(profileEls.profileMessage, t("avatarCleared"));
}

function resizeAvatar(file) {
    return new Promise((resolve, reject) => {
        const image = new Image();
        const url = URL.createObjectURL(file);
        image.onload = () => {
            const size = 192;
            const canvas = document.createElement("canvas");
            canvas.width = size;
            canvas.height = size;
            const ctx = canvas.getContext("2d");
            const sourceSize = Math.min(image.width, image.height);
            const sx = (image.width - sourceSize) / 2;
            const sy = (image.height - sourceSize) / 2;
            ctx.drawImage(image, sx, sy, sourceSize, sourceSize, 0, 0, size, size);
            URL.revokeObjectURL(url);
            resolve(canvas.toDataURL("image/jpeg", 0.88));
        };
        image.onerror = () => {
            URL.revokeObjectURL(url);
            reject(new Error(t("avatarTypeError")));
        };
        image.src = url;
    });
}

function renderAvatar(displayName) {
    profileEls.avatar.textContent = "";
    profileEls.avatar.classList.toggle("has-image", Boolean(profileState.avatarDataUrl));
    profileEls.clearAvatarBtn.hidden = !profileState.avatarDataUrl;
    if (profileState.avatarDataUrl) {
        const image = document.createElement("img");
        image.src = profileState.avatarDataUrl;
        image.alt = "头像";
        profileEls.avatar.append(image);
        return;
    }
    profileEls.avatar.textContent = (displayName || "用").slice(0, 1).toUpperCase();
}

async function changePassword(event) {
    event.preventDefault();
    setMessage(profileEls.passwordMessage, "处理中...");
    try {
        await apiPut("/api/auth/password", {
            currentPassword: profileEls.currentPasswordInput.value,
            newPassword: profileEls.newPasswordInput.value,
        });
        profileEls.currentPasswordInput.value = "";
        profileEls.newPasswordInput.value = "";
        setMessage(profileEls.passwordMessage, t("passwordChanged"));
    } catch (error) {
        setMessage(profileEls.passwordMessage, error.message, true);
    }
}

async function savePreferences(language, fontSize) {
    try {
        const user = await apiPut("/api/auth/preferences", { language, fontSize });
        applyUser(user);
        setMessage(profileEls.preferenceMessage, t("saved"));
    } catch (error) {
        setMessage(profileEls.preferenceMessage, error.message, true);
    }
}

async function logout() {
    profileEls.logoutBtn.disabled = true;
    try {
        await apiPost("/api/auth/logout");
    } finally {
        window.location.href = "/login";
    }
}

function applyLanguage(language) {
    document.documentElement.lang = language === "en-US" ? "en" : "zh-CN";
    document.querySelectorAll("[data-i18n]").forEach(node => {
        const key = node.dataset.i18n;
        node.textContent = t(key);
    });
    profileEls.languageLabel.textContent = language === "en-US" ? t("en") : t("zh");
}

function applyFontSize(fontSize) {
    document.body.classList.remove("font-small", "font-standard", "font-large", "font-x-large");
    const allowed = ["small", "standard", "large", "x-large"];
    const size = allowed.includes(fontSize) ? fontSize : "standard";
    document.body.classList.add(`font-${size}`);
    profileEls.fontSizeLabel.textContent = t(size === "x-large" ? "xLarge" : size);
}

function renderChoices() {
    document.querySelectorAll("[data-language]").forEach(button => {
        button.classList.toggle("active", button.dataset.language === profileState.language);
    });
    document.querySelectorAll("[data-font-size]").forEach(button => {
        button.classList.toggle("active", button.dataset.fontSize === profileState.fontSize);
    });
}

function t(key) {
    return (i18n[profileState.language] || i18n["zh-CN"])[key] || key;
}

function setMessage(target, message, error = false) {
    target.textContent = message;
    target.classList.toggle("toast", error);
}

async function apiGet(url) {
    return api(url, { method: "GET" });
}

async function apiPost(url, body) {
    return api(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
    });
}

async function apiPut(url, body) {
    return api(url, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
    });
}

async function api(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        if (response.status === 401) {
            window.location.href = "/login";
            return null;
        }
        throw new Error(data?.message || `请求失败：${response.status}`);
    }
    return data;
}

const state = {
    mode: "login",
};

const els = {
    form: document.querySelector("#authForm"),
    loginTab: document.querySelector("#loginTab"),
    registerTab: document.querySelector("#registerTab"),
    username: document.querySelector("#usernameInput"),
    password: document.querySelector("#passwordInput"),
    submit: document.querySelector("#authSubmitBtn"),
    message: document.querySelector("#authMessage"),
};

document.addEventListener("DOMContentLoaded", initAuth);

async function initAuth() {
    bindAuthEvents();
    try {
        const user = await apiGet("/api/auth/me");
        if (user?.username) {
            window.location.href = "/";
        }
    } catch (error) {
        // 未登录时保持在当前页面。
    }
}

function bindAuthEvents() {
    els.loginTab.addEventListener("click", () => setMode("login"));
    els.registerTab.addEventListener("click", () => setMode("register"));
    els.form.addEventListener("submit", submitAuth);
}

function setMode(mode) {
    state.mode = mode;
    els.loginTab.classList.toggle("active", mode === "login");
    els.registerTab.classList.toggle("active", mode === "register");
    els.submit.textContent = mode === "login" ? "登录" : "注册并登录";
    els.password.autocomplete = mode === "login" ? "current-password" : "new-password";
    setMessage("");
}

async function submitAuth(event) {
    event.preventDefault();
    const username = els.username.value.trim();
    const password = els.password.value;
    if (!username || !password) {
        setMessage("请输入用户名和密码", true);
        return;
    }

    els.submit.disabled = true;
    setMessage(state.mode === "login" ? "登录中..." : "注册中...");
    try {
        await apiPost(`/api/auth/${state.mode}`, { username, password });
        window.location.href = "/";
    } catch (error) {
        setMessage(error.message, true);
    } finally {
        els.submit.disabled = false;
    }
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

async function api(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error(data?.message || `请求失败：${response.status}`);
    }
    return data;
}

function setMessage(message, error = false) {
    els.message.textContent = message;
    els.message.classList.toggle("toast", error);
}

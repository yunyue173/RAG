const state = {
    documents: [],
    conversations: [],
    documentId: null,
    conversationId: null,
    lastQuestion: "",
    historySearchTimer: null,
    historySearchKeyword: "",
    focusedMessageId: null,
    focusedKeyword: "",
};

const els = {
    appStatus: document.querySelector("#appStatus"),
    currentUser: document.querySelector("#currentUser"),
    profileBtn: document.querySelector("#profileBtn"),
    profileAvatar: document.querySelector("#profileAvatar"),
    fileInput: document.querySelector("#fileInput"),
    fileName: document.querySelector("#fileName"),
    uploadBtn: document.querySelector("#uploadBtn"),
    uploadProgress: document.querySelector("#uploadProgress"),
    uploadMessage: document.querySelector("#uploadMessage"),
    refreshDocsBtn: document.querySelector("#refreshDocsBtn"),
    historySearchInput: document.querySelector("#historySearchInput"),
    historySearchResults: document.querySelector("#historySearchResults"),
    documentList: document.querySelector("#documentList"),
    activeDocTitle: document.querySelector("#activeDocTitle"),
    activeDocMeta: document.querySelector("#activeDocMeta"),
    newConversationBtn: document.querySelector("#newConversationBtn"),
    conversationList: document.querySelector("#conversationList"),
    messages: document.querySelector("#messages"),
    questionForm: document.querySelector("#questionForm"),
    questionInput: document.querySelector("#questionInput"),
    topKSelect: document.querySelector("#topKSelect"),
    sendBtn: document.querySelector("#sendBtn"),
    citationList: document.querySelector("#citationList"),
    citationCount: document.querySelector("#citationCount"),
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    bindEvents();
    await Promise.all([loadCurrentUser(), loadStatus(), loadDocuments()]);
}

function bindEvents() {
    els.fileInput.addEventListener("change", () => {
        els.fileName.textContent = els.fileInput.files[0]?.name || "选择文件";
        setUploadMessage("");
    });
    els.uploadBtn.addEventListener("click", uploadSelectedFile);
    els.refreshDocsBtn.addEventListener("click", () => loadDocuments(true));
    els.newConversationBtn.addEventListener("click", createConversation);
    els.questionForm.addEventListener("submit", askQuestion);
    els.profileBtn.addEventListener("click", () => {
        window.location.href = "/profile";
    });
    els.historySearchInput.addEventListener("input", searchHistory);
    els.historySearchInput.addEventListener("search", searchHistory);
}

async function loadCurrentUser() {
    try {
        const user = await apiGet("/api/auth/me");
        if (!user?.username) {
            window.location.href = "/login";
            return;
        }
        const displayName = user.displayName || user.username;
        els.currentUser.textContent = displayName;
        renderUserAvatar(displayName, user.avatarDataUrl);
        applyPreferences(user);
    } catch (error) {
        window.location.href = "/login";
    }
}

function renderUserAvatar(displayName, avatarDataUrl) {
    els.profileAvatar.textContent = "";
    els.profileAvatar.classList.toggle("has-image", Boolean(avatarDataUrl));
    if (avatarDataUrl) {
        const image = document.createElement("img");
        image.src = avatarDataUrl;
        image.alt = "头像";
        els.profileAvatar.append(image);
        return;
    }
    els.profileAvatar.textContent = (displayName || "用").slice(0, 1).toUpperCase();
}

async function loadStatus() {
    try {
        const status = await apiGet("/api/status");
        els.appStatus.textContent = `${status.embeddingMode} / ${status.answerMode}`;
        els.topKSelect.value = String(status.topK || 3);
    } catch (error) {
        els.appStatus.textContent = "状态读取失败";
    }
}

async function loadDocuments(keepSelection = false) {
    try {
        state.documents = await apiGet("/api/documents");
        renderDocuments();
        if (!keepSelection && state.documents.length > 0 && !state.documentId) {
            await selectDocument(state.documents[0].id);
        }
        if (state.documents.length === 0) {
            resetDocumentState();
        }
    } catch (error) {
        renderError(els.documentList, error.message);
    }
}

async function uploadSelectedFile() {
    const file = els.fileInput.files[0];
    if (!file) {
        setUploadMessage("请选择 PDF 或 TXT 文件", true);
        return;
    }
    setUploadMessage("上传中");
    els.uploadBtn.disabled = true;
    els.uploadProgress.style.width = "0%";
    try {
        const response = await uploadFile(file);
        setUploadMessage(`解析完成：${response.document.chunkCount} 个文本块`);
        els.fileInput.value = "";
        els.fileName.textContent = "选择文件";
        await loadDocuments(true);
        await selectDocument(response.document.id, response.conversation.id);
    } catch (error) {
        setUploadMessage(error.message, true);
    } finally {
        els.uploadBtn.disabled = false;
        setTimeout(() => {
            els.uploadProgress.style.width = "0%";
        }, 700);
    }
}

function uploadFile(file) {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        formData.append("file", file);
        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/api/documents");
        xhr.upload.onprogress = event => {
            if (event.lengthComputable) {
                els.uploadProgress.style.width = `${Math.round((event.loaded / event.total) * 100)}%`;
            }
        };
        xhr.onload = () => {
            const data = xhr.responseText ? JSON.parse(xhr.responseText) : null;
            if (xhr.status >= 200 && xhr.status < 300) {
                els.uploadProgress.style.width = "100%";
                resolve(data);
            } else {
                reject(new Error(data?.message || "上传失败"));
            }
        };
        xhr.onerror = () => reject(new Error("网络错误，上传失败"));
        xhr.send(formData);
    });
}

async function selectDocument(documentId, preferredConversationId = null) {
    state.documentId = documentId;
    const documentRecord = state.documents.find(item => item.id === documentId);
    if (!documentRecord) {
        resetDocumentState();
        return;
    }
    els.activeDocTitle.textContent = documentRecord.filename;
    els.activeDocMeta.textContent = `${documentRecord.chunkCount} 个文本块 / ${documentRecord.characterCount} 字符 / ${formatBytes(documentRecord.fileSize)}`;
    els.newConversationBtn.disabled = false;
    els.questionInput.disabled = false;
    els.sendBtn.disabled = false;
    renderDocuments();
    await loadConversations(documentId, preferredConversationId);
}

async function loadConversations(documentId, preferredConversationId = null) {
    state.conversations = await apiGet(`/api/documents/${documentId}/conversations`);
    if (state.conversations.length === 0) {
        const conversation = await apiPost(`/api/documents/${documentId}/conversations`, { title: "默认会话" });
        state.conversations = [conversation];
    }
    const target = preferredConversationId || state.conversations[0].id;
    await selectConversation(target);
}

async function createConversation() {
    if (!state.documentId) {
        return;
    }
    clearFocusedHistory();
    const title = `会话 ${state.conversations.length + 1}`;
    const conversation = await apiPost(`/api/documents/${state.documentId}/conversations`, { title });
    state.conversations.unshift(conversation);
    await selectConversation(conversation.id);
}

async function selectConversation(conversationId) {
    state.conversationId = conversationId;
    renderConversations();
    const messages = await apiGet(`/api/conversations/${conversationId}/messages`);
    renderMessages(messages);
    const lastAssistant = [...messages].reverse().find(message => message.role === "assistant" && message.citations?.length);
    renderCitations(lastAssistant?.citations || [], state.lastQuestion);
}

async function askQuestion(event) {
    event.preventDefault();
    const question = els.questionInput.value.trim();
    if (!question || !state.conversationId) {
        return;
    }
    clearFocusedHistory();
    state.lastQuestion = question;
    els.questionInput.value = "";
    setChatBusy(true);
    renderPending(question);
    try {
        const response = await apiPost(`/api/conversations/${state.conversationId}/messages`, {
            question,
            topK: Number(els.topKSelect.value),
        });
        const messages = await apiGet(`/api/conversations/${state.conversationId}/messages`);
        renderMessages(messages);
        renderCitations(response.citations || [], question);
        await loadDocuments(true);
        renderConversations();
    } catch (error) {
        renderError(els.messages, error.message);
    } finally {
        setChatBusy(false);
    }
}

function searchHistory() {
    clearTimeout(state.historySearchTimer);
    const keyword = els.historySearchInput.value.trim();
    state.historySearchKeyword = keyword;
    if (keyword.length < 2) {
        hideHistorySearchResults();
        return;
    }
    renderHistorySearchLoading();
    state.historySearchTimer = setTimeout(async () => {
        try {
            const results = await apiGet(`/api/history/search?keyword=${encodeURIComponent(keyword)}`);
            if (state.historySearchKeyword === keyword) {
                renderHistorySearchResults(results, keyword);
            }
        } catch (error) {
            renderHistorySearchError(error.message);
        }
    }, 250);
}

function renderHistorySearchLoading() {
    els.historySearchResults.hidden = false;
    els.historySearchResults.innerHTML = `<div class="history-search-empty">搜索中...</div>`;
}

function renderHistorySearchResults(results, keyword) {
    els.historySearchResults.hidden = false;
    els.historySearchResults.innerHTML = "";
    if (!results.length) {
        els.historySearchResults.innerHTML = `<div class="history-search-empty">未找到相关会话</div>`;
        return;
    }
    for (const result of results) {
        const button = document.createElement("button");
        button.className = "history-result";
        button.type = "button";
        button.innerHTML = `
            <span class="history-result-title"></span>
            <span class="history-result-meta"></span>
            <span class="history-result-snippet"></span>
        `;
        button.querySelector(".history-result-title").textContent = result.conversationTitle || "未命名会话";
        button.querySelector(".history-result-meta").textContent = `${result.documentName || "未知文档"} · ${roleLabel(result.messageRole)} · ${formatDate(result.matchedAt)}`;
        button.querySelector(".history-result-snippet").innerHTML = highlight(result.snippet || "", keyword);
        button.addEventListener("click", async () => {
            els.historySearchInput.value = keyword;
            state.lastQuestion = keyword;
            state.focusedMessageId = result.messageId || null;
            state.focusedKeyword = keyword;
            await selectDocument(result.documentId, result.conversationId);
        });
        els.historySearchResults.append(button);
    }
}

function renderHistorySearchError(message) {
    els.historySearchResults.hidden = false;
    els.historySearchResults.innerHTML = `<div class="history-search-empty toast">${escapeHtml(message)}</div>`;
}

function hideHistorySearchResults() {
    els.historySearchResults.hidden = true;
    els.historySearchResults.innerHTML = "";
}

function renderDocuments() {
    els.documentList.innerHTML = "";
    if (state.documents.length === 0) {
        els.documentList.innerHTML = `<div class="empty-reference">暂无文档</div>`;
        return;
    }
    for (const doc of state.documents) {
        const item = document.createElement("div");
        item.className = `document-item${doc.id === state.documentId ? " active" : ""}`;

        const main = document.createElement("button");
        main.className = "document-main";
        main.type = "button";
        main.addEventListener("click", () => {
            clearFocusedHistory();
            selectDocument(doc.id);
        });
        main.innerHTML = `
            <p class="document-name"></p>
            <div class="document-meta">${doc.chunkCount} 块 / ${doc.characterCount} 字符<br>${formatDate(doc.uploadedAt)}</div>
        `;
        main.querySelector(".document-name").textContent = doc.filename;

        const deleteBtn = document.createElement("button");
        deleteBtn.className = "delete-doc";
        deleteBtn.type = "button";
        deleteBtn.title = "删除文档";
        deleteBtn.innerHTML = `
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 6h18M8 6V4h8v2M9 10v8M15 10v8M6 6l1 16h10l1-16"/>
            </svg>
        `;
        deleteBtn.addEventListener("click", async () => {
            if (!confirm(`删除文档“${doc.filename}”？`)) {
                return;
            }
            await apiDelete(`/api/documents/${doc.id}`);
            if (state.documentId === doc.id) {
                resetDocumentState();
            }
            await loadDocuments(false);
        });

        item.append(main, deleteBtn);
        els.documentList.append(item);
    }
}

function renderConversations() {
    els.conversationList.innerHTML = "";
    for (const conversation of state.conversations) {
        const tab = document.createElement("button");
        tab.className = `conversation-tab${conversation.id === state.conversationId ? " active" : ""}`;
        tab.type = "button";
        tab.textContent = conversation.title;
        tab.title = conversation.title;
        tab.addEventListener("click", () => {
            clearFocusedHistory();
            selectConversation(conversation.id);
        });
        els.conversationList.append(tab);
    }
}

function clearFocusedHistory() {
    state.focusedMessageId = null;
    state.focusedKeyword = "";
}

function renderMessages(messages) {
    els.messages.innerHTML = "";
    if (!messages.length) {
        els.messages.innerHTML = `
            <div class="empty-state">
                <strong>当前会话暂无消息</strong>
                <span>输入问题后会显示答案和引用。</span>
            </div>
        `;
        return;
    }
    for (const message of messages) {
        els.messages.append(createMessageElement(message));
    }
    const focused = state.focusedMessageId
        ? els.messages.querySelector(`[data-message-id="${state.focusedMessageId}"]`)
        : null;
    if (focused) {
        focused.scrollIntoView({ block: "center" });
    } else {
        els.messages.scrollTop = els.messages.scrollHeight;
    }
}

function renderPending(question) {
    els.messages.innerHTML = "";
    els.messages.append(createMessageElement({ role: "user", content: question, createdAt: new Date().toISOString() }));
    els.messages.append(createMessageElement({ role: "assistant", content: "检索文档并生成回答中...", createdAt: new Date().toISOString() }));
}

function createMessageElement(message) {
    const node = document.createElement("article");
    node.className = `message ${message.role}${message.id === state.focusedMessageId ? " focused" : ""}`;
    if (message.id) {
        node.dataset.messageId = message.id;
    }
    const meta = document.createElement("span");
    meta.className = "message-meta";
    meta.textContent = `${message.role === "user" ? "你" : "助手"} · ${formatDate(message.createdAt)}`;
    const content = document.createElement("span");
    if (message.id === state.focusedMessageId && state.focusedKeyword) {
        content.innerHTML = highlight(message.content, state.focusedKeyword);
    } else {
        content.textContent = message.content;
    }
    node.append(meta, content);
    return node;
}

function renderCitations(citations, question) {
    els.citationCount.textContent = String(citations.length);
    els.citationList.innerHTML = "";
    if (!citations.length) {
        els.citationList.innerHTML = `<div class="empty-reference">暂无引用</div>`;
        return;
    }
    citations.forEach((citation, index) => {
        const item = document.createElement("article");
        item.className = "citation-item";
        item.style.borderColor = index === 0 ? "var(--accent)" : "var(--line)";

        const topline = document.createElement("div");
        topline.className = "citation-topline";
        topline.innerHTML = `
            <span><strong>片段 ${index + 1}</strong> / 原文块 #${citation.chunkIndex}</span>
            <span>相似度 ${Number(citation.score).toFixed(4)}</span>
        `;

        const text = document.createElement("p");
        text.className = "citation-text";

        const toggle = document.createElement("button");
        toggle.className = "citation-toggle";
        toggle.type = "button";

        let expanded = false;
        const preview = truncateText(citation.content, 110);
        const renderCitationText = () => {
            item.classList.toggle("expanded", expanded);
            text.innerHTML = highlight(expanded ? citation.content : preview, question);
            toggle.textContent = expanded ? "收起原文" : "展开原文";
            toggle.setAttribute("aria-expanded", String(expanded));
        };
        toggle.addEventListener("click", () => {
            expanded = !expanded;
            renderCitationText();
        });
        renderCitationText();

        item.append(topline, text, toggle);
        els.citationList.append(item);
    });
}

function resetDocumentState() {
    state.documentId = null;
    state.conversationId = null;
    state.conversations = [];
    els.activeDocTitle.textContent = "选择或上传文档";
    els.activeDocMeta.textContent = "当前没有加载文档";
    els.newConversationBtn.disabled = true;
    els.questionInput.disabled = true;
    els.sendBtn.disabled = true;
    els.conversationList.innerHTML = "";
    els.messages.innerHTML = `
        <div class="empty-state">
            <strong>上传文档后开始提问</strong>
            <span>系统会检索相关原文片段并生成回答。</span>
        </div>
    `;
    renderCitations([], "");
}

function setChatBusy(busy) {
    els.sendBtn.disabled = busy || !state.conversationId;
    els.questionInput.disabled = busy || !state.conversationId;
}

function setUploadMessage(message, error = false) {
    els.uploadMessage.textContent = message;
    els.uploadMessage.classList.toggle("toast", error);
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

async function apiDelete(url) {
    return api(url, { method: "DELETE" });
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

function renderError(target, message) {
    target.innerHTML = `<div class="empty-reference toast">${escapeHtml(message)}</div>`;
}

function highlight(text, question) {
    let html = escapeHtml(text || "");
    const terms = extractTerms(question).sort((a, b) => b.length - a.length);
    for (const term of terms) {
        const escapedTerm = escapeHtml(term);
        const regex = new RegExp(`(${escapeRegExp(escapedTerm)})`, "gi");
        html = html.replace(regex, "<mark>$1</mark>");
    }
    return html;
}

function extractTerms(text) {
    const matches = String(text || "").match(/[\u4e00-\u9fff]{2,}|[a-zA-Z0-9]{2,}/g) || [];
    return [...new Set(matches.map(term => term.toLowerCase()))].slice(0, 12);
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function truncateText(value, maxLength) {
    const text = String(value || "").replace(/\s+/g, " ").trim();
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, maxLength - 1)}...`;
}

function formatBytes(bytes) {
    if (!bytes) {
        return "0 B";
    }
    const units = ["B", "KB", "MB", "GB"];
    let value = bytes;
    let index = 0;
    while (value >= 1024 && index < units.length - 1) {
        value /= 1024;
        index++;
    }
    return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function formatDate(value) {
    if (!value) {
        return "";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    }).format(new Date(value));
}

function roleLabel(role) {
    if (role === "user") {
        return "用户消息";
    }
    if (role === "assistant") {
        return "助手回答";
    }
    return "会话";
}

function applyFontSize(fontSize) {
    document.body.classList.remove("font-small", "font-standard", "font-large", "font-x-large");
    const allowed = ["small", "standard", "large", "x-large"];
    const size = allowed.includes(fontSize) ? fontSize : "standard";
    document.body.classList.add(`font-${size}`);
}

function applyPreferences(user) {
    applyFontSize(user.fontSize);
    applyLanguage(user.language || "zh-CN");
}

function applyLanguage(language) {
    const en = language === "en-US";
    document.documentElement.lang = en ? "en-US" : "zh-CN";
    document.querySelector(".brand-copy h1").textContent = en ? "Document QA" : "文档问答";
    document.querySelector(".profile-copy span").textContent = en ? "Profile" : "个人资料";
    document.querySelector(".upload-panel .panel-title span:first-child").textContent = en ? "Upload" : "文档上传";
    document.querySelector("#fileName").textContent = en ? "Choose file" : "选择文件";
    document.querySelector("#uploadBtn span").textContent = en ? "Upload and parse" : "上传并解析";
    document.querySelector(".document-panel .panel-title > span").textContent = en ? "Documents" : "文档列表";
    document.querySelector("#historySearchInput").placeholder = en ? "Search" : "搜索";
    document.querySelector("#activeDocTitle").textContent = state.documentId
            ? document.querySelector("#activeDocTitle").textContent
            : (en ? "Select or upload a document" : "选择或上传文档");
    document.querySelector("#questionInput").placeholder = en ? "Ask about the current document" : "针对当前文档输入问题";
    document.querySelector(".reference-pane h2").textContent = en ? "References" : "引用片段";
}

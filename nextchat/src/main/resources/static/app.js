/**
 * Chatty — Real-time Chat Frontend (Modeled after burakorkmez/fullstack-chat-app)
 * Powered by Spring Boot WebSocket STOMP + SockJS + DaisyUI v4 + Tailwind CSS
 */

const STORAGE_KEYS = {
    ACCESS_TOKEN: 'nextchat_access_token',
    REFRESH_TOKEN: 'nextchat_refresh_token',
    USER: 'nextchat_user',
    THEME: 'chat-theme'
};

// All 32 DaisyUI themes from burakorkmez/fullstack-chat-app/frontend/src/constants/index.js
const THEMES = [
    "light", "dark", "cupcake", "bumblebee", "emerald", "corporate", "synthwave", "retro",
    "cyberpunk", "valentine", "halloween", "garden", "forest", "aqua", "lofi", "pastel",
    "fantasy", "wireframe", "black", "luxury", "dracula", "cmyk", "autumn", "business",
    "acid", "lemonade", "night", "coffee", "winter", "dim", "nord", "sunset"
];

// Preset avatars for quick 1-click selection on Profile Page
const PRESET_AVATARS = [
    "https://api.dicebear.com/9.x/adventurer/svg?seed=Felix&backgroundColor=b6e3f4",
    "https://api.dicebear.com/9.x/adventurer/svg?seed=Aneka&backgroundColor=ffdfbf",
    "https://api.dicebear.com/9.x/adventurer/svg?seed=Liam&backgroundColor=c0aede",
    "https://api.dicebear.com/9.x/adventurer/svg?seed=Sophia&backgroundColor=d1d4f9",
    "https://api.dicebear.com/9.x/adventurer/svg?seed=Milo&backgroundColor=ffd5dc",
    "https://api.dicebear.com/9.x/bottts/svg?seed=ChattyBot&backgroundColor=b6e3f4"
];

// Application State
let stompClient = null;
let roomSubscriptions = new Map(); // roomId -> StompSubscription
let roomsList = [];
let contactsList = [];
let activeRoom = null;
let currentUser = null;
let currentView = 'auth'; // 'auth' | 'home' | 'settings' | 'profile'
let sidebarFilter = 'all'; // 'all' | 'direct' | 'group' | 'contacts'
let sidebarSearchQuery = '';
let pendingImageDataUrl = null;
let selectedGroupMemberIds = new Set();

// =====================================================================
// INITIALIZATION
// =====================================================================
window.addEventListener('DOMContentLoaded', () => {
    initTheme();
    renderThemeGrid();
    renderPresetAvatars();
    refreshIcons();

    const savedToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    const savedUser = localStorage.getItem(STORAGE_KEYS.USER);

    if (savedToken && savedUser) {
        try {
            currentUser = JSON.parse(savedUser);
            onAuthenticatedSuccess();
        } catch (e) {
            logout();
        }
    } else {
        navigateTo('auth');
    }
});

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

// =====================================================================
// THEME ENGINE (32 DaisyUI Themes)
// =====================================================================
function initTheme() {
    const savedTheme = localStorage.getItem(STORAGE_KEYS.THEME) || 'coffee';
    setTheme(savedTheme, false);
}

function setTheme(themeName, rerenderGrid = true) {
    document.documentElement.setAttribute('data-theme', themeName);
    localStorage.setItem(STORAGE_KEYS.THEME, themeName);
    if (rerenderGrid) {
        renderThemeGrid();
    }
}

function renderThemeGrid() {
    const container = document.getElementById('themeGridContainer');
    if (!container) return;
    const currentTheme = localStorage.getItem(STORAGE_KEYS.THEME) || 'coffee';

    container.innerHTML = THEMES.map(t => {
        const isActive = currentTheme === t;
        const label = t.charAt(0).toUpperCase() + t.slice(1);
        return `
            <button type="button" onclick="setTheme('${t}')"
                class="group flex flex-col items-center gap-1.5 p-2 rounded-lg transition-colors ${
                    isActive ? 'bg-base-200 ring-2 ring-primary' : 'hover:bg-base-200/50'
                }">
                <div class="relative h-8 w-full rounded-md overflow-hidden" data-theme="${t}">
                    <div class="absolute inset-0 grid grid-cols-4 gap-px p-1">
                        <div class="rounded bg-primary"></div>
                        <div class="rounded bg-secondary"></div>
                        <div class="rounded bg-accent"></div>
                        <div class="rounded bg-neutral"></div>
                    </div>
                </div>
                <span class="text-[11px] font-medium truncate w-full text-center">${label}</span>
            </button>
        `;
    }).join('');
}

// =====================================================================
// VIEW NAVIGATION & TOASTS
// =====================================================================
function navigateTo(view) {
    const isAuthenticated = Boolean(localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN) && currentUser);

    if (!isAuthenticated && (view === 'home' || view === 'profile')) {
        view = 'auth';
    }
    if (isAuthenticated && view === 'auth') {
        view = 'home';
    }

    currentView = view;

    const views = {
        auth: document.getElementById('authView'),
        home: document.getElementById('homeView'),
        settings: document.getElementById('settingsView'),
        profile: document.getElementById('profileView')
    };

    Object.entries(views).forEach(([key, el]) => {
        if (!el) return;
        if (key === view) {
            el.classList.remove('hidden');
        } else {
            el.classList.add('hidden');
        }
    });

    // Update top navbar visibility
    const navAuthActions = document.getElementById('navAuthActions');
    const navWsBadge = document.getElementById('navWsBadge');
    if (isAuthenticated) {
        navAuthActions.classList.remove('hidden');
        navAuthActions.classList.add('flex');
        navWsBadge.classList.remove('hidden');
        navWsBadge.classList.add('sm:flex');
    } else {
        navAuthActions.classList.add('hidden');
        navAuthActions.classList.remove('flex');
        navWsBadge.classList.add('hidden');
        navWsBadge.classList.remove('sm:flex');
    }

    if (view === 'profile' && currentUser) {
        populateProfileView();
    }

    refreshIcons();
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const alertClass = type === 'error'
        ? 'alert-error'
        : type === 'info'
            ? 'alert-info'
            : 'alert-success';

    const el = document.createElement('div');
    el.className = `alert ${alertClass} shadow-lg text-xs py-2.5 px-4 rounded-xl animate-fade-in flex items-center gap-2`;
    el.innerHTML = `<span>${escapeHtml(message)}</span>`;
    container.appendChild(el);

    setTimeout(() => {
        el.remove();
    }, 3000);
}

// =====================================================================
// AVATAR GENERATOR & HELPERS
// =====================================================================
function getUserAvatarUrl(user) {
    if (!user) {
        return `https://api.dicebear.com/9.x/adventurer/svg?seed=Guest&backgroundColor=b6e3f4`;
    }
    if (user.avatarUrl && user.avatarUrl.trim() !== '') {
        return user.avatarUrl;
    }
    const seed = encodeURIComponent(user.username || user.fullName || `user_${user.id || 1}`);
    return `https://api.dicebear.com/9.x/adventurer/svg?seed=${seed}&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;
}

function getRoomAvatarUrl(room) {
    if (!room) return getUserAvatarUrl(null);
    if (room.avatarUrl && room.avatarUrl.trim() !== '') {
        return room.avatarUrl;
    }
    if (room.type === 'PRIVATE' && Array.isArray(room.participants) && currentUser) {
        const partner = room.participants.find(p => p.userId !== currentUser.id && p.username !== currentUser.username);
        if (partner) {
            return getUserAvatarUrl(partner);
        }
    }
    const seed = encodeURIComponent(room.name || `room_${room.id}`);
    return `https://api.dicebear.com/9.x/initials/svg?seed=${seed}&backgroundColor=6366f1,ec4899,14b8a6,f59e0b`;
}

function formatRoomDisplayName(room) {
    if (!room) return 'Conversation';
    if (room.name && room.name.trim() !== '') return room.name;
    if (Array.isArray(room.participants) && currentUser) {
        const partner = room.participants.find(p => p.userId !== currentUser.id && p.username !== currentUser.username);
        if (partner) return partner.fullName || partner.username || `Room #${room.id}`;
    }
    return `Chat #${room.id}`;
}

function formatMessageTime(isoString) {
    if (!isoString) return '';
    const date = new Date(isoString);
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false });
}

// =====================================================================
// REST API CLIENT WITH AUTOMATIC JWT ROTATION
// =====================================================================
async function apiFetch(path, options = {}, retry = true) {
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (accessToken && !options.skipAuth) {
        headers['Authorization'] = `Bearer ${accessToken}`;
    }

    const response = await fetch(path, { ...options, headers });
    let payload = null;
    try {
        payload = await response.json();
    } catch (e) {
        payload = null;
    }

    if (response.status === 401 && !options.skipAuth && retry) {
        const refreshed = await refreshAccessToken(true);
        if (refreshed) {
            return apiFetch(path, options, false);
        }
    }

    if (!response.ok || (payload && payload.success === false)) {
        let errMsg = payload?.message || `HTTP ${response.status}`;
        if (payload?.data && typeof payload.data === 'object') {
            const fieldErrors = Object.values(payload.data).join(', ');
            if (fieldErrors) errMsg += `: ${fieldErrors}`;
        }
        throw new Error(errMsg);
    }

    return payload?.data !== undefined ? payload.data : payload;
}

// =====================================================================
// AUTHENTICATION (Login, Register, Token Rotation, Logout)
// =====================================================================
function switchAuthTab(tab) {
    const loginForm = document.getElementById('loginForm');
    const regForm = document.getElementById('registerForm');
    const heading = document.getElementById('authHeading');
    const subheading = document.getElementById('authSubheading');
    const switchText = document.getElementById('authSwitchText');
    const patternTitle = document.getElementById('patternTitle');
    const patternSubtitle = document.getElementById('patternSubtitle');

    hideAuthAlert();

    if (tab === 'login') {
        loginForm.classList.remove('hidden');
        regForm.classList.add('hidden');
        heading.textContent = 'Welcome Back';
        subheading.textContent = 'Sign in to your account';
        patternTitle.textContent = 'Welcome back!';
        patternSubtitle.textContent = 'Sign in to continue your conversations and catch up with your messages in real-time.';
        switchText.innerHTML = `
            Don't have an account?
            <button type="button" onclick="switchAuthTab('register')" class="link link-primary font-medium ml-1">
                Create account
            </button>
        `;
    } else {
        loginForm.classList.add('hidden');
        regForm.classList.remove('hidden');
        heading.textContent = 'Create Account';
        subheading.textContent = 'Get started with your free account';
        patternTitle.textContent = 'Join our community';
        patternSubtitle.textContent = 'Connect with friends, share moments, and stay in touch with your loved ones.';
        switchText.innerHTML = `
            Already have an account?
            <button type="button" onclick="switchAuthTab('login')" class="link link-primary font-medium ml-1">
                Sign in
            </button>
        `;
    }
    refreshIcons();
}

function togglePasswordVisibility(inputId, iconId) {
    const input = document.getElementById(inputId);
    const icon = document.getElementById(iconId);
    if (!input || !icon) return;

    if (input.type === 'password') {
        input.type = 'text';
        icon.setAttribute('data-lucide', 'eye-off');
    } else {
        input.type = 'password';
        icon.setAttribute('data-lucide', 'eye');
    }
    refreshIcons();
}

function showAuthAlert(msg, isError = true) {
    const alertEl = document.getElementById('authAlert');
    alertEl.textContent = msg;
    alertEl.className = `alert text-xs font-medium py-2.5 mb-4 ${isError ? 'alert-error' : 'alert-success'}`;
    alertEl.classList.remove('hidden');
}

function hideAuthAlert() {
    document.getElementById('authAlert')?.classList.add('hidden');
}

async function quickLogin(username, password) {
    document.getElementById('loginUsername').value = username;
    document.getElementById('loginPassword').value = password;
    await performLogin(username, password);
}

async function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    await performLogin(username, password);
}

async function performLogin(username, password) {
    hideAuthAlert();
    const btn = document.getElementById('loginSubmitBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="loading loading-spinner loading-xs"></span> Signing in...`;
    }

    try {
        const authData = await apiFetch('/api/v1/auth/login', {
            method: 'POST',
            skipAuth: true,
            body: JSON.stringify({ username, password })
        });
        saveAuthSession(authData);
        showToast(`Welcome back, ${authData.user?.fullName || username}!`, 'success');
        await onAuthenticatedSuccess();
    } catch (err) {
        showAuthAlert(err.message, true);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Sign in';
        }
    }
}

async function handleRegister(event) {
    event.preventDefault();
    hideAuthAlert();

    const body = {
        fullName: document.getElementById('regFullName').value.trim(),
        username: document.getElementById('regUsername').value.trim(),
        email: document.getElementById('regEmail').value.trim(),
        password: document.getElementById('regPassword').value
    };

    const btn = document.getElementById('regSubmitBtn');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="loading loading-spinner loading-xs"></span> Creating account...`;
    }

    try {
        await apiFetch('/api/v1/auth/register', {
            method: 'POST',
            skipAuth: true,
            body: JSON.stringify(body)
        });
        showToast('Account created successfully! Signing you in...', 'success');
        await performLogin(body.username, body.password);
    } catch (err) {
        showAuthAlert(err.message, true);
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Create Account';
        }
    }
}

async function refreshAccessToken(silent = false) {
    const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
    if (!refreshToken) return false;

    try {
        const authData = await apiFetch('/api/v1/auth/refresh', {
            method: 'POST',
            skipAuth: true,
            body: JSON.stringify({ refreshToken })
        });
        saveAuthSession(authData);
        if (!silent) {
            showToast('JWT Access & Refresh tokens rotated successfully!', 'success');
        }
        connectWebSocket();
        return true;
    } catch (err) {
        if (!silent) {
            showToast('Session expired. Please sign in again.', 'error');
        }
        logout();
        return false;
    }
}

function saveAuthSession(authData) {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, authData.accessToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, authData.refreshToken);
    if (authData.user) {
        currentUser = authData.user;
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(authData.user));
    }
}

async function onAuthenticatedSuccess() {
    try {
        const me = await apiFetch('/api/v1/users/me');
        currentUser = me;
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(me));
    } catch (e) {
        // Keep cached user
    }

    navigateTo('home');
    await Promise.all([loadRooms(), loadContacts()]);
    connectWebSocket();
}

function logout() {
    unsubscribeAllRooms();
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
    currentUser = null;
    activeRoom = null;
    roomsList = [];
    contactsList = [];

    closeActiveChat();
    updateWsStatus(false);
    navigateTo('auth');
}

// =====================================================================
// WEBSOCKET STOMP & MULTI-ROOM SUBSCRIPTIONS
// =====================================================================
function connectWebSocket() {
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (!accessToken) return;

    if (stompClient && stompClient.connected) {
        unsubscribeAllRooms();
        stompClient.disconnect();
    }

    const socket = new SockJS('/ws-chat');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    const connectHeaders = {
        Authorization: `Bearer ${accessToken}`
    };

    stompClient.connect(
        connectHeaders,
        () => {
            updateWsStatus(true);
            subscribeAllUserRooms();
        },
        () => {
            updateWsStatus(false);
        }
    );
}

function updateWsStatus(connected) {
    const dot = document.getElementById('wsStatusDot');
    const text = document.getElementById('wsStatusText');
    if (!dot || !text) return;

    if (connected) {
        dot.className = 'size-2 rounded-full bg-success animate-pulse';
        text.textContent = 'Connected';
    } else {
        dot.className = 'size-2 rounded-full bg-error';
        text.textContent = 'Offline';
    }
}

function unsubscribeAllRooms() {
    roomSubscriptions.forEach(sub => {
        try { sub.unsubscribe(); } catch (e) {}
    });
    roomSubscriptions.clear();
}

function subscribeAllUserRooms() {
    if (!stompClient || !stompClient.connected) return;

    roomsList.forEach(room => {
        ensureRoomSubscription(room.id);
    });
}

function ensureRoomSubscription(roomId) {
    if (!stompClient || !stompClient.connected) return;
    if (roomSubscriptions.has(roomId)) return;

    const destination = `/topic/room/${roomId}`;
    const sub = stompClient.subscribe(destination, (stompFrame) => {
        try {
            const incomingMsg = JSON.parse(stompFrame.body);
            handleIncomingRealtimeMessage(roomId, incomingMsg);
        } catch (err) {
            console.error('Failed to parse STOMP message:', err);
        }
    });

    roomSubscriptions.set(roomId, sub);
}

async function handleIncomingRealtimeMessage(roomId, msg) {
    const isCurrentRoom = activeRoom && activeRoom.id === roomId;
    const isFromMe = currentUser && (msg.senderId === currentUser.id || msg.senderUsername === currentUser.username);

    if (isCurrentRoom) {
        appendMessageBubble(msg, true);
        if (!isFromMe) {
            // Immediately mark as read on backend so unread count stays 0
            apiFetch(`/api/v1/rooms/${roomId}/read`, { method: 'POST' }).catch(() => {});
        }
    }

    // Update room list state in real time without full reload flicker
    const idx = roomsList.findIndex(r => r.id === roomId);
    if (idx !== -1) {
        const room = { ...roomsList[idx] };
        room.lastMessage = msg;
        room.updatedAt = msg.createdAt || new Date().toISOString();
        if (isCurrentRoom || isFromMe) {
            room.unreadCount = 0;
        } else {
            room.unreadCount = (room.unreadCount || 0) + 1;
        }
        roomsList.splice(idx, 1);
        roomsList.unshift(room);
        renderSidebarList();
    } else {
        await loadRooms();
    }
}

// =====================================================================
// SIDEBAR: ROOMS & CONTACTS LIST
// =====================================================================
async function loadRooms() {
    try {
        const rooms = await apiFetch('/api/v1/rooms');
        roomsList = Array.isArray(rooms) ? rooms : [];
        if (activeRoom) {
            // Keep activeRoom unreadCount cleared locally
            const activeInList = roomsList.find(r => r.id === activeRoom.id);
            if (activeInList) {
                activeInList.unreadCount = 0;
                activeRoom = activeInList;
            }
        }
        renderSidebarList();
        subscribeAllUserRooms();
    } catch (err) {
        console.error('Could not load rooms:', err);
    }
}

async function loadContacts() {
    try {
        const users = await apiFetch('/api/v1/users');
        contactsList = (Array.isArray(users) ? users : []).filter(u => !currentUser || u.id !== currentUser.id);
        if (sidebarFilter === 'contacts') {
            renderSidebarList();
        }
    } catch (err) {
        console.error('Could not load contacts:', err);
    }
}

function setSidebarFilter(filter) {
    sidebarFilter = filter;
    ['all', 'direct', 'group', 'contacts'].forEach(f => {
        const btn = document.getElementById(`filterBtn-${f}`);
        if (!btn) return;
        if (f === filter) {
            btn.className = 'py-1 rounded-md font-medium bg-base-100 shadow-sm text-primary';
        } else {
            btn.className = 'py-1 rounded-md opacity-70 hover:opacity-100';
        }
    });
    renderSidebarList();
}

function handleSidebarSearch(query) {
    sidebarSearchQuery = (query || '').trim().toLowerCase();
    renderSidebarList();
}

function renderSidebarList() {
    const container = document.getElementById('roomListContainer');
    if (!container) return;

    // Mode 1: People / Contacts tab
    if (sidebarFilter === 'contacts') {
        const filteredContacts = contactsList.filter(u => {
            if (!sidebarSearchQuery) return true;
            return (u.username || '').toLowerCase().includes(sidebarSearchQuery) ||
                   (u.fullName || '').toLowerCase().includes(sidebarSearchQuery);
        });

        if (!filteredContacts.length) {
            container.innerHTML = `<div class="text-center text-base-content/50 py-8 text-xs px-4">No contacts found</div>`;
            return;
        }

        container.innerHTML = filteredContacts.map(u => `
            <button type="button" onclick="openDirectRoom(${u.id})"
                class="w-full p-3 flex items-center gap-3 hover:bg-base-200 transition-colors text-left">
                <div class="relative mx-auto lg:mx-0 shrink-0">
                    <img src="${escapeHtml(getUserAvatarUrl(u))}" alt="${escapeHtml(u.username)}"
                        class="size-12 object-cover rounded-full bg-base-300 border border-base-300" />
                    <span class="absolute bottom-0 right-0 size-3 bg-emerald-500 rounded-full ring-2 ring-base-100"></span>
                </div>
                <div class="hidden lg:block text-left min-w-0 flex-1">
                    <div class="font-medium truncate text-sm">${escapeHtml(u.fullName || u.username)}</div>
                    <div class="text-xs text-base-content/60 truncate">@${escapeHtml(u.username)} • Tap to chat</div>
                </div>
            </button>
        `).join('');
        return;
    }

    // Mode 2: Conversations (All / Direct / Groups)
    const filteredRooms = roomsList.filter(r => {
        if (sidebarFilter === 'direct' && r.type !== 'PRIVATE') return false;
        if (sidebarFilter === 'group' && r.type !== 'GROUP') return false;
        if (sidebarSearchQuery) {
            const title = formatRoomDisplayName(r).toLowerCase();
            return title.includes(sidebarSearchQuery);
        }
        return true;
    });

    if (!filteredRooms.length) {
        container.innerHTML = `
            <div class="text-center text-base-content/50 py-10 px-4 space-y-2">
                <p class="text-xs">No conversations yet</p>
                <button type="button" onclick="setSidebarFilter('contacts')" class="btn btn-xs btn-outline">
                    Browse Contacts
                </button>
            </div>
        `;
        return;
    }

    container.innerHTML = filteredRooms.map(room => {
        const isSelected = activeRoom && activeRoom.id === room.id;
        const roomName = formatRoomDisplayName(room);
        const avatarUrl = getRoomAvatarUrl(room);
        const isGroup = room.type === 'GROUP';
        const unread = isSelected ? 0 : (room.unreadCount || 0);

        let lastMsgPreview = isGroup ? 'Group chat created' : 'Start a conversation';
        if (room.lastMessage) {
            const senderPrefix = currentUser && room.lastMessage.senderUsername === currentUser.username
                ? 'You: '
                : isGroup && room.lastMessage.senderUsername
                    ? `${room.lastMessage.senderUsername}: `
                    : '';
            const contentPreview = room.lastMessage.messageType === 'IMAGE'
                ? '📷 Photo'
                : (room.lastMessage.content || '');
            lastMsgPreview = `${senderPrefix}${contentPreview}`;
        }

        const timeLabel = room.lastMessage?.createdAt
            ? formatMessageTime(room.lastMessage.createdAt)
            : '';

        return `
            <button type="button" onclick="selectRoomById(${room.id})"
                class="w-full p-3 flex items-center gap-3 hover:bg-base-200/80 transition-colors ${
                    isSelected ? 'bg-base-200 ring-1 ring-base-300' : ''
                }">
                <div class="relative mx-auto lg:mx-0 shrink-0">
                    <img src="${escapeHtml(avatarUrl)}" alt="${escapeHtml(roomName)}"
                        class="size-12 object-cover rounded-full bg-base-300 border border-base-300" />
                    <span class="absolute bottom-0 right-0 size-3 ${isGroup ? 'bg-primary' : 'bg-emerald-500'} rounded-full ring-2 ring-base-100"></span>
                </div>

                <div class="hidden lg:block text-left min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-1">
                        <span class="font-medium truncate text-sm ${unread > 0 ? 'font-bold text-base-content' : ''}">
                            ${escapeHtml(roomName)}
                        </span>
                        <span class="text-[11px] text-base-content/50 shrink-0">${timeLabel}</span>
                    </div>
                    <div class="flex items-center justify-between gap-2 mt-0.5">
                        <p class="text-xs truncate ${unread > 0 ? 'font-semibold text-base-content' : 'text-base-content/60'}">
                            ${escapeHtml(lastMsgPreview)}
                        </p>
                        ${unread > 0 ? `<span class="badge badge-primary badge-sm font-bold shrink-0">${unread}</span>` : ''}
                    </div>
                </div>
            </button>
        `;
    }).join('');
}

// =====================================================================
// ACTIVE CHAT ROOM SELECTION & HISTORY
// =====================================================================
function selectRoomById(roomId) {
    const room = roomsList.find(r => r.id === roomId);
    if (room) {
        selectRoom(room);
    }
}

async function selectRoom(room) {
    activeRoom = room;

    // Immediately clear unread badge locally
    room.unreadCount = 0;
    const roomInList = roomsList.find(r => r.id === room.id);
    if (roomInList) {
        roomInList.unreadCount = 0;
    }
    renderSidebarList();

    // Switch right panel from NoChatSelected to ChatContainer
    document.getElementById('noChatSelected').classList.add('hidden');
    const chatContainer = document.getElementById('chatContainer');
    chatContainer.classList.remove('hidden');
    chatContainer.classList.add('flex');

    // Populate ChatHeader
    document.getElementById('activeRoomAvatar').src = getRoomAvatarUrl(room);
    document.getElementById('activeRoomTitle').textContent = formatRoomDisplayName(room);

    if (room.type === 'GROUP') {
        const names = (room.participants || []).map(p => p.fullName || p.username).filter(Boolean).join(', ');
        document.getElementById('activeRoomMeta').textContent = names
            ? `${room.participants.length} members: ${names}`
            : 'Group Conversation';
    } else {
        document.getElementById('activeRoomMeta').textContent = 'Online';
    }

    ensureRoomSubscription(room.id);
    await loadRoomHistory(room.id);

    // Ensure backend marks room as read
    apiFetch(`/api/v1/rooms/${room.id}/read`, { method: 'POST' }).catch(() => {});
    document.getElementById('messageInput')?.focus();
}

function closeActiveChat() {
    activeRoom = null;
    document.getElementById('chatContainer')?.classList.add('hidden');
    document.getElementById('chatContainer')?.classList.remove('flex');
    document.getElementById('noChatSelected')?.classList.remove('hidden');
    renderSidebarList();
}

async function loadRoomHistory(roomId) {
    const container = document.getElementById('messagesContainer');
    if (!container) return;

    // MessageSkeleton loading state (just like MessageSkeleton.jsx in burakorkmez/fullstack-chat-app)
    container.innerHTML = Array.from({ length: 4 }).map((_, idx) => `
        <div class="chat ${idx % 2 === 0 ? 'chat-start' : 'chat-end'}">
            <div class="chat-image avatar">
                <div class="size-10 rounded-full">
                    <div class="skeleton w-full h-full rounded-full"></div>
                </div>
            </div>
            <div class="chat-header mb-1">
                <div class="skeleton h-4 w-16"></div>
            </div>
            <div class="chat-bubble bg-transparent p-0">
                <div class="skeleton h-12 w-[200px]"></div>
            </div>
        </div>
    `).join('');

    try {
        const pageData = await apiFetch(`/api/v1/rooms/${roomId}/messages?page=0&size=50`);
        const items = pageData?.content || pageData?.items || (Array.isArray(pageData) ? pageData : []);
        container.innerHTML = '';

        const sorted = [...items].sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
        if (!sorted.length) {
            container.innerHTML = `
                <div class="h-full flex flex-col items-center justify-center text-base-content/50 text-sm py-12 space-y-2">
                    <p class="font-medium">No messages here yet</p>
                    <p class="text-xs">Send a message or photo below to start the conversation!</p>
                </div>
            `;
            return;
        }

        sorted.forEach(msg => appendMessageBubble(msg, false));
        container.scrollTop = container.scrollHeight;
    } catch (err) {
        container.innerHTML = `<div class="text-center text-xs text-error py-8">Failed to load messages: ${escapeHtml(err.message)}</div>`;
    }
}

function appendMessageBubble(msg, autoScroll = true) {
    const container = document.getElementById('messagesContainer');
    if (!container) return;

    // Remove empty placeholder if present
    if (container.querySelector('.py-12')) {
        container.innerHTML = '';
    }

    const senderId = msg.senderId || msg.sender?.id;
    const senderUsername = msg.senderUsername || msg.sender?.username || '';
    const senderFullName = msg.senderFullName || msg.sender?.fullName || senderUsername || `User #${senderId || '?'}`;
    const isMine = currentUser && (senderId === currentUser.id || senderUsername === currentUser.username);

    const avatarSrc = isMine
        ? getUserAvatarUrl(currentUser)
        : getUserAvatarUrl({
            id: senderId,
            username: senderUsername,
            fullName: senderFullName,
            avatarUrl: msg.senderAvatarUrl || msg.sender?.avatarUrl
        });

    const timeStr = formatMessageTime(msg.createdAt);
    const isImage = msg.messageType === 'IMAGE' || (msg.content && msg.content.startsWith('data:image/'));

    const chatEl = document.createElement('div');
    chatEl.className = `chat ${isMine ? 'chat-end' : 'chat-start'} animate-fade-in`;

    const bodyHtml = isImage
        ? `<img src="${escapeHtml(msg.content)}" alt="Attachment" onclick="openImageLightbox('${escapeHtml(msg.content)}')"
                class="sm:max-w-[220px] rounded-lg mb-1 cursor-zoom-in border border-base-300 hover:opacity-95 transition" />`
        : `<p class="text-sm leading-relaxed break-words">${escapeHtml(msg.content || '')}</p>`;

    chatEl.innerHTML = `
        <div class="chat-image avatar">
            <div class="size-10 rounded-full border border-base-300 bg-base-200">
                <img src="${escapeHtml(avatarSrc)}" alt="avatar" />
            </div>
        </div>
        <div class="chat-header mb-1">
            <span class="text-xs font-medium opacity-75 mr-1">${escapeHtml(isMine ? 'You' : senderFullName)}</span>
            <time class="text-xs opacity-50">${timeStr}</time>
        </div>
        <div class="chat-bubble flex flex-col ${isMine ? 'chat-bubble-primary' : ''}">
            ${bodyHtml}
        </div>
    `;

    container.appendChild(chatEl);
    if (autoScroll) {
        container.scrollTop = container.scrollHeight;
    }
}

// =====================================================================
// SENDING MESSAGES & IMAGE ATTACHMENTS
// =====================================================================
function handleImageAttachment(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
        showToast('Please select an image file', 'error');
        return;
    }

    compressImageFile(file, 480, 0.72, (dataUrl) => {
        pendingImageDataUrl = dataUrl;
        document.getElementById('imagePreviewEl').src = dataUrl;
        document.getElementById('imagePreviewWrapper').classList.remove('hidden');
        document.getElementById('imagePreviewWrapper').classList.add('flex');
    });
}

function removeImageAttachment() {
    pendingImageDataUrl = null;
    const fileInput = document.getElementById('imageFileInput');
    if (fileInput) fileInput.value = '';
    document.getElementById('imagePreviewWrapper').classList.add('hidden');
    document.getElementById('imagePreviewWrapper').classList.remove('flex');
}

function sendRealtimeMessage(event) {
    event.preventDefault();
    if (!activeRoom) return;

    const input = document.getElementById('messageInput');
    const textContent = input.value.trim();

    if (!textContent && !pendingImageDataUrl) return;

    if (!stompClient || !stompClient.connected) {
        showToast('Reconnecting to real-time server...', 'info');
        connectWebSocket();
        return;
    }

    const destination = `/app/room/${activeRoom.id}`;

    // Send attached image if present
    if (pendingImageDataUrl) {
        stompClient.send(destination, {}, JSON.stringify({
            content: pendingImageDataUrl,
            messageType: 'IMAGE',
            replyToMessageId: null
        }));
        removeImageAttachment();
    }

    // Send text message if present
    if (textContent) {
        stompClient.send(destination, {}, JSON.stringify({
            content: textContent,
            messageType: 'TEXT',
            replyToMessageId: null
        }));
        input.value = '';
    }
}

function compressImageFile(file, maxDim, quality, callback) {
    const reader = new FileReader();
    reader.onload = (e) => {
        const img = new Image();
        img.onload = () => {
            let width = img.width;
            let height = img.height;
            if (width > maxDim || height > maxDim) {
                if (width > height) {
                    height = Math.round((height * maxDim) / width);
                    width = maxDim;
                } else {
                    width = Math.round((width * maxDim) / height);
                    height = maxDim;
                }
            }
            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, width, height);
            const dataUrl = canvas.toDataURL('image/jpeg', quality);
            callback(dataUrl);
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

// =====================================================================
// MODALS: DIRECT CHAT & CREATE GROUP
// =====================================================================
async function openNewChatModal() {
    document.getElementById('newChatModal').classList.remove('hidden');
    document.getElementById('modalUserSearchInput').value = '';
    if (!contactsList.length) {
        await loadContacts();
    }
    renderModalUserList(contactsList);
    document.getElementById('modalUserSearchInput').focus();
}

function closeNewChatModal() {
    document.getElementById('newChatModal').classList.add('hidden');
}

async function filterModalUsers(query) {
    const trimmed = (query || '').trim();
    if (!trimmed) {
        renderModalUserList(contactsList);
        return;
    }
    try {
        const found = await apiFetch(`/api/v1/users/search?q=${encodeURIComponent(trimmed)}`);
        const filtered = (found || []).filter(u => !currentUser || u.id !== currentUser.id);
        renderModalUserList(filtered);
    } catch (err) {
        console.error(err);
    }
}

function renderModalUserList(users) {
    const box = document.getElementById('modalUserListContainer');
    if (!box) return;

    if (!users || !users.length) {
        box.innerHTML = `<div class="p-6 text-center text-xs text-base-content/50">No matching users found</div>`;
        return;
    }

    box.innerHTML = users.map(u => `
        <div class="p-3 flex items-center justify-between hover:bg-base-200/70 transition">
            <div class="flex items-center gap-3">
                <img src="${escapeHtml(getUserAvatarUrl(u))}" alt="${escapeHtml(u.username)}"
                    class="size-10 rounded-full object-cover bg-base-300" />
                <div>
                    <div class="text-sm font-medium">${escapeHtml(u.fullName || u.username)}</div>
                    <div class="text-xs text-base-content/60">@${escapeHtml(u.username)}</div>
                </div>
            </div>
            <button type="button" onclick="openDirectRoom(${u.id})" class="btn btn-xs btn-primary">
                Message
            </button>
        </div>
    `).join('');
}

async function openDirectRoom(targetUserId) {
    try {
        const room = await apiFetch('/api/v1/rooms/direct', {
            method: 'POST',
            body: JSON.stringify({ targetUserId })
        });
        closeNewChatModal();
        if (sidebarFilter === 'contacts') {
            setSidebarFilter('all');
        }
        await loadRooms();
        selectRoom(room);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function toggleGroupModal(show) {
    const modal = document.getElementById('groupModal');
    if (!show) {
        modal.classList.add('hidden');
        return;
    }
    selectedGroupMemberIds.clear();
    document.getElementById('groupNameInput').value = '';
    updateSelectedMembersCount();
    modal.classList.remove('hidden');

    if (!contactsList.length) {
        await loadContacts();
    }
    renderGroupMembersPicker();
}

function renderGroupMembersPicker() {
    const container = document.getElementById('groupMembersPicker');
    if (!container) return;

    if (!contactsList.length) {
        container.innerHTML = `<div class="p-4 text-center text-xs text-base-content/50">No other users available</div>`;
        return;
    }

    container.innerHTML = contactsList.map(u => {
        const checked = selectedGroupMemberIds.has(u.id);
        return `
            <label class="flex items-center justify-between p-2.5 hover:bg-base-200 rounded-lg cursor-pointer transition">
                <div class="flex items-center gap-3">
                    <img src="${escapeHtml(getUserAvatarUrl(u))}" alt="${escapeHtml(u.username)}"
                        class="size-9 rounded-full object-cover bg-base-300" />
                    <div>
                        <div class="text-sm font-medium">${escapeHtml(u.fullName || u.username)}</div>
                        <div class="text-xs text-base-content/60">@${escapeHtml(u.username)}</div>
                    </div>
                </div>
                <input type="checkbox" class="checkbox checkbox-primary checkbox-sm"
                    ${checked ? 'checked' : ''}
                    onchange="toggleGroupMemberSelection(${u.id}, this.checked)" />
            </label>
        `;
    }).join('');
}

function toggleGroupMemberSelection(userId, isChecked) {
    if (isChecked) {
        selectedGroupMemberIds.add(userId);
    } else {
        selectedGroupMemberIds.delete(userId);
    }
    updateSelectedMembersCount();
}

function updateSelectedMembersCount() {
    const el = document.getElementById('selectedMembersCount');
    if (el) {
        el.textContent = `${selectedGroupMemberIds.size} selected`;
    }
}

async function createGroupRoom(event) {
    event.preventDefault();
    const groupName = document.getElementById('groupNameInput').value.trim();
    const memberIds = Array.from(selectedGroupMemberIds);

    if (!memberIds.length) {
        showToast('Please select at least 1 member for the group', 'error');
        return;
    }

    try {
        const room = await apiFetch('/api/v1/rooms/group', {
            method: 'POST',
            body: JSON.stringify({ groupName, memberIds })
        });
        toggleGroupModal(false);
        showToast(`Group "${groupName}" created!`, 'success');
        await loadRooms();
        selectRoom(room);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// =====================================================================
// PROFILE PAGE (Avatar upload, Preset Avatars, Profile Edit)
// =====================================================================
function populateProfileView() {
    if (!currentUser) return;
    document.getElementById('profileAvatarImg').src = getUserAvatarUrl(currentUser);
    document.getElementById('profileFullNameInput').value = currentUser.fullName || '';
    document.getElementById('profileUsernameDisplay').textContent = `@${currentUser.username}`;
    document.getElementById('profileEmailDisplay').textContent = currentUser.email || '—';
    document.getElementById('profileBioInput').value = currentUser.bio || '';

    const createdDate = currentUser.createdAt
        ? new Date(currentUser.createdAt).toLocaleDateString('vi-VN', { year: 'numeric', month: 'long', day: 'numeric' })
        : '2026';
    document.getElementById('profileMemberSince').textContent = createdDate;
}

function renderPresetAvatars() {
    const row = document.getElementById('presetAvatarsRow');
    if (!row) return;
    row.innerHTML = PRESET_AVATARS.map(url => `
        <button type="button" onclick="selectPresetAvatar('${url}')"
            class="size-10 rounded-full overflow-hidden border-2 border-base-300 hover:border-primary hover:scale-110 transition">
            <img src="${url}" alt="Preset avatar" class="w-full h-full object-cover" />
        </button>
    `).join('');
}

async function selectPresetAvatar(url) {
    document.getElementById('profileAvatarImg').src = url;
    await updateUserProfileOnServer({ avatarUrl: url });
}

function handleProfileAvatarUpload(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    const statusEl = document.getElementById('avatarUploadStatus');
    statusEl.textContent = 'Uploading photo...';

    compressImageFile(file, 256, 0.85, async (dataUrl) => {
        document.getElementById('profileAvatarImg').src = dataUrl;
        await updateUserProfileOnServer({ avatarUrl: dataUrl });
        statusEl.textContent = 'Click the camera icon to update your photo or choose a preset below';
    });
}

async function saveProfileChanges(event) {
    event.preventDefault();
    const fullName = document.getElementById('profileFullNameInput').value.trim();
    const bio = document.getElementById('profileBioInput').value.trim();
    await updateUserProfileOnServer({ fullName, bio });
}

async function updateUserProfileOnServer(fields) {
    try {
        const updated = await apiFetch('/api/v1/users/me', {
            method: 'PUT',
            body: JSON.stringify(fields)
        });
        currentUser = updated;
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(updated));
        populateProfileView();
        await Promise.all([loadRooms(), loadContacts()]);
        showToast('Profile updated successfully!', 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// =====================================================================
// LIGHTBOX & UTILITIES
// =====================================================================
function openImageLightbox(src) {
    const box = document.getElementById('imageLightbox');
    const img = document.getElementById('lightboxImg');
    if (!box || !img) return;
    img.src = src;
    box.classList.remove('hidden');
}

function closeImageLightbox() {
    document.getElementById('imageLightbox')?.classList.add('hidden');
}

function escapeHtml(str) {
    return String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

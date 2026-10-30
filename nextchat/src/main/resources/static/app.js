/**
 * NextChat Frontend Client (Tuần 7)
 * Tích hợp đầy đủ REST API (/api/v1/auth, /api/v1/users, /api/v1/rooms)
 * và kết nối thời gian thực WebSocket/SockJS/STOMP (/ws-chat, /app/chat.sendMessage, /topic/rooms.{roomId})
 */

const STORAGE_KEYS = {
    ACCESS_TOKEN: 'nextchat_access_token',
    REFRESH_TOKEN: 'nextchat_refresh_token',
    USER: 'nextchat_user',
    API_BASE: 'nextchat_api_base'
};

let stompClient = null;
let currentRoomSubscription = null;
let activeRoom = null;
let currentUser = null;

// Khởi chạy khi mở trang
window.addEventListener('DOMContentLoaded', () => {
    const savedBase = localStorage.getItem(STORAGE_KEYS.API_BASE);
    if (savedBase) {
        document.getElementById('apiBaseUrlInput').value = savedBase;
    }

    const savedToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    const savedUser = localStorage.getItem(STORAGE_KEYS.USER);

    if (savedToken && savedUser) {
        try {
            currentUser = JSON.parse(savedUser);
            onAuthenticatedSuccess();
        } catch (e) {
            logout();
        }
    }
});

function getApiBase() {
    const val = document.getElementById('apiBaseUrlInput')?.value?.trim() || 'http://localhost:8080';
    const clean = val.replace(/\/+$/, '');
    localStorage.setItem(STORAGE_KEYS.API_BASE, clean);
    return clean;
}

// Ghi nhật ký giao thức ở thanh bên phải để phục vụ demo
function logProtocol(type, title, detail = '') {
    const container = document.getElementById('protocolLogContainer');
    const time = new Date().toLocaleTimeString('vi-VN', { hour12: false });
    const colorMap = {
        REST: 'text-sky-400 border-sky-500/30 bg-sky-950/30',
        STOMP: 'text-emerald-400 border-emerald-500/30 bg-emerald-950/30',
        AUTH: 'text-amber-400 border-amber-500/30 bg-amber-950/30',
        ERROR: 'text-rose-400 border-rose-500/30 bg-rose-950/30'
    };
    const badgeClass = colorMap[type] || 'text-slate-300 border-slate-700 bg-slate-900';

    const item = document.createElement('div');
    item.className = `p-2 rounded border ${badgeClass} break-all`;
    item.innerHTML = `
        <div class="flex justify-between text-[10px] opacity-80 mb-0.5">
            <span class="font-bold">[${type}] ${title}</span>
            <span>${time}</span>
        </div>
        ${detail ? `<div class="text-slate-300 text-[10px] font-mono whitespace-pre-wrap">${escapeHtml(detail)}</div>` : ''}
    `;
    container.prepend(item);
}

function clearLogs() {
    document.getElementById('protocolLogContainer').innerHTML = '';
}

// Chuyển đổi tab Đăng nhập / Đăng ký
function switchAuthTab(tab) {
    const loginForm = document.getElementById('loginForm');
    const regForm = document.getElementById('registerForm');
    const tabLoginBtn = document.getElementById('tabLoginBtn');
    const tabRegBtn = document.getElementById('tabRegisterBtn');
    hideAuthAlert();

    if (tab === 'login') {
        loginForm.classList.remove('hidden');
        regForm.classList.add('hidden');
        tabLoginBtn.className = 'flex-1 pb-3 text-sm font-semibold border-b-2 border-indigo-500 text-indigo-400';
        tabRegBtn.className = 'flex-1 pb-3 text-sm font-semibold border-b-2 border-transparent text-slate-400 hover:text-slate-200';
    } else {
        loginForm.classList.add('hidden');
        regForm.classList.remove('hidden');
        tabRegBtn.className = 'flex-1 pb-3 text-sm font-semibold border-b-2 border-emerald-500 text-emerald-400';
        tabLoginBtn.className = 'flex-1 pb-3 text-sm font-semibold border-b-2 border-transparent text-slate-400 hover:text-slate-200';
    }
}

function showAuthAlert(msg, isError = true) {
    const alertEl = document.getElementById('authAlert');
    alertEl.textContent = msg;
    alertEl.className = `mb-4 p-3 rounded-lg text-xs font-medium ${
        isError ? 'bg-rose-950/60 border border-rose-500/40 text-rose-300' : 'bg-emerald-950/60 border border-emerald-500/40 text-emerald-300'
    }`;
    alertEl.classList.remove('hidden');
}

function hideAuthAlert() {
    document.getElementById('authAlert').classList.add('hidden');
}

// Wrapper gọi REST API có tự động đính kèm Bearer JWT và tự động xoay vòng Refresh Token nếu gặp 401
async function apiFetch(path, options = {}, retry = true) {
    const url = `${getApiBase()}${path}`;
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (accessToken && !options.skipAuth) {
        headers['Authorization'] = `Bearer ${accessToken}`;
    }

    logProtocol('REST', `${options.method || 'GET'} ${path}`, options.body || '');

    const response = await fetch(url, { ...options, headers });
    let payload = null;
    try {
        payload = await response.json();
    } catch (e) {
        payload = null;
    }

    // Tự động gọi Refresh Token Rotation nếu Access Token hết hạn (401)
    if (response.status === 401 && !options.skipAuth && retry) {
        const refreshed = await refreshAccessToken(true);
        if (refreshed) {
            return apiFetch(path, options, false);
        }
    }

    if (!response.ok || (payload && payload.success === false)) {
        const errMsg = payload?.message || `HTTP ${response.status}`;
        logProtocol('ERROR', `Lỗi ${path}`, errMsg);
        throw new Error(errMsg);
    }

    return payload?.data !== undefined ? payload.data : payload;
}

// 1. Đăng ký tài khoản: POST /api/v1/auth/register
async function handleRegister(event) {
    event.preventDefault();
    hideAuthAlert();

    const body = {
        username: document.getElementById('regUsername').value.trim(),
        email: document.getElementById('regEmail').value.trim(),
        fullName: document.getElementById('regFullName').value.trim(),
        password: document.getElementById('regPassword').value
    };

    try {
        const userDto = await apiFetch('/api/v1/auth/register', {
            method: 'POST',
            skipAuth: true,
            body: JSON.stringify(body)
        });
        logProtocol('AUTH', 'Đăng ký thành công', `User ID: ${userDto.id} (${userDto.username})`);
        showAuthAlert('Đăng ký thành công! Hãy đăng nhập vào hệ thống.', false);
        document.getElementById('loginUsername').value = body.username;
        switchAuthTab('login');
    } catch (err) {
        showAuthAlert(err.message, true);
    }
}

// 2. Đăng nhập: POST /api/v1/auth/login
async function handleLogin(event) {
    event.preventDefault();
    hideAuthAlert();

    const body = {
        username: document.getElementById('loginUsername').value.trim(),
        password: document.getElementById('loginPassword').value
    };

    try {
        const authData = await apiFetch('/api/v1/auth/login', {
            method: 'POST',
            skipAuth: true,
            body: JSON.stringify(body)
        });

        saveAuthSession(authData);
        onAuthenticatedSuccess();
    } catch (err) {
        showAuthAlert(err.message, true);
    }
}

// 3. Xoay vòng Refresh Token: POST /api/v1/auth/refresh
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
        logProtocol('AUTH', 'Refresh Token Rotation thành công', 'Đã cấp cặp Access + Refresh Token mới và thu hồi token cũ.');

        // Kết nối lại STOMP với token mới
        connectWebSocket();
        return true;
    } catch (err) {
        if (!silent) alert('Không thể xoay vòng Refresh Token: ' + err.message);
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
    updateTokenInspectorUI(authData.expiresIn);
}

function updateTokenInspectorUI(expiresIn) {
    const at = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN) || 'Chưa có';
    const rt = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN) || 'Chưa có';
    document.getElementById('accessTokenPreview').textContent = at;
    document.getElementById('refreshTokenPreview').textContent = rt;
    if (expiresIn) {
        document.getElementById('accessTokenExp').textContent = `TTL: ${expiresIn}s`;
    }
}

async function onAuthenticatedSuccess() {
    document.getElementById('authView').classList.add('hidden');
    document.getElementById('userHeaderInfo').classList.remove('hidden');
    document.getElementById('userHeaderInfo').classList.add('flex');

    updateTokenInspectorUI();

    // Nạp hồ sơ người dùng hiện tại từ GET /api/v1/users/me
    try {
        const me = await apiFetch('/api/v1/users/me');
        currentUser = me;
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(me));
    } catch (e) {
        // Dùng thông tin user đã lưu nếu có
    }

    document.getElementById('currentUserName').textContent = currentUser?.fullName || currentUser?.username || 'User';
    document.getElementById('currentUserHandle').textContent = `@${currentUser?.username} (ID: #${currentUser?.id})`;

    await loadRooms();
    connectWebSocket();
}

function logout() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.USER);
    currentUser = null;
    activeRoom = null;

    document.getElementById('authView').classList.remove('hidden');
    document.getElementById('userHeaderInfo').classList.add('hidden');
    updateWsStatus(false);
    updateTokenInspectorUI();
}

// 4. Kết nối WebSocket STOMP qua SockJS (/ws-chat) đính kèm JWT Header
function connectWebSocket() {
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (!accessToken) return;

    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }

    const wsUrl = `${getApiBase()}/ws-chat?access_token=${encodeURIComponent(accessToken)}`;
    logProtocol('STOMP', 'Khởi tạo kết nối SockJS', wsUrl);

    const socket = new SockJS(wsUrl);
    stompClient = Stomp.over(socket);
    // Tắt debug rác trên console, hiển thị qua bảng STOMP Inspector
    stompClient.debug = null;

    const connectHeaders = {
        Authorization: `Bearer ${accessToken}`,
        access_token: accessToken
    };

    stompClient.connect(
        connectHeaders,
        (frame) => {
            updateWsStatus(true);
            logProtocol('STOMP', 'CONNECTED thành công', `Frame: ${frame.command}`);
            if (activeRoom) {
                subscribeToRoomTopic(activeRoom.id);
            }
        },
        (error) => {
            updateWsStatus(false);
            logProtocol('ERROR', 'STOMP Disconnected / Error', String(error));
        }
    );
}

function updateWsStatus(connected) {
    const dot = document.getElementById('wsStatusDot');
    const text = document.getElementById('wsStatusText');
    if (connected) {
        dot.className = 'w-2 h-2 rounded-full bg-emerald-400 animate-pulse';
        text.textContent = 'WS: Connected (/ws-chat)';
    } else {
        dot.className = 'w-2 h-2 rounded-full bg-rose-500';
        text.textContent = 'WS: Disconnected';
    }
}

// 5. Subscribe vào kênh phòng chat: /topic/rooms.{roomId}
function subscribeToRoomTopic(roomId) {
    if (!stompClient || !stompClient.connected) return;

    if (currentRoomSubscription) {
        currentRoomSubscription.unsubscribe();
        currentRoomSubscription = null;
    }

    const destination = `/topic/rooms.${roomId}`;
    logProtocol('STOMP', `SUBSCRIBE ${destination}`, 'JwtStompChannelInterceptor kiểm tra quyền thành viên...');

    currentRoomSubscription = stompClient.subscribe(destination, (stompMessage) => {
        try {
            const body = JSON.parse(stompMessage.body);
            const msgDto = body?.data !== undefined ? body.data : body;
            logProtocol('STOMP', `MESSAGE từ ${destination}`, JSON.stringify(msgDto));
            appendMessageBubble(msgDto);
            loadRooms(); // Cập nhật lastMessage trên sidebar
        } catch (err) {
            console.error('Lỗi parse STOMP message:', err);
        }
    });

    document.getElementById('activeRoomTopicBadge').textContent = destination;
    document.getElementById('activeRoomBadges').classList.remove('hidden');
    document.getElementById('activeRoomBadges').classList.add('flex');
}

// 6. Gửi tin nhắn thời gian thực qua STOMP: /app/chat.sendMessage
function sendRealtimeMessage(event) {
    event.preventDefault();
    if (!activeRoom) return;

    const input = document.getElementById('messageInput');
    const content = input.value.trim();
    const messageType = document.getElementById('messageTypeSelect').value || 'TEXT';
    if (!content) return;

    if (!stompClient || !stompClient.connected) {
        alert('WebSocket STOMP chưa kết nối! Hệ thống đang thử kết nối lại...');
        connectWebSocket();
        return;
    }

    const payload = {
        roomId: activeRoom.id,
        conversationId: activeRoom.id,
        content: content,
        messageType: messageType
    };

    logProtocol('STOMP', 'SEND /app/chat.sendMessage', JSON.stringify(payload));
    stompClient.send('/app/chat.sendMessage', {}, JSON.stringify(payload));
    input.value = '';
}

// 7. Truy vấn danh sách phòng chat: GET /api/v1/rooms
async function loadRooms() {
    try {
        const rooms = await apiFetch('/api/v1/rooms');
        renderRoomList(rooms || []);
    } catch (err) {
        console.error('Không thể tải danh sách phòng:', err);
    }
}

function renderRoomList(rooms) {
    const container = document.getElementById('roomListContainer');
    if (!rooms.length) {
        container.innerHTML = `<div class="p-6 text-center text-xs text-slate-500">Chưa có phòng chat nào. Hãy tìm kiếm người dùng để tạo phòng!</div>`;
        return;
    }

    container.innerHTML = rooms.map(room => {
        const isActive = activeRoom && activeRoom.id === room.id;
        const roomName = formatRoomDisplayName(room);
        const lastMsg = room.lastMessage
            ? `${escapeHtml(room.lastMessage.senderUsername || '')}: ${escapeHtml(room.lastMessage.content || '')}`
            : 'Chưa có tin nhắn';
        const unread = room.unreadCount || 0;

        return `
            <div onclick='selectRoom(${JSON.stringify(room).replace(/'/g, "&#39;")})'
                class="p-3.5 cursor-pointer transition hover:bg-slate-800/70 ${isActive ? 'bg-indigo-950/40 border-l-4 border-indigo-500' : ''}">
                <div class="flex items-center justify-between mb-1">
                    <span class="text-xs font-bold text-slate-100 truncate">${escapeHtml(roomName)}</span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700">${room.type || 'ROOM'} #${room.id}</span>
                </div>
                <div class="flex items-center justify-between">
                    <p class="text-[11px] text-slate-400 truncate pr-2">${lastMsg}</p>
                    ${unread > 0 ? `<span class="bg-indigo-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full">${unread}</span>` : ''}
                </div>
            </div>
        `;
    }).join('');
}

function formatRoomDisplayName(room) {
    if (room.name) return room.name;
    if (Array.isArray(room.participants) && currentUser) {
        const partner = room.participants.find(p => p.id !== currentUser.id && p.userId !== currentUser.id);
        if (partner) return partner.fullName || partner.username || `Room #${room.id}`;
    }
    return `Phòng #${room.id}`;
}

// 8. Chọn phòng chat -> Tải lịch sử tin nhắn (GET /api/v1/rooms/{roomId}/messages) & Subscribe STOMP
async function selectRoom(room) {
    activeRoom = room;
    document.getElementById('activeRoomTitle').textContent = `${formatRoomDisplayName(room)} (ID: #${room.id})`;
    const participantNames = (room.participants || []).map(p => p.username || p.fullName).filter(Boolean).join(', ');
    document.getElementById('activeRoomMeta').textContent = participantNames
        ? `Thành viên: ${participantNames}`
        : `Loại phòng: ${room.type || 'DIRECT/GROUP'}`;

    document.getElementById('messageInput').disabled = false;
    document.getElementById('sendMessageBtn').disabled = false;

    subscribeToRoomTopic(room.id);
    await loadRoomHistory(room.id);
    loadRooms();
}

async function loadRoomHistory(roomId) {
    const container = document.getElementById('messagesContainer');
    container.innerHTML = `<div class="text-center text-xs text-slate-500 py-8">Đang tải lịch sử tin nhắn...</div>`;

    try {
        const pageData = await apiFetch(`/api/v1/rooms/${roomId}/messages?page=0&size=50`);
        const items = pageData?.content || pageData?.items || (Array.isArray(pageData) ? pageData : []);
        container.innerHTML = '';

        // Sắp xếp tin nhắn từ cũ đến mới để hiển thị thuận chiều đọc
        const sorted = [...items].sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
        if (!sorted.length) {
            container.innerHTML = `<div class="text-center text-xs text-slate-500 py-8">Phòng chưa có tin nhắn nào. Hãy gửi tin nhắn đầu tiên!</div>`;
            return;
        }
        sorted.forEach(msg => appendMessageBubble(msg, false));
        container.scrollTop = container.scrollHeight;
    } catch (err) {
        container.innerHTML = `<div class="text-center text-xs text-rose-400 py-8">Lỗi tải lịch sử: ${escapeHtml(err.message)}</div>`;
    }
}

function appendMessageBubble(msg, autoScroll = true) {
    const container = document.getElementById('messagesContainer');
    // Xóa dòng placeholder nếu có
    if (container.querySelector('.py-8')) {
        container.innerHTML = '';
    }

    const senderId = msg.senderId || msg.sender?.id;
    const senderName = msg.senderUsername || msg.senderFullName || msg.sender?.username || `User #${senderId || '?'}`;
    const isMine = currentUser && (senderId === currentUser.id || msg.senderUsername === currentUser.username);
    const timeStr = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '';

    const wrapper = document.createElement('div');
    wrapper.className = `flex flex-col ${isMine ? 'items-end' : 'items-start'}`;
    wrapper.innerHTML = `
        <span class="text-[10px] text-slate-400 mb-0.5 px-1">${escapeHtml(senderName)} ${timeStr ? '• ' + timeStr : ''}</span>
        <div class="max-w-md px-3.5 py-2 rounded-2xl text-xs leading-relaxed ${
            isMine ? 'bg-indigo-600 text-white rounded-br-none' : 'bg-slate-800 text-slate-100 border border-slate-700 rounded-bl-none'
        }">
            ${escapeHtml(msg.content || '')}
        </div>
    `;
    container.appendChild(wrapper);
    if (autoScroll) {
        container.scrollTop = container.scrollHeight;
    }
}

// 9. Tìm kiếm người dùng (GET /api/v1/users/search?q=) & Tạo phòng 1-1 (POST /api/v1/rooms/direct)
async function searchUsers() {
    const q = document.getElementById('searchUserInput').value.trim();
    const box = document.getElementById('searchResultsBox');
    if (!q) {
        box.classList.add('hidden');
        return;
    }

    try {
        const users = await apiFetch(`/api/v1/users/search?q=${encodeURIComponent(q)}`);
        box.classList.remove('hidden');
        if (!users || !users.length) {
            box.innerHTML = `<div class="p-2.5 text-xs text-slate-400">Không tìm thấy người dùng.</div>`;
            return;
        }

        box.innerHTML = users.map(u => `
            <div class="p-2.5 flex items-center justify-between hover:bg-slate-700/50 transition">
                <div>
                    <div class="text-xs font-semibold text-white">${escapeHtml(u.fullName || u.username)} <span class="text-indigo-400 font-mono">#${u.id}</span></div>
                    <div class="text-[10px] text-slate-400">@${escapeHtml(u.username)} • ${escapeHtml(u.email || '')}</div>
                </div>
                <button onclick="openDirectRoom(${u.id})"
                    class="bg-indigo-600 hover:bg-indigo-500 text-white text-[10px] font-semibold px-2.5 py-1 rounded">
                    Nhắn tin
                </button>
            </div>
        `).join('');
    } catch (err) {
        alert('Lỗi tìm kiếm: ' + err.message);
    }
}

async function openDirectRoom(targetUserId) {
    try {
        const room = await apiFetch('/api/v1/rooms/direct', {
            method: 'POST',
            body: JSON.stringify({ targetUserId })
        });
        document.getElementById('searchResultsBox').classList.add('hidden');
        await loadRooms();
        selectRoom(room);
    } catch (err) {
        alert('Không thể tạo phòng 1-1: ' + err.message);
    }
}

// 10. Tạo phòng chat nhóm: POST /api/v1/rooms/group
function toggleGroupModal(show) {
    const modal = document.getElementById('groupModal');
    if (show) modal.classList.remove('hidden');
    else modal.classList.add('hidden');
}

async function createGroupRoom(event) {
    event.preventDefault();
    const name = document.getElementById('groupNameInput').value.trim();
    const rawIds = document.getElementById('groupMemberIdsInput').value;
    const memberIds = rawIds
        .split(',')
        .map(s => parseInt(s.trim(), 10))
        .filter(n => !isNaN(n));

    try {
        const room = await apiFetch('/api/v1/rooms/group', {
            method: 'POST',
            body: JSON.stringify({ name, memberIds })
        });
        toggleGroupModal(false);
        document.getElementById('groupNameInput').value = '';
        document.getElementById('groupMemberIdsInput').value = '';
        await loadRooms();
        selectRoom(room);
    } catch (err) {
        alert('Lỗi tạo phòng nhóm: ' + err.message);
    }
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

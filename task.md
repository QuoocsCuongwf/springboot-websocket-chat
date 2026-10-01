Backend Auth, REST API & CSDL (PostgreSQL / PLpgSQL)
Nhiệm vụ chính:
Hoàn thiện luồng xác thực JWT (Đăng ký, Đăng nhập, Làm mới Token) dựa trên nhánh feat/auth-register-login-jwt.
Thiết kế và phát triển các REST API quản lý tài khoản, danh sách phòng chat và truy xuất lịch sử tin nhắn.
Quản lý kết nối cơ sở dữ liệu qua tệp database.yml và tối ưu truy vấn bằng các Stored Procedures/Functions bằng PLpgSQL.
Các REST API Endpoints đảm nhận:
HTTP Method
Endpoint
Chức năng chi tiết
Input / Output chính
POST
/api/v1/auth/register
Đăng ký tài khoản người dùng mới
In: RegisterRequest / Out: UserDTO
POST
/api/v1/auth/login
Đăng nhập và trả về JWT Access Token
In: LoginRequest / Out: JwtResponse
POST
/api/v1/auth/refresh
Làm mới Access Token khi hết hạn
In: RefreshTokenRequest / Out: JwtResponse
GET
/api/v1/users/me
Lấy thông tin chi tiết user đang đăng nhập
Headers: Authorization / Out: UserProfileDTO
GET
/api/v1/users/search
Tìm kiếm người dùng theo tên/email (?q=name)
Out: List<UserDTO>
GET
/api/v1/rooms
Lấy danh sách các cuộc trò chuyện của user
Out: List<RoomDTO>
POST
/api/v1/rooms/direct
Tạo hoặc lấy phòng chat 1-1 giữa 2 người
In: targetUserId / Out: RoomDTO
POST
/api/v1/rooms/group
Tạo phòng chat nhóm mới
In: groupName, memberIds / Out: RoomDTO
GET
/api/v1/rooms/{roomId}/messages
Lấy lịch sử tin nhắn (phân trang: page, size)
Out: Page<MessageDTO> (Tối ưu truy vấn bằng PLpgSQL)

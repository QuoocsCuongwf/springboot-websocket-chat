package com.quoocscuongwf.nextchat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoocscuongwf.nextchat.auth.dto.LoginRequest;
import com.quoocscuongwf.nextchat.auth.dto.RefreshTokenRequest;
import com.quoocscuongwf.nextchat.auth.dto.RegisterRequest;
import com.quoocscuongwf.nextchat.room.Message;
import com.quoocscuongwf.nextchat.room.MessageRepository;
import com.quoocscuongwf.nextchat.room.ConversationRepository;
import com.quoocscuongwf.nextchat.room.RoomService;
import com.quoocscuongwf.nextchat.room.dto.CreateGroupRoomRequest;
import com.quoocscuongwf.nextchat.room.dto.DirectRoomRequest;
import com.quoocscuongwf.nextchat.user.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@ActiveProfiles("test")
public class AuthAndRoomIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nextchat_test")
            .withUsername("nextchat")
            .withPassword("nextchat");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("app.security.jwt.secret", () -> "test-only-secret-that-is-at-least-thirty-two-bytes-long");
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String user1Token;
    private static String user1RefreshToken;
    private static Long user1Id;

    private static String user2Token;
    private static Long user2Id;

    private static Long directRoomId;
    private static Long groupRoomId;

    private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String USER1_NAME = "testuser1_" + UNIQUE_SUFFIX;
    private static final String USER1_EMAIL = USER1_NAME + "@example.com";
    private static final String USER2_NAME = "testuser2_" + UNIQUE_SUFFIX;
    private static final String USER2_EMAIL = USER2_NAME + "@example.com";
    private static final String PASSWORD = "StrongPassword123!";

    @Test
    @Order(1)
    void testMigrationAndProtectedEndpoints() throws Exception {
        String messageFunction = jdbcTemplate.queryForObject(
                "SELECT to_regprocedure('get_room_messages(bigint,integer,integer)')::text",
                String.class
        );
        String directRoomFunction = jdbcTemplate.queryForObject(
                "SELECT to_regprocedure('get_or_create_direct_room(bigint,bigint)')::text",
                String.class
        );
        assertThat(messageFunction).isEqualTo("get_room_messages(bigint,integer,integer)");
        assertThat(directRoomFunction).isEqualTo("get_or_create_direct_room(bigint,bigint)");

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(2)
    void testRegisterUser1() throws Exception {
        RegisterRequest request = new RegisterRequest(
                USER1_NAME,
                USER1_EMAIL,
                PASSWORD,
                "Test User One"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(USER1_NAME))
                .andExpect(jsonPath("$.data.email").value(USER1_EMAIL))
                .andExpect(jsonPath("$.data.fullName").value("Test User One"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        user1Id = root.path("data").path("id").asLong();
        assertThat(user1Id).isNotNull();
    }

    @Test
    @Order(3)
    void testDuplicateRegistrationIsRejected() throws Exception {
        RegisterRequest request = new RegisterRequest(
                USER1_NAME,
                USER1_EMAIL,
                PASSWORD,
                "Test User One"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(4)
    void testRegisterUser2() throws Exception {
        RegisterRequest request = new RegisterRequest(
                USER2_NAME,
                USER2_EMAIL,
                PASSWORD,
                "Test User Two"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(USER2_NAME))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        user2Id = root.path("data").path("id").asLong();
        assertThat(user2Id).isNotNull();
    }

    @Test
    @Order(5)
    void testInvalidLoginDoesNotExposeDetails() throws Exception {
        LoginRequest request = new LoginRequest(USER1_NAME, "wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @Order(6)
    void testLoginUser1() throws Exception {
        LoginRequest request = new LoginRequest(USER1_NAME, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        user1Token = root.path("data").path("accessToken").asText();
        user1RefreshToken = root.path("data").path("refreshToken").asText();
        assertThat(user1Token).isNotBlank();
        assertThat(user1RefreshToken).isNotBlank();
        String persistedToken = jdbcTemplate.queryForObject("SELECT token FROM refresh_tokens", String.class);
        assertThat(persistedToken).isNotEqualTo(user1RefreshToken);
    }

    @Test
    @Order(7)
    void testLoginUser2() throws Exception {
        LoginRequest request = new LoginRequest(USER2_NAME, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        user2Token = root.path("data").path("accessToken").asText();
        assertThat(user2Token).isNotBlank();
    }

    @Test
    @Order(8)
    void testRefreshToken() throws Exception {
        String previousRefreshToken = user1RefreshToken;
        RefreshTokenRequest request = new RefreshTokenRequest(user1RefreshToken);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String newAccessToken = root.path("data").path("accessToken").asText();
        String newRefreshToken = root.path("data").path("refreshToken").asText();
        assertThat(newAccessToken).isNotBlank();
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(previousRefreshToken);
        user1Token = newAccessToken; // Use refreshed token for subsequent tests
        user1RefreshToken = newRefreshToken;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(previousRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(9)
    void testGetCurrentUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user1Id))
                .andExpect(jsonPath("$.data.username").value(USER1_NAME))
                .andExpect(jsonPath("$.data.email").value(USER1_EMAIL))
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    @Test
    @Order(10)
    void testSearchUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                        .param("q", USER2_NAME)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].username").value(USER2_NAME));
    }

    @Test
    @Order(11)
    void testCreateOrGetDirectRoom() throws Exception {
        DirectRoomRequest request = new DirectRoomRequest(user2Id);

        MvcResult result = mockMvc.perform(post("/api/v1/rooms/direct")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("PRIVATE"))
                .andExpect(jsonPath("$.data.participants").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        directRoomId = root.path("data").path("id").asLong();
        assertThat(directRoomId).isNotNull();

        // Idempotency check: Calling again returns the exact same room
        mockMvc.perform(post("/api/v1/rooms/direct")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(directRoomId));
    }

    @Test
    @Order(12)
    void testCreateGroupRoom() throws Exception {
        CreateGroupRoomRequest request = new CreateGroupRoomRequest(
                "Study Group " + UNIQUE_SUFFIX,
                List.of(user2Id)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/rooms/group")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("GROUP"))
                .andExpect(jsonPath("$.data.name").value("Study Group " + UNIQUE_SUFFIX))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        groupRoomId = root.path("data").path("id").asLong();
        assertThat(groupRoomId).isNotNull();
    }

    @Test
    @Order(13)
    void testGetUserRooms() throws Exception {
        mockMvc.perform(get("/api/v1/rooms")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(14)
    void testGetRoomMessagesWithPLpgSQL() throws Exception {
        // Insert sample messages into the direct room
        var conv = conversationRepository.findById(directRoomId).orElseThrow();
        var sender1 = userRepository.findById(user1Id).orElseThrow();
        var sender2 = userRepository.findById(user2Id).orElseThrow();

        messageRepository.save(new Message(conv, sender1, "TEXT", "Hello from user 1"));
        messageRepository.save(new Message(conv, sender2, "TEXT", "Hi back from user 2!"));

        mockMvc.perform(get("/api/v1/rooms/" + directRoomId + "/messages")
                        .header("Authorization", "Bearer " + user1Token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].senderUsername").isString())
                .andExpect(jsonPath("$.data.content[0].content").isString());

        mockMvc.perform(get("/api/v1/rooms/" + directRoomId + "/messages")
                        .header("Authorization", "Bearer " + user1Token)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page.totalElements").value(2));

        mockMvc.perform(get("/api/v1/rooms/" + directRoomId + "/messages")
                        .header("Authorization", "Bearer " + user1Token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(15)
    void testInvalidGroupMembersAreRejected() throws Exception {
        CreateGroupRoomRequest request = new CreateGroupRoomRequest(
                "Invalid Group " + UNIQUE_SUFFIX,
                List.of(999_999L)
        );

        mockMvc.perform(post("/api/v1/rooms/group")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(16)
    void testNonMemberCannotReadRoomHistory() throws Exception {
        String outsiderName = "outsider_" + UNIQUE_SUFFIX;
        Long outsiderId = registerUser(outsiderName, "Outsider");
        String outsiderToken = login(outsiderName);
        assertThat(outsiderId).isNotNull();

        mockMvc.perform(get("/api/v1/rooms/" + directRoomId + "/messages")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(17)
    void testConcurrentDirectRoomCreationIsIdempotent() throws Exception {
        Long firstUserId = registerUser("concurrent_a_" + UNIQUE_SUFFIX, "Concurrent A");
        Long secondUserId = registerUser("concurrent_b_" + UNIQUE_SUFFIX, "Concurrent B");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return roomService.getOrCreateDirectRoom(firstUserId, secondUserId).id();
                }));
            }
            ready.await();
            start.countDown();

            Long firstRoomId = results.get(0).get();
            Long secondRoomId = results.get(1).get();
            assertThat(firstRoomId).isEqualTo(secondRoomId);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long registerUser(String username, String fullName) throws Exception {
        RegisterRequest request = new RegisterRequest(
                username,
                username + "@example.com",
                PASSWORD,
                fullName
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}

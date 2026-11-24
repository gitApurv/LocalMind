package com.localmind.auth.controller;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.localmind.auth.dto.LoginRequest;
import com.localmind.auth.dto.RegisterRequest;
import com.localmind.auth.dto.UpdateProfileRequest;
import com.localmind.auth.repository.RefreshTokenRepository;
import com.localmind.auth.service.RefreshTokenCleanupService;
import com.localmind.support.OfflineAiStack;
import com.localmind.user.entity.User;
import com.localmind.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * MockMvc tests for {@link AuthController}: register and password validation, login with refresh cookie, token
 * rotation, reuse detection and grace window, logout, profile read and update, and 401/400 ProblemDetails. Boots the
 * full context on the test profile (H2).
 */
@SpringBootTest
@OfflineAiStack
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenCleanupService refreshTokenCleanupService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register should create user and return 201 Created")
    void registerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest("test@localmind.ai", "secretPass123", "Test User");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.email", is("test@localmind.ai")))
                .andExpect(jsonPath("$.fullName", is("Test User")));

        assertTrue(userRepository.existsByEmail("test@localmind.ai"));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email should return 409 Conflict")
    void registerDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate@localmind.ai", "secretPass123", "Test User");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/auth/register with invalid data should return 400 Bad Request")
    void registerInvalidData() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "123", "");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register with a 7-character password should return 400 Bad Request")
    void registerPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("short@localmind.ai", "1234567", "Short Password");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("password: Password must be between 8 and 72 characters")));

        assertFalse(userRepository.existsByEmail("short@localmind.ai"));
    }

    @Test
    @DisplayName("POST /api/auth/register with an 8-character password should return 201 Created")
    void registerPasswordMinimumLength() throws Exception {
        RegisterRequest request = new RegisterRequest("eight@localmind.ai", "12345678", "Eight Chars");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/auth/login should return JWT access token and HttpOnly refresh cookie")
    void loginSuccess() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("login@localmind.ai", "password123", "Login User");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq))).andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login@localmind.ai", "password123");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq))).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.email", is("login@localmind.ai"))).andExpect(header().exists("Set-Cookie"))
                .andExpect(cookie().exists("refreshToken")).andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password should return 401 Unauthorized")
    void loginInvalidPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("login_fail@localmind.ai", "correctPassword", "Fail User");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq))).andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login_fail@localmind.ai", "wrongPassword");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh should rotate refresh token and issue new JWT")
    void refreshSuccess() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("refresh@localmind.ai", "password123", "Refresh User");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq))).andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("refresh@localmind.ai", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq))).andExpect(status().isOk()).andReturn();

        jakarta.servlet.http.Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("POST /api/auth/logout should delete the refresh token and clear the cookie")
    void logoutSuccess() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("logout@localmind.ai", "password123", "Logout User");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq))).andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("logout@localmind.ai", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq))).andExpect(status().isOk()).andReturn();

        jakarta.servlet.http.Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    @DisplayName("GET /api/auth/me should return current user profile when authenticated")
    void getCurrentUserSuccess() throws Exception {
        User user = saveUser("me@localmind.ai", "Me User");

        mockMvc.perform(get("/api/auth/me").with(asUser(user.getId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("me@localmind.ai"))).andExpect(jsonPath("$.fullName", is("Me User")));
    }

    @Test
    @DisplayName("PATCH /api/auth/me should update fullName and customInstructions")
    void updateProfileSuccess() throws Exception {
        User user = saveUser("update-profile@localmind.ai", "Old Name");
        UpdateProfileRequest updateReq = new UpdateProfileRequest("New Name",
                "Always answer in concise bullet points.");

        mockMvc.perform(patch("/api/auth/me").with(asUser(user.getId())).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("New Name")))
                .andExpect(jsonPath("$.customInstructions", is("Always answer in concise bullet points.")));

        mockMvc.perform(get("/api/auth/me").with(asUser(user.getId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("New Name")))
                .andExpect(jsonPath("$.customInstructions", is("Always answer in concise bullet points.")));
    }

    @Test
    @DisplayName("GET /api/auth/me with a token for a deleted user should return 401, an invalid session")
    void getCurrentUserOfDeletedUserIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(asUser("deleted-user"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("User not found")));
    }

    @Test
    @DisplayName("GET /api/auth/me with an access token from a real login should be accepted")
    void loginAccessTokenIsAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper
                .writeValueAsString(new RegisterRequest("real-token@localmind.ai", "password123", "Real Token"))))
                .andExpect(status().isCreated());
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                        objectMapper.writeValueAsString(new LoginRequest("real-token@localmind.ai", "password123"))))
                .andExpect(status().isOk()).andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken")
                .asString();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("real-token@localmind.ai")));
    }

    @Test
    @DisplayName("POST /api/auth/refresh with reused token should terminate entire session")
    void refreshReuseDetection() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("reuse@localmind.ai", "password123", "Reuse User");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq))).andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("reuse@localmind.ai", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq))).andExpect(status().isOk()).andReturn();

        jakarta.servlet.http.Cookie initialCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(initialCookie);

        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                .andExpect(status().isOk()).andReturn();

        ageRotations();
        mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie)).andExpect(status().isUnauthorized());

        jakarta.servlet.http.Cookie rotatedCookie = firstRefresh.getResponse().getCookie("refreshToken");
        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh twice within the grace window (parallel tabs) should keep the session")
    void refreshWithinGraceWindowKeepsSession() throws Exception {
        jakarta.servlet.http.Cookie initialCookie = registerAndLogin("tabs@localmind.ai");

        MvcResult firstTab = mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                .andExpect(status().isOk()).andReturn();
        MvcResult secondTab = mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                .andExpect(status().isOk()).andReturn();

        mockMvc.perform(post("/api/auth/refresh").cookie(firstTab.getResponse().getCookie("refreshToken")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(secondTab.getResponse().getCookie("refreshToken")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/auth/refresh with a logged-out token should be rejected without ending other sessions")
    void refreshAfterLogoutRejected() throws Exception {
        jakarta.servlet.http.Cookie cookie = registerAndLogin("loggedout@localmind.ai");
        jakarta.servlet.http.Cookie otherSession = login("loggedout@localmind.ai");

        mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid refresh token")));

        mockMvc.perform(post("/api/auth/refresh").cookie(otherSession)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/auth/me without a token should return 401 Unauthorized")
    void getCurrentUserWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoints should return 401 Unauthorized")
    void unauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status", is(401))).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Malformed JSON should return a 400 ProblemDetail with a message")
    void malformedJsonReturnsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/register with an over-long full name should return 400, not a database error")
    void registerOverLongNameRejected() throws Exception {
        RegisterRequest request = new RegisterRequest("long@localmind.ai", "secretPass123", "x".repeat(101));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fullName")));
    }

    @Test
    @DisplayName("Expired-token cleanup keeps live sessions and still detects reuse of a rotated token")
    void cleanupKeepsReuseDetection() throws Exception {
        jakarta.servlet.http.Cookie initialCookie = registerAndLogin("cleanup@localmind.ai");
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                .andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie rotatedCookie = refreshed.getResponse().getCookie("refreshToken");

        assertEquals(0, refreshTokenCleanupService.deleteExpiredTokens());

        ageRotations();
        mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Refresh token reuse detected: session terminated")));
        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token signed with LocalMind's key but issued by someone else should be rejected with 401")
    void tokenFromOtherIssuerRejected() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("someone-else").issuedAt(now)
                .expiresAt(now.plusSeconds(300)).subject("u1").claim("userId", "u1").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login should scope the refresh cookie to /api/auth with HttpOnly and SameSite=Strict")
    void refreshCookieAttributes() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("cookie@localmind.ai", "password123",
                        "Cookie User"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("cookie@localmind.ai", "password123"))))
                .andExpect(status().isOk()).andExpect(cookie().path("refreshToken", "/api/auth"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().sameSite("refreshToken", "Strict"));
    }

    @Test
    @DisplayName("POST /api/auth/register should reject a password over 72 UTF-8 bytes even if under 72 characters")
    void registerPasswordOver72BytesRejected() throws Exception {
        RegisterRequest request = new RegisterRequest("bytes@localmind.ai", "\u00e9".repeat(40), "Bytes User");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Password must not exceed 72 bytes")));
    }

    private User saveUser(String email, String fullName) {
        return userRepository.save(User.builder().email(email).passwordHash("unused-hash").fullName(fullName).build());
    }

    private static RequestPostProcessor asUser(String userId) {
        return jwt().jwt(token -> token.subject(userId).claim("userId", userId));
    }

    private jakarta.servlet.http.Cookie registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, "password123", "Tab User"))))
                .andExpect(status().isCreated());
        return login(email);
    }

    private jakarta.servlet.http.Cookie login(String email) throws Exception {
        MvcResult login = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk()).andReturn();
        return login.getResponse().getCookie("refreshToken");
    }

    private void ageRotations() {
        refreshTokenRepository.findAll().stream().filter(token -> token.getRotatedAt() != null).forEach(token -> {
            token.setRotatedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)));
            refreshTokenRepository.save(token);
        });
    }
}

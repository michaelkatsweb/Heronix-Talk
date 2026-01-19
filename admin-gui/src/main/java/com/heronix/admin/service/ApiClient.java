package com.heronix.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heronix.admin.model.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP API client for communicating with the Heronix Talk server.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static ApiClient instance;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String baseUrl = "http://localhost:9680";
    private String sessionToken;
    private UserDTO currentUser;

    private ApiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public UserDTO getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return sessionToken != null && currentUser != null;
    }

    // ==================== Authentication ====================

    public AuthResponse login(String username, String password) throws IOException {
        String json = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/api/auth/login")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            AuthResponse authResponse = objectMapper.readValue(body, AuthResponse.class);

            if (authResponse.isSuccess()) {
                this.sessionToken = authResponse.getSessionToken();
                this.currentUser = authResponse.getUser();
                log.info("Login successful for user: {}", username);
            }

            return authResponse;
        }
    }

    public void logout() {
        if (sessionToken != null) {
            try {
                Request request = new Request.Builder()
                        .url(baseUrl + "/api/auth/logout")
                        .post(RequestBody.create("", MediaType.parse("application/json")))
                        .addHeader("X-Session-Token", sessionToken)
                        .build();
                httpClient.newCall(request).execute().close();
            } catch (IOException e) {
                log.warn("Error during logout", e);
            }
        }
        this.sessionToken = null;
        this.currentUser = null;
    }

    // ==================== Dashboard ====================

    public DashboardDTO getDashboard() throws IOException {
        return get("/api/admin/dashboard", DashboardDTO.class);
    }

    public SystemHealthDTO getSystemHealth() throws IOException {
        return get("/api/admin/health", SystemHealthDTO.class);
    }

    // ==================== Users ====================

    public List<UserDTO> getAllUsers() throws IOException {
        return getList("/api/admin/users", new TypeReference<List<UserDTO>>() {});
    }

    public void lockUser(Long userId) throws IOException {
        post("/api/admin/users/" + userId + "/lock", null);
    }

    public void unlockUser(Long userId) throws IOException {
        post("/api/admin/users/" + userId + "/unlock", null);
    }

    public String resetPassword(Long userId, boolean generateRandom) throws IOException {
        Map<String, Object> body = Map.of("generateRandom", generateRandom);
        Map<String, String> response = post("/api/admin/users/" + userId + "/reset-password", body,
                new TypeReference<Map<String, String>>() {});
        return response.get("temporaryPassword");
    }

    public void updateUserRole(Long userId, String roleName) throws IOException {
        put("/api/admin/users/" + userId + "/role", Map.of("role", roleName));
    }

    public void resetUserPassword(Long userId, String newPassword) throws IOException {
        post("/api/admin/users/" + userId + "/reset-password", Map.of("password", newPassword));
    }

    // ==================== Roles ====================

    public List<RoleDTO> getAllRoles() throws IOException {
        return getList("/api/admin/roles", new TypeReference<List<RoleDTO>>() {});
    }

    public RoleDTO getRole(Long id) throws IOException {
        return get("/api/admin/roles/" + id, RoleDTO.class);
    }

    public RoleDTO createRole(RoleDTO role) throws IOException {
        return post("/api/admin/roles", role, RoleDTO.class);
    }

    public RoleDTO updateRole(Long id, RoleDTO role) throws IOException {
        return put("/api/admin/roles/" + id, role, RoleDTO.class);
    }

    public void deleteRole(Long id) throws IOException {
        delete("/api/admin/roles/" + id);
    }

    // ==================== System Config ====================

    public List<SystemConfigDTO> getAllSystemConfigs() throws IOException {
        return getList("/api/admin/config", new TypeReference<List<SystemConfigDTO>>() {});
    }

    public SystemConfigDTO createSystemConfig(SystemConfigDTO config) throws IOException {
        return post("/api/admin/config", config, SystemConfigDTO.class);
    }

    public SystemConfigDTO updateSystemConfig(Long id, SystemConfigDTO config) throws IOException {
        return put("/api/admin/config/" + id, config, SystemConfigDTO.class);
    }

    public void deleteSystemConfig(Long id) throws IOException {
        delete("/api/admin/config/" + id);
    }

    public List<SystemConfigDTO> getConfigsByCategory(String category) throws IOException {
        return getList("/api/admin/config/category/" + category, new TypeReference<List<SystemConfigDTO>>() {});
    }

    public List<String> getConfigCategories() throws IOException {
        return getList("/api/admin/config/categories", new TypeReference<List<String>>() {});
    }

    public SystemConfigDTO updateConfig(String key, String value) throws IOException {
        return put("/api/admin/config/" + key, Map.of("value", value), SystemConfigDTO.class);
    }

    // ==================== Security Policy ====================

    public SecurityPolicyDTO getSecurityPolicy() throws IOException {
        return get("/api/admin/security/policy", SecurityPolicyDTO.class);
    }

    public SecurityPolicyDTO updateSecurityPolicy(SecurityPolicyDTO policy) throws IOException {
        return put("/api/admin/security/policy", policy, SecurityPolicyDTO.class);
    }

    // ==================== Network Config ====================

    public NetworkConfigDTO getNetworkConfig() throws IOException {
        return get("/api/admin/network/config", NetworkConfigDTO.class);
    }

    public NetworkConfigDTO updateNetworkConfig(NetworkConfigDTO config) throws IOException {
        return put("/api/admin/network/config", config, NetworkConfigDTO.class);
    }

    public Map<String, Object> testConnectivity(String url) throws IOException {
        return post("/api/admin/network/test-connectivity", Map.of("url", url),
                new TypeReference<Map<String, Object>>() {});
    }

    // ==================== Audit Logs ====================

    public PagedResponse<AuditLogDTO> getAuditLogs(int page, int size) throws IOException {
        return get("/api/admin/audit?page=" + page + "&size=" + size,
                new TypeReference<PagedResponse<AuditLogDTO>>() {});
    }

    public PagedResponse<AuditLogDTO> getAuditLogsByCategory(String category, int page, int size) throws IOException {
        return get("/api/admin/audit/category/" + category + "?page=" + page + "&size=" + size,
                new TypeReference<PagedResponse<AuditLogDTO>>() {});
    }

    public PagedResponse<AuditLogDTO> searchAuditLogs(String query, int page, int size) throws IOException {
        return get("/api/admin/audit/search?q=" + query + "&page=" + page + "&size=" + size,
                new TypeReference<PagedResponse<AuditLogDTO>>() {});
    }

    // ==================== Helper Methods ====================

    private <T> T get(String path, Class<T> responseType) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .get()
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String body = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(body, responseType);
        }
    }

    private <T> T get(String path, TypeReference<T> typeRef) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .get()
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String body = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(body, typeRef);
        }
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> typeRef) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .get()
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String body = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(body, typeRef);
        }
    }

    private void post(String path, Object body) throws IOException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) throws IOException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String responseBody = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(responseBody, responseType);
        }
    }

    private <T> T post(String path, Object body, TypeReference<T> typeRef) throws IOException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String responseBody = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(responseBody, typeRef);
        }
    }

    private void put(String path, Object body) throws IOException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .put(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
        }
    }

    private <T> T put(String path, Object body, Class<T> responseType) throws IOException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .put(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
            String responseBody = response.body() != null ? response.body().string() : "";
            return objectMapper.readValue(responseBody, responseType);
        }
    }

    private void delete(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .delete()
                .addHeader("X-Session-Token", sessionToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            checkResponse(response);
        }
    }

    private void checkResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            String body = response.body() != null ? response.body().string() : "";
            throw new IOException("API request failed: " + response.code() + " - " + body);
        }
    }

    // ==================== Async Methods ====================

    public CompletableFuture<DashboardDTO> getDashboardAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getDashboard();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<UserDTO>> getAllUsersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAllUsers();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<RoleDTO>> getAllRolesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAllRoles();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

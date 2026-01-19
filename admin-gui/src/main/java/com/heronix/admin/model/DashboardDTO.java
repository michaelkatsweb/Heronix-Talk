package com.heronix.admin.model;

import lombok.Data;
import java.util.Map;

@Data
public class DashboardDTO {
    private long totalUsers;
    private long activeUsers;
    private long onlineUsers;
    private long activeSessions;
    private long totalConversations;
    private long totalMessages;
    private long todayMessages;
    private long totalGroups;
    private Map<String, Long> messagesByDay;
    private Map<String, Long> usersByStatus;
    private SystemHealthDTO systemHealth;
}

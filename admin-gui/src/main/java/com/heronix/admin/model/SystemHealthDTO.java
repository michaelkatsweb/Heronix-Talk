package com.heronix.admin.model;

import lombok.Data;

@Data
public class SystemHealthDTO {
    private String status;
    private long uptime;
    private double cpuUsage;
    private long memoryUsed;
    private long memoryMax;
    private long diskUsed;
    private long diskTotal;
    private int activeConnections;
    private String databaseStatus;
    private String cacheStatus;
}

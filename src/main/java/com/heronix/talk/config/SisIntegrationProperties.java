package com.heronix.talk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for SIS (Student Information System) integration.
 * Supports both API-based sync and manual file import.
 */
@Configuration
@ConfigurationProperties(prefix = "heronix.sis")
@Getter
@Setter
public class SisIntegrationProperties {

    private final Sync sync = new Sync();
    private final Api api = new Api();
    private final Import importConfig = new Import();
    private final Database database = new Database();

    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = false;
        private int intervalSeconds = 300;
    }

    @Getter
    @Setter
    public static class Api {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8080";
        private String endpoint = "/api/teachers";
        private String token = "";
        private int timeoutSeconds = 30;
    }

    @Getter
    @Setter
    public static class Import {
        private String directory = "./imports";
        private String processedDirectory = "./imports/processed";
        private boolean autoProcess = false;
    }

    @Getter
    @Setter
    public static class Database {
        private boolean enabled = false;
        private String url = "jdbc:h2:file:./data/heronix;AUTO_SERVER=TRUE";
        private String username = "sa";
        private String password = "";
        private String driverClassName = "org.h2.Driver";
        private String tableName = "teachers";
    }
}

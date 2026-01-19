package com.heronix.talk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Heronix Talk - Offline-First Chat Messaging Server
 *
 * A Microsoft Teams-style communication system designed for educational institutions.
 * Built with the Heronix philosophy: offline-first, no web dependencies, local network capable.
 *
 * This server component handles:
 * - Real-time messaging via WebSocket
 * - Channel and direct message management
 * - User presence and status tracking
 * - Message persistence and history
 * - File attachment handling
 * - Integration with Heronix-Teacher portal
 */
@SpringBootApplication
@EnableScheduling
public class HeronixTalkApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Heronix Talk Server Starting...     ");
        System.out.println("   Offline-First Messaging System      ");
        System.out.println("========================================");

        SpringApplication.run(HeronixTalkApplication.class, args);
    }
}

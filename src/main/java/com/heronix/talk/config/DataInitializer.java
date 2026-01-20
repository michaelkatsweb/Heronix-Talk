package com.heronix.talk.config;

import com.heronix.talk.model.domain.Channel;
import com.heronix.talk.model.domain.NewsItem;
import com.heronix.talk.model.domain.User;
import com.heronix.talk.model.enums.ChannelType;
import com.heronix.talk.model.enums.SyncStatus;
import com.heronix.talk.model.enums.UserRole;
import com.heronix.talk.repository.ChannelRepository;
import com.heronix.talk.repository.NewsItemRepository;
import com.heronix.talk.repository.UserRepository;
import com.heronix.talk.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Initializes default data for the application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final NewsItemRepository newsItemRepository;
    private final ChannelService channelService;
    private final PasswordEncoder passwordEncoder;

    @Value("${heronix.init.create-defaults:true}")
    private boolean createDefaults;

    @Value("${heronix.init.create-test-data:false}")
    private boolean createTestData;

    @Override
    public void run(String... args) {
        if (createDefaults) {
            initializeDefaultChannels();
            initializeAdminUser();
        }

        if (createTestData) {
            initializeTestData();
        }
    }

    private void initializeDefaultChannels() {
        // Create General channel if not exists
        if (!channelRepository.existsByNameAndChannelType("General", ChannelType.PUBLIC)) {
            Channel general = Channel.builder()
                    .name("General")
                    .description("General discussion for all staff members")
                    .channelType(ChannelType.PUBLIC)
                    .icon("chat")
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            channelRepository.save(general);
            log.info("Created default General channel");
        }

        // Create Announcements channel
        if (!channelRepository.existsByNameAndChannelType("Announcements", ChannelType.ANNOUNCEMENT)) {
            Channel announcements = Channel.builder()
                    .name("Announcements")
                    .description("Official school announcements")
                    .channelType(ChannelType.ANNOUNCEMENT)
                    .icon("campaign")
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            channelRepository.save(announcements);
            log.info("Created default Announcements channel");
        }

        // Create Teachers Lounge channel
        if (!channelRepository.existsByNameAndChannelType("Teachers Lounge", ChannelType.PUBLIC)) {
            Channel teachersLounge = Channel.builder()
                    .name("Teachers Lounge")
                    .description("Informal discussion space for teachers")
                    .channelType(ChannelType.PUBLIC)
                    .icon("local_cafe")
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            channelRepository.save(teachersLounge);
            log.info("Created default Teachers Lounge channel");
        }
    }

    private void initializeAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .employeeId("admin")  // Match Heronix-Teacher employeeId for testing
                    .firstName("System")
                    .lastName("Administrator")
                    .email("admin@heronix.local")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .department("Administration")
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            userRepository.save(admin);
            log.info("Created default admin user (username: admin, password: admin123)");
        }
    }

    private void initializeTestData() {
        log.info("Creating test data...");

        // Create test users
        createTestUserIfNotExists("jsmith", "John", "Smith", "Math", UserRole.TEACHER);
        createTestUserIfNotExists("mjohnson", "Mary", "Johnson", "Science", UserRole.TEACHER);
        createTestUserIfNotExists("rbrown", "Robert", "Brown", "English", UserRole.DEPARTMENT_HEAD);
        createTestUserIfNotExists("lwilson", "Lisa", "Wilson", "History", UserRole.TEACHER);
        createTestUserIfNotExists("dthompson", "David", "Thompson", "Administration", UserRole.PRINCIPAL);

        // Create test news items
        if (newsItemRepository.countByActiveTrue() == 0) {
            User admin = userRepository.findByUsername("admin").orElse(null);

            NewsItem news1 = NewsItem.builder()
                    .headline("Welcome to Heronix Talk!")
                    .content("The new communication system is now live. Please update your profile and join channels.")
                    .category("System")
                    .author(admin)
                    .priority(10)
                    .publishedAt(LocalDateTime.now())
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            newsItemRepository.save(news1);

            NewsItem news2 = NewsItem.builder()
                    .headline("Staff Meeting Tomorrow")
                    .content("Reminder: Monthly staff meeting tomorrow at 3 PM in the main auditorium.")
                    .category("Events")
                    .author(admin)
                    .priority(5)
                    .publishedAt(LocalDateTime.now())
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            newsItemRepository.save(news2);

            log.info("Created test news items");
        }

        log.info("Test data initialization complete");
    }

    private void createTestUserIfNotExists(String username, String firstName, String lastName,
                                            String department, UserRole role) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .employeeId("EMP" + username.toUpperCase())
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(username + "@school.local")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .role(role)
                    .department(department)
                    .syncStatus(SyncStatus.LOCAL_ONLY)
                    .build();
            userRepository.save(user);
            log.debug("Created test user: {}", username);
        }
    }
}

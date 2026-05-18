package com.pdasilem.contactwork.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminUserSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminUserSeeder(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String login = trimToNull(environment.getProperty("CONTACTWORK_ADMIN_LOGIN"));
        String password = trimToNull(environment.getProperty("CONTACTWORK_ADMIN_PASSWORD"));
        if (login == null || password == null) {
            log.warn("CONTACTWORK_ADMIN_LOGIN and CONTACTWORK_ADMIN_PASSWORD are not both set; no admin user seeded");
            return;
        }
        AppUser user = appUserRepository.findByLoginIgnoreCase(login)
                .orElseGet(AppUser::new);
        user.setLogin(login.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(trimToNull(environment.getProperty("CONTACTWORK_ADMIN_NAME")) == null
                ? login
                : environment.getProperty("CONTACTWORK_ADMIN_NAME").trim());
        user.setEmail(trimToNull(environment.getProperty("CONTACTWORK_ADMIN_EMAIL")));
        user.setRole(AppRole.ADMIN);
        user.setActive(true);
        appUserRepository.save(user);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

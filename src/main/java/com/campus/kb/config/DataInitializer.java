package com.campus.kb.config;

import com.campus.kb.user.Role;
import com.campus.kb.user.User;
import com.campus.kb.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        String username = properties.getAuth().getAdminUsername().trim().toLowerCase(Locale.ROOT);
        String password = properties.getAuth().getAdminPassword();
        if (password == null || password.isBlank()) {
            log.warn("未配置 ADMIN_PASSWORD，且系统中没有管理员账号，请手动创建");
            return;
        }
        if (userRepository.existsByUsername(username)) {
            log.warn("用户名 {} 已存在但不是管理员，跳过初始化管理员", username);
            return;
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName(properties.getAuth().getAdminDisplayName());
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("已初始化管理员账号：{}（请尽快修改默认密码）", username);
    }
}
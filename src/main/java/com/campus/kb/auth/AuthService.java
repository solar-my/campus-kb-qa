package com.campus.kb.auth;

import com.campus.kb.auth.AuthDtos.AuthResponse;
import com.campus.kb.auth.AuthDtos.LoginRequest;
import com.campus.kb.auth.AuthDtos.RegisterRequest;
import com.campus.kb.auth.AuthDtos.UserDto;
import com.campus.kb.common.BizException;
import com.campus.kb.config.AppProperties;
import com.campus.kb.user.Role;
import com.campus.kb.user.User;
import com.campus.kb.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    public AuthResponse register(RegisterRequest request) {
        if (!properties.getAuth().isAllowRegistration()) {
            throw new BizException(HttpStatus.FORBIDDEN, "当前系统未开放自主注册，请联系管理员");
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new BizException(HttpStatus.CONFLICT, "用户名已被占用，请换一个");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        String displayName = request.displayName() == null ? null : request.displayName().trim();
        user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
        user.setRole(Role.STUDENT);
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(user), UserDto.from(user));
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return new AuthResponse(jwtService.generate(user), UserDto.from(user));
    }

    public UserDto current(UserPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return UserDto.from(user);
    }

    public void changePassword(UserPrincipal principal, String oldPassword, String newPassword) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BizException("新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
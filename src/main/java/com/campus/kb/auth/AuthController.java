package com.campus.kb.auth;

import com.campus.kb.auth.AuthDtos.AuthResponse;
import com.campus.kb.auth.AuthDtos.ChangePasswordRequest;
import com.campus.kb.auth.AuthDtos.LoginRequest;
import com.campus.kb.auth.AuthDtos.RegisterRequest;
import com.campus.kb.auth.AuthDtos.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.current(principal);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                              @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal, request.oldPassword(), request.newPassword());
        return Map.of("success", true, "message", "密码修改成功");
    }
}
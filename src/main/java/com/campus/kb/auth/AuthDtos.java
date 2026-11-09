package com.campus.kb.auth;

import com.campus.kb.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 128, message = "密码长度需为 8-128 位")
            String password,
            @Size(max = 40, message = "昵称不能超过 40 个字符")
            String displayName) {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 8, max = 128, message = "新密码长度需为 8-128 位")
            String newPassword) {
    }

    public record UserDto(Long id, String username, String displayName, String role) {
        public static UserDto from(User user) {
            return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getRole().name());
        }
    }

    public record AuthResponse(String token, UserDto user) {
    }
}
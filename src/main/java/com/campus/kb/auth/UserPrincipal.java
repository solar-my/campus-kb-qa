package com.campus.kb.auth;

public record UserPrincipal(Long id, String username, String displayName, String role) {
}
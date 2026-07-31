package com.sxw.sxwaiagent.auth.dto;

public record AuthResponse(String token, String refreshToken, UserView user) {
}

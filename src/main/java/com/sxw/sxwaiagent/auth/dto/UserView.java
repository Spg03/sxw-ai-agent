package com.sxw.sxwaiagent.auth.dto;

import com.sxw.sxwaiagent.auth.model.UserAccount;

public record UserView(Long id, String username, String nickname, String role) {

    public static UserView from(UserAccount user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}

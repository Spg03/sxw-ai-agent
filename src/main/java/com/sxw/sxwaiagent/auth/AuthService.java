package com.sxw.sxwaiagent.auth;

import com.sxw.sxwaiagent.auth.dto.AuthResponse;
import com.sxw.sxwaiagent.auth.dto.LoginRequest;
import com.sxw.sxwaiagent.auth.dto.RegisterRequest;
import com.sxw.sxwaiagent.auth.dto.UserView;
import com.sxw.sxwaiagent.auth.model.UserAccount;
import com.sxw.sxwaiagent.auth.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserAccountRepository userAccountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userAccountRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists");
        }
        UserAccount user = UserAccount.create(username, passwordEncoder.encode(request.password()), request.nickname());
        UserAccount saved = userAccountRepository.save(user);
        return new AuthResponse(jwtTokenService.generateToken(saved.getUsername()), UserView.from(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByUsername(request.username().trim())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("invalid username or password");
        }
        return new AuthResponse(jwtTokenService.generateToken(user.getUsername()), UserView.from(user));
    }

    @Transactional(readOnly = true)
    public UserView me(String username) {
        return userAccountRepository.findByUsername(username)
                .map(UserView::from)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }
}

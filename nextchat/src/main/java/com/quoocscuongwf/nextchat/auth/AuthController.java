package com.quoocscuongwf.nextchat.auth;

import com.quoocscuongwf.nextchat.security.JwtService;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import com.quoocscuongwf.nextchat.user.RoleRepository;
import com.quoocscuongwf.nextchat.auth.dto.LoginRequest;
import com.quoocscuongwf.nextchat.auth.dto.RegisterRequest;
import com.quoocscuongwf.nextchat.auth.dto.TokenResponse;
import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService users;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleRepository roleRepository;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsService users,
                          UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RoleRepository roleRepository) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.roleRepository = roleRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())
                || userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
        User user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()), request.fullName());
        user.addRole(roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER is not configured")));
        userRepository.save(user);
        return login(new LoginRequest(request.username(), request.password()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        return ApiResponse.success("Login successful",
                new TokenResponse(jwtService.generateToken(users.loadUserByUsername(request.username()))));
    }
}

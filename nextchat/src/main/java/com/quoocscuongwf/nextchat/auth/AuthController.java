package com.quoocscuongwf.nextchat.auth;

import com.quoocscuongwf.nextchat.auth.dto.JwtResponse;
import com.quoocscuongwf.nextchat.auth.dto.LoginRequest;
import com.quoocscuongwf.nextchat.auth.dto.RefreshTokenRequest;
import com.quoocscuongwf.nextchat.auth.dto.RegisterRequest;
import com.quoocscuongwf.nextchat.common.dto.ApiResponse;
import com.quoocscuongwf.nextchat.common.security.JwtService;
import com.quoocscuongwf.nextchat.user.Role;
import com.quoocscuongwf.nextchat.user.RoleRepository;
import com.quoocscuongwf.nextchat.user.User;
import com.quoocscuongwf.nextchat.user.UserRepository;
import com.quoocscuongwf.nextchat.user.dto.UserDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RoleRepository roleRepository,
            RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.roleRepository = roleRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName()
        );
        user.addRole(userRole);
        User savedUser = userRepository.save(user);

        return ApiResponse.success("User registered successfully", UserDTO.fromEntity(savedUser));
    }

    @PostMapping("/login")
    public ApiResponse<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .filter(u -> u.isActive() && !u.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtService.generateToken(userDetails);
        RefreshTokenService.RefreshTokenIssue refreshToken = refreshTokenService.createRefreshToken(user);
        long expiresInSeconds = jwtService.getExpiration().toSeconds();

        JwtResponse response = new JwtResponse(
                accessToken,
                refreshToken.rawToken(),
                "Bearer",
                expiresInSeconds,
                UserDTO.fromEntity(user)
        );

        return ApiResponse.success("Login successful", response);
    }

    @PostMapping("/refresh")
    public ApiResponse<JwtResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.RefreshTokenIssue refreshToken = refreshTokenService.rotateRefreshToken(request.refreshToken());
        User user = refreshToken.user();

        var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String newAccessToken = jwtService.generateToken(userDetails);
        long expiresInSeconds = jwtService.getExpiration().toSeconds();

        JwtResponse response = new JwtResponse(
                newAccessToken,
                refreshToken.rawToken(),
                "Bearer",
                expiresInSeconds,
                UserDTO.fromEntity(user)
        );

        return ApiResponse.success("Token refreshed successfully", response);
    }
}

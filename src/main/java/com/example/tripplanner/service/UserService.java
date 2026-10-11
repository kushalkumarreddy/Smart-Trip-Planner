package com.example.tripplanner.service;

import com.example.tripplanner.dto.UserRegisterDto;
import com.example.tripplanner.dto.UserResponseDto;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );
    }

    @Transactional
    public User registerUser(UserRegisterDto registerDto) {
        if (userRepository.existsByEmail(registerDto.getEmail())) {
            throw new BadRequestException("An account with this email address already exists.");
        }

        User user = new User();
        user.setName(registerDto.getName().trim());
        user.setEmail(registerDto.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        user.setRole("ROLE_USER");

        return userRepository.save(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
    }

    public UserResponseDto mapToResponseDto(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    @Transactional
    public User getOrCreateDemoUser() {
        return userRepository.findByEmail("traveler@example.com")
                .orElseGet(() -> {
                    User demoUser = new User();
                    demoUser.setName("Alex Wanderer");
                    demoUser.setEmail("traveler@example.com");
                    demoUser.setPassword(passwordEncoder.encode("travel123"));
                    demoUser.setRole("ROLE_USER");
                    return userRepository.save(demoUser);
                });
    }
}

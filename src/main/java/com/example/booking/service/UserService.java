package com.example.booking.service;

import com.example.booking.dto.UserRequest;
import com.example.booking.dto.UserResponse;
import com.example.booking.model.User;
import com.example.booking.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    /* 🔹 Mapper */
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getFullName()
        );
    }

    /* CREATE */
    public UserResponse createUser(@Valid UserRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .role(request.getRole() != null ? request.getRole() : "ROLE_USER") // valeur par défaut
                .fullName(request.getFullName())
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    /* READ - all */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /* READ - by id */
    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("User not found with id " + id));
    }

    /* UPDATE */
    public UserResponse updateUser(Long id, @Valid UserRequest updates) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id " + id));

        existing.setUsername(updates.getUsername());
        existing.setEmail(updates.getEmail());
        existing.setRole(updates.getRole());
        existing.setFullName(updates.getFullName());

        User updated = userRepository.save(existing);
        return toResponse(updated);
    }

    /* DELETE */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NoSuchElementException("User not found with id " + id);
        }
        userRepository.deleteById(id);
    }
}

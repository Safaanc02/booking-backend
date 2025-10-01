package com.example.booking.user.dto;

import com.example.booking.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 120) String password,
        Role role
) {}

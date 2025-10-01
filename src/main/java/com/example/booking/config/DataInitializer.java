package com.example.booking.config;

import com.example.booking.model.User;
import com.example.booking.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserRepository userRepository) {
        return args -> {
            if (userRepository.count() == 0) {

                User admin = User.builder()
                        .username("admin")
                        .role("ROLE_ADMIN")   // 🔹 pas ROLE_admin
                        .email("admin@mail.com")
                        .fullName("Super Admin")
                        .keycloakId(UUID.randomUUID().toString()) // 🔹 provisoire
                        .enabled(true)
                        .build();

                User pro = User.builder()
                        .username("pro1")
                        .role("ROLE_PRO")     // 🔹 pas ROLE_pro
                        .email("pro1@mail.com")
                        .fullName("Propriétaire Salon")
                        .keycloakId(UUID.randomUUID().toString()) // 🔹 provisoire
                        .enabled(true)
                        .build();

                User client = User.builder()
                        .username("client1")
                        .role("ROLE_CLIENT")
                        .email("client1@mail.com")
                        .fullName("Client Test")
                        .keycloakId(UUID.randomUUID().toString()) // 🔹 provisoire
                        .enabled(true)
                        .build();

                userRepository.save(admin);
                userRepository.save(pro);
                userRepository.save(client);

                System.out.println("✅ Utilisateurs initiaux créés : admin / pro1 / client1");
            }
        };
    }
}

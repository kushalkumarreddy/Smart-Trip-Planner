package com.example.tripplanner;

import com.example.tripplanner.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TripPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripPlannerApplication.class, args);
    }

    @Bean
    public CommandLineRunner databaseInitializer(UserService userService) {
        return args -> {
            try {
                userService.getOrCreateDemoUser();
            } catch (Exception e) {
                // Ignore during testing / read-only contexts
            }
        };
    }
}

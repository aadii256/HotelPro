
package com.hotelpro.config;

import com.hotelpro.model.AppUser;
import com.hotelpro.model.Room;
import com.hotelpro.repository.RoomRepository;
import com.hotelpro.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Value("${app.admin.email:admin@hotelpro.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin12345!}")
    private String adminPassword;

    @Bean
    public CommandLineRunner seedDatabase(
            UserRepository userRepository,
            RoomRepository roomRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            // Create default admin if it does not exist
            String normalizedEmail = adminEmail.trim().toLowerCase();

            if (!userRepository.existsByEmail(normalizedEmail)) {
                AppUser admin = new AppUser(
                        "HotelPro Admin",
                        normalizedEmail,
                        passwordEncoder.encode(adminPassword),
                        "ADMIN"
                );

                userRepository.save(admin);
                System.out.println("HotelPro admin account created.");
            }

            // Add sample rooms if no rooms exist
            if (roomRepository.count() == 0) {

                Room standard = new Room();
                standard.name = "Deluxe Standard Room";
                standard.type = "Standard";
                standard.description =
                        "Comfortable room with modern amenities.";
                standard.price = 2500;
                standard.capacity = 2;
                standard.imageUrl =
                        "https://images.unsplash.com/photo-1611892440504-42a792e24d32";
                standard.active = true;

                Room premium = new Room();
                premium.name = "Premium King Room";
                premium.type = "Premium";
                premium.description =
                        "Spacious room with a king-size bed.";
                premium.price = 4500;
                premium.capacity = 3;
                premium.imageUrl =
                        "https://images.unsplash.com/photo-1590490360182-c33d57733427";
                premium.active = true;

                Room suite = new Room();
                suite.name = "Luxury Suite";
                suite.type = "Suite";
                suite.description =
                        "Luxury suite for a relaxing stay.";
                suite.price = 7500;
                suite.capacity = 4;
                suite.imageUrl =
                        "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b";
                suite.active = true;

                roomRepository.save(standard);
                roomRepository.save(premium);
                roomRepository.save(suite);

                System.out.println("HotelPro sample rooms created.");
            }
        };
    }
}

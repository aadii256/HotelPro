
package com.hotelpro.controller;

import com.hotelpro.model.AppUser;
import com.hotelpro.model.Room;
import com.hotelpro.repository.BookingRepository;
import com.hotelpro.repository.RoomRepository;
import com.hotelpro.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public AdminController(
            UserRepository userRepository,
            RoomRepository roomRepository,
            BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // Dashboard statistics
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long totalUsers = userRepository.count();
        long totalRooms = roomRepository.findByActiveTrue().size();
        long totalBookings = bookingRepository.count();

        double totalRevenue = bookingRepository.findAll().stream()
                .filter(booking ->
                        !"CANCELLED".equals(booking.status))
                .filter(booking ->
                        !"COMPLETED".equals(booking.status))
                .mapToDouble(booking -> booking.totalPrice)
                .sum();

        return Map.of(
                "totalUsers", totalUsers,
                "totalRooms", totalRooms,
                "totalBookings", totalBookings,
                "totalRevenue", totalRevenue
        );
    }

    // All users, without password hashes
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.id,
                        "name", user.name == null ? "" : user.name,
                        "email", user.email == null ? "" : user.email,
                        "phone", user.getPhone() == null
                                ? "" : user.getPhone(),
                        "role", user.role == null
                                ? "GUEST" : user.role
                ))
                .toList();
    }

    // All rooms, including inactive rooms
    @GetMapping("/rooms")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }
}

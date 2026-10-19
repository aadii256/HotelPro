
package com.hotelpro.controller;

import com.hotelpro.model.AppUser;
import com.hotelpro.model.Booking;
import com.hotelpro.model.Room;
import com.hotelpro.repository.BookingRepository;
import com.hotelpro.repository.RoomRepository;
import com.hotelpro.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public BookingController(
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    public record BookingRequest(
            @NotNull Long roomId,
            @NotNull @FutureOrPresent LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @Min(1) int guests) {
    }

    public record StatusRequest(String status) {
    }

    public record BookingResponse(
            Long id,
            String guestName,
            String guestEmail,
            String guestPhone,
            Long roomId,
            String roomName,
            String roomType,
            LocalDate checkIn,
            LocalDate checkOut,
            int guests,
            double totalPrice,
            String status,
            String paymentStatus) {
    }

    private BookingResponse toResponse(Booking booking) {
        AppUser user = booking.user;
        Room room = booking.room;

        return new BookingResponse(
                booking.id,
                user == null ? "" : user.name,
                user == null ? "" : user.email,
                user == null || user.getPhone() == null
                        ? "" : user.getPhone(),
                room == null ? null : room.id,
                room == null ? "" : room.name,
                room == null ? "" : room.type,
                booking.checkIn,
                booking.checkOut,
                booking.guests,
                booking.totalPrice,
                booking.status,
                booking.paymentStatus == null
                        ? "PENDING" : booking.paymentStatus
        );
    }

    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in"));
        }

        if (request.checkIn() == null
                || request.checkOut() == null
                || !request.checkOut().isAfter(request.checkIn())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Check-out must be after check-in"
                    ));
        }

        var userResult =
                userRepository.findByEmail(authentication.getName());

        if (userResult.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not found"));
        }

        var roomResult = roomRepository.findById(request.roomId());

        if (roomResult.isEmpty() || !roomResult.get().active) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Room not available"));
        }

        AppUser user = userResult.get();
        Room room = roomResult.get();

        if (request.guests() > room.capacity) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Guest count exceeds room capacity"
                    ));
        }

        boolean overlappingBooking =
                bookingRepository
                        .existsByRoom_IdAndStatusNotAndCheckInLessThanAndCheckOutGreaterThan(
                                room.id,
                                "CANCELLED",
                                request.checkOut(),
                                request.checkIn()
                        );

        if (overlappingBooking) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Room is already booked for those dates"
                    ));
        }

        long nights = ChronoUnit.DAYS.between(
                request.checkIn(), request.checkOut());

        Booking booking = new Booking();
        booking.user = user;
        booking.room = room;
        booking.checkIn = request.checkIn();
        booking.checkOut = request.checkOut();
        booking.guests = request.guests();
        booking.totalPrice = room.price * nights;
        booking.status = "CONFIRMED";
        booking.paymentStatus = "PENDING";

        Booking saved = bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(saved));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> myBookings(
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in"));
        }

        return userRepository.findByEmail(authentication.getName())
                .<ResponseEntity<?>>map(user -> {
                    List<BookingResponse> bookings =
                            bookingRepository
                                    .findByUserOrderByIdDesc(user)
                                    .stream()
                                    .map(this::toResponse)
                                    .toList();

                    return ResponseEntity.ok(bookings);
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "message", "User not found"
                                )));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<BookingResponse> allBookings() {
        return bookingRepository.findAllByOrderByIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Please log in"));
        }

        return bookingRepository.findById(id)
                .<ResponseEntity<?>>map(booking -> {

                    boolean isOwner = booking.user != null
                            && authentication.getName()
                            .equals(booking.user.email);

                    boolean isAdmin = authentication.getAuthorities()
                            .stream()
                            .anyMatch(authority ->
                                    authority.getAuthority()
                                            .equals("ROLE_ADMIN"));

                    if (!isOwner && !isAdmin) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of(
                                        "message",
                                        "You cannot cancel this booking"
                                ));
                    }

                    if ("CANCELLED".equals(booking.status)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of(
                                        "message",
                                        "Booking is already cancelled"
                                ));
                    }

                    if ("COMPLETED".equals(booking.status)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of(
                                        "message",
                                        "Completed bookings cannot be cancelled"
                                ));
                    }

                    booking.status = "CANCELLED";
                    Booking saved = bookingRepository.save(booking);

                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of(
                                        "message", "Booking not found"
                                )));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusRequest request) {

        Set<String> allowedStatuses =
                Set.of("CONFIRMED", "CANCELLED", "COMPLETED");

        if (request == null || request.status() == null
                || !allowedStatuses.contains(request.status())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid booking status"));
        }

        return bookingRepository.findById(id)
                .<ResponseEntity<?>>map(booking -> {
                    booking.status = request.status();
                    Booking saved = bookingRepository.save(booking);
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElseGet(() ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of(
                                        "message", "Booking not found"
                                )));
    }
}


package com.hotelpro.controller;

import com.hotelpro.model.Room;
import com.hotelpro.repository.RoomRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public record RoomRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 80) String type,
            @Size(max = 2000) String description,
            @Positive double price,
            @Min(1) int capacity,
            @Size(max = 1000) String imageUrl
    ) {}

    // Guests see active rooms only.
    @GetMapping
    public List<Room> getRooms() {
        return roomRepository.findByActiveTrue();
    }

    // Admin can see active and inactive rooms.
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoom(@PathVariable Long id) {
        return roomRepository.findById(id)
                .filter(room -> room.active)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Room not found")));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addRoom(
            @Valid @RequestBody RoomRequest request) {

        Room room = new Room();
        applyRequest(room, request);
        room.active = true;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomRepository.save(room));
    }

    // Update room details.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateRoom(
            @PathVariable Long id,
            @Valid @RequestBody RoomRequest request) {

        return roomRepository.findById(id)
                .<ResponseEntity<?>>map(room -> {
                    applyRequest(room, request);
                    return ResponseEntity.ok(roomRepository.save(room));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Room not found")));
    }

    // Restore a previously deactivated room.
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activateRoom(@PathVariable Long id) {
        return roomRepository.findById(id)
                .<ResponseEntity<?>>map(room -> {
                    room.active = true;
                    roomRepository.save(room);
                    return ResponseEntity.ok(
                            Map.of("message", "Room activated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Room not found")));
    }

    // Soft delete: preserve room and booking records.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateRoom(@PathVariable Long id) {
        return roomRepository.findById(id)
                .<ResponseEntity<?>>map(room -> {
                    room.active = false;
                    roomRepository.save(room);
                    return ResponseEntity.ok(
                            Map.of("message", "Room deactivated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Room not found")));
    }

    private void applyRequest(Room room, RoomRequest request) {
        room.name = request.name().trim();
        room.type = request.type().trim();
        room.description = request.description() == null
                ? "" : request.description().trim();
        room.price = request.price();
        room.capacity = request.capacity();
        room.imageUrl = request.imageUrl() == null
                ? "" : request.imageUrl().trim();
    }
}

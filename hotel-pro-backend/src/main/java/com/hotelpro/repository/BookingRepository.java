package com.hotelpro.repository;

import com.hotelpro.model.Booking;
import com.hotelpro.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDate;


public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserOrderByIdDesc(AppUser user);

    List<Booking> findAllByOrderByIdDesc();
    boolean existsByRoom_IdAndStatusNotAndCheckInLessThanAndCheckOutGreaterThan(
            Long roomId,
            String excludedStatus,
            LocalDate checkOut,
            LocalDate checkIn
    );
}
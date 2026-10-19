package com.hotelpro.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    public Room room;

    @Column(nullable = false)
    public LocalDate checkIn;

    @Column(nullable = false)
    public LocalDate checkOut;

    @Column(nullable = false)
    public int guests;

    @Column(nullable = false)
    public double totalPrice;

    @Column(nullable = false)
    public String status = "CONFIRMED";

    @Column
    public String razorpayOrderId;

    @Column
    public String razorpayPaymentId;

    @Column(nullable = false)
    public String paymentStatus = "PENDING";

    public Booking() {}
}

package com.hotelpro.controller;

import com.hotelpro.model.Booking;
import com.hotelpro.repository.BookingRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final BookingRepository bookingRepository;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    public PaymentController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    // Create a Razorpay order for a booking
    @PostMapping("/bookings/{bookingId}/order")
    public ResponseEntity<?> createOrder(
            @PathVariable Long bookingId,
            Authentication authentication) {

        try {
            if (authentication == null ||
                    !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Please log in first."));
            }

            Booking booking = bookingRepository.findById(bookingId)
                    .orElse(null);

            if (booking == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Booking not found."));
            }

            // Check that the logged-in user owns this booking.
            String loggedInEmail = authentication.getName();

            if (booking.user == null ||
                    booking.user.email == null ||
                    !booking.user.email.equalsIgnoreCase(loggedInEmail)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "You are not allowed to pay for this booking."
                        ));
            }

            if ("CANCELLED".equalsIgnoreCase(booking.status) ||
                    "COMPLETED".equalsIgnoreCase(booking.status)) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Payment is not available for this booking."
                        ));
            }

            if ("PAID".equalsIgnoreCase(booking.paymentStatus)) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "This booking is already paid."
                        ));
            }

            if (booking.totalPrice <= 0 ||
                    !Double.isFinite(booking.totalPrice)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid booking amount."));
            }

            RazorpayClient razorpay =
                    new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put(
                    "amount",
                    Math.round(booking.totalPrice * 100)
            );
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "booking_" + booking.id);

            Order order = razorpay.orders.create(orderRequest);

            booking.razorpayOrderId = order.get("id").toString();
            booking.paymentStatus = "PENDING";
            bookingRepository.save(booking);

            // Use a Java Map for the response, not JSONObject.
            Map<String, Object> response = new HashMap<>();
            response.put("keyId", keyId);
            response.put("orderId", order.get("id").toString());
            response.put("amount", order.get("amount"));
            response.put("currency", order.get("currency").toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Could not create payment order. Check the backend console."
                    ));
        }
    }

    // Verify the Razorpay payment signature
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestBody Map<String, String> payload,
            Authentication authentication) {

        try {
            if (authentication == null ||
                    !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Please log in first."));
            }

            String orderId = payload.get("razorpay_order_id");
            String paymentId = payload.get("razorpay_payment_id");
            String signature = payload.get("razorpay_signature");

            if (orderId == null ||
                    paymentId == null ||
                    signature == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Missing payment verification details."
                        ));
            }

            Booking booking = bookingRepository.findAll()
                    .stream()
                    .filter(b -> orderId.equals(b.razorpayOrderId))
                    .findFirst()
                    .orElse(null);

            if (booking == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Payment order not found."));
            }

            if (booking.user == null ||
                    booking.user.email == null ||
                    !booking.user.email.equalsIgnoreCase(
                            authentication.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "You cannot verify this booking's payment."
                        ));
            }

            JSONObject verificationData = new JSONObject();
            verificationData.put("razorpay_order_id", orderId);
            verificationData.put("razorpay_payment_id", paymentId);
            verificationData.put("razorpay_signature", signature);

            boolean valid = Utils.verifyPaymentSignature(
                    verificationData,
                    keySecret
            );

            if (!valid) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid payment signature."));
            }

            booking.razorpayPaymentId = paymentId;
            booking.paymentStatus = "PAID";
            bookingRepository.save(booking);

            return ResponseEntity.ok(
                    Map.of("message", "Payment verified successfully.")
            );

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Payment verification failed. Check the backend console."
                    ));
        }
    }
}

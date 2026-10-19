import { useEffect, useState } from "react";
import "./App.css";

const API = "http://localhost:8080/api";

function getToken() {
  return localStorage.getItem("hotelpro_token");
}

async function apiRequest(path, options = {}) {
  const token = getToken();

  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  const text = await response.text();
  let data = {};

  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { message: text };
  }

  if (!response.ok) {
    throw new Error(
        data.message || data.error || text || `Request failed (${response.status})`
    );
  }

  return data;
}

function loadRazorpayScript() {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }

    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

function formatPrice(value) {
  return Number(value || 0).toLocaleString("en-IN");
}

function getRoomName(room) {
  return room?.name || `Room ${room?.id ?? ""}`;
}

export default function App() {
  const [user, setUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem("hotelpro_user") || "null");
    } catch {
      return null;
    }
  });

  const [rooms, setRooms] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [page, setPage] = useState("rooms");
  const [authMode, setAuthMode] = useState("login");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [busyBooking, setBusyBooking] = useState(null);
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [roomIndex, setRoomIndex] = useState(0);

  const [authForm, setAuthForm] = useState({
    name: "",
    email: "",
    phone: "",
    password: "",
    confirmPassword: "",
  });

  const [bookingForm, setBookingForm] = useState({
    checkIn: "",
    checkOut: "",
    guests: 1,
  });

  const isAdmin =
      user?.role === "ADMIN" || user?.role === "ROLE_ADMIN";

  const availableRooms = rooms.filter(
      (room) => room.active !== false && room.status !== "INACTIVE"
  );

  useEffect(() => {
    fetchRooms();
    if (getToken()) fetchBookings();
    // Load initial data once when the application starts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function fetchRooms() {
    try {
      const data = await apiRequest("/rooms");
      const list = Array.isArray(data) ? data : data.content || [];
      setRooms(list);
    } catch (error) {
      setMessage(`Could not load rooms: ${error.message}`);
    }
  }

  async function fetchBookings() {
    if (!getToken()) {
      setBookings([]);
      return;
    }

    try {
      const data = await apiRequest("/bookings/mine");
      setBookings(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error("Could not load bookings:", error.message);
    }
  }

  function updateAuthForm(event) {
    const { name, value } = event.target;
    setAuthForm((previous) => ({ ...previous, [name]: value }));
  }

  async function handleAuth(event) {
    event.preventDefault();
    setMessage("");

    if (
        authMode === "register" &&
        authForm.password !== authForm.confirmPassword
    ) {
      setMessage("Passwords do not match.");
      return;
    }

    if (authMode === "register" && !authForm.phone.trim()) {
      setMessage("Please enter your phone number.");
      return;
    }

    setLoading(true);

    try {
      const path = authMode === "login" ? "/auth/login" : "/auth/register";

      const payload =
          authMode === "login"
              ? { email: authForm.email, password: authForm.password }
              : {
                name: authForm.name,
                email: authForm.email,
                phone: authForm.phone,
                password: authForm.password,
                confirmPassword: authForm.confirmPassword,
              };

      const data = await apiRequest(path, {
        method: "POST",
        body: JSON.stringify(payload),
      });

      const token = data.token || data.accessToken || data.jwt;
      const loggedInUser = data.user || data;

      if (!token) {
        throw new Error(
            "No login token was returned. Check the response from your backend."
        );
      }

      localStorage.setItem("hotelpro_token", token);
      localStorage.setItem("hotelpro_user", JSON.stringify(loggedInUser));
      setUser(loggedInUser);
      setMessage("Welcome to HotelPro.");
      setPage("rooms");

      await fetchRooms();
      await fetchBookings();
    } catch (error) {
      setMessage(error.message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    localStorage.removeItem("hotelpro_token");
    localStorage.removeItem("hotelpro_user");
    setUser(null);
    setBookings([]);
    setSelectedRoom(null);
    setPage("rooms");
    setMessage("You have been logged out.");
  }

  async function handleBooking(event) {
    event.preventDefault();
    setMessage("");

    if (!getToken()) {
      setMessage("Please log in before booking a room.");
      setPage("auth");
      return;
    }

    if (!selectedRoom) return;

    if (
        !bookingForm.checkIn ||
        !bookingForm.checkOut ||
        bookingForm.checkOut <= bookingForm.checkIn
    ) {
      setMessage("Check-out must be after check-in.");
      return;
    }

    setLoading(true);

    try {
      await apiRequest("/bookings", {
        method: "POST",
        body: JSON.stringify({
          roomId: selectedRoom.id,
          checkIn: bookingForm.checkIn,
          checkOut: bookingForm.checkOut,
          guests: Number(bookingForm.guests),
        }),
      });

      setMessage(
          "Booking created. Open My Bookings to complete payment if required."
      );
      setSelectedRoom(null);
      setPage("bookings");
      await fetchBookings();
    } catch (error) {
      setMessage(`Booking failed: ${error.message}`);
    } finally {
      setLoading(false);
    }
  }

  async function cancelBooking(bookingId) {
    if (!window.confirm("Are you sure you want to cancel this booking?")) {
      return;
    }

    try {
      await apiRequest(`/bookings/${bookingId}/cancel`, {
        method: "PATCH",
      });
      setMessage("Booking cancelled.");
      await fetchBookings();
    } catch (error) {
      setMessage(`Cancellation failed: ${error.message}`);
    }
  }

  async function payForBooking(booking) {
    setMessage("");
    setBusyBooking(booking.id);

    try {
      if (!getToken()) {
        throw new Error("Please log in again before making a payment.");
      }

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        throw new Error(
            "Razorpay Checkout could not load. Check your internet connection."
        );
      }

      const order = await apiRequest(
          `/payments/bookings/${booking.id}/order`,
          { method: "POST" }
      );

      if (!order.keyId || !order.orderId || !order.amount) {
        throw new Error(
            "The backend returned incomplete payment order details. Check the PaymentController response."
        );
      }

      const checkout = new window.Razorpay({
        key: order.keyId,
        amount: order.amount,
        currency: order.currency || "INR",
        name: "HotelPro",
        description: `Payment for booking #${booking.id}`,
        order_id: order.orderId,

        handler: async (paymentResponse) => {
          try {
            await apiRequest("/payments/verify", {
              method: "POST",
              body: JSON.stringify({
                razorpay_order_id: paymentResponse.razorpay_order_id,
                razorpay_payment_id: paymentResponse.razorpay_payment_id,
                razorpay_signature: paymentResponse.razorpay_signature,
              }),
            });

            setMessage("Payment verified successfully.");
            await fetchBookings();
          } catch (error) {
            setMessage(
                `Payment verification failed: ${error.message}. Check the payment status before trying again.`
            );
          }
        },

        prefill: {
          name: user?.name || "",
          email: user?.email || "",
          contact: user?.phone || "",
        },

        theme: { color: "#bd9a60" },

        modal: {
          ondismiss: () => {
            setMessage(
                "Payment window closed. The payment has not been confirmed by HotelPro."
            );
          },
        },
      });

      checkout.on("payment.failed", (response) => {
        setMessage(
            `Payment failed: ${
                response.error?.description || "Please try again."
            }`
        );
      });

      checkout.open();
    } catch (error) {
      setMessage(`Could not start payment: ${error.message}`);
    } finally {
      setBusyBooking(null);
    }
  }

  async function updateRoomStatus(room) {
    const newStatus =
        room.active === false || room.status === "INACTIVE"
            ? "ACTIVE"
            : "INACTIVE";

    try {
      await apiRequest(`/admin/rooms/${room.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: newStatus }),
      });

      setMessage("Room status updated.");
      await fetchRooms();
    } catch (error) {
      setMessage(`Could not update room status: ${error.message}`);
    }
  }

  function startBooking(room) {
    setSelectedRoom(room);
    setBookingForm({ checkIn: "", checkOut: "", guests: 1 });
    setPage("rooms");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function changePage(nextPage) {
    setPage(nextPage);
    setSelectedRoom(null);
    if (nextPage === "bookings") fetchBookings();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  const visibleRooms = availableRooms.length
      ? Array.from({ length: Math.min(3, availableRooms.length) }, (_, offset) => {
        return availableRooms[(roomIndex + offset) % availableRooms.length];
      })
      : [];

  return (
      <div className="site-shell">
        <div className="topline">
          <span>AN ELEVATED STAY, EVERY TIME</span>
          <span>Hospitality, thoughtfully reimagined</span>
        </div>

        <header className="navbar">
          <button
              className="brand"
              onClick={() => changePage("rooms")}
              aria-label="HotelPro home"
          >
            <span className="brand-mark">H</span>
            <span className="brand-text">
            HOTELPRO
            <small>STAY BEAUTIFULLY</small>
          </span>
          </button>

          <nav className="nav-links">
            <button
                className={page === "rooms" ? "nav-active" : ""}
                onClick={() => changePage("rooms")}
            >
              Our Rooms
            </button>
            {user && !isAdmin && (
                <button
                    className={page === "bookings" ? "nav-active" : ""}
                    onClick={() => changePage("bookings")}
                >
                  My Bookings
                </button>
            )}
            {isAdmin && (
                <button
                    className={page === "admin" ? "nav-active" : ""}
                    onClick={() => changePage("admin")}
                >
                  Dashboard
                </button>
            )}
          </nav>

          <div className="nav-actions">
            {user ? (
                <>
              <span className="welcome-user">
                {user.name || user.email}
              </span>
                  <button className="button button-outline button-small" onClick={logout}>
                    Log out
                  </button>
                </>
            ) : (
                <button
                    className="button button-dark button-small"
                    onClick={() => changePage("auth")}
                >
                  Sign in
                </button>
            )}
          </div>
        </header>

        {message && (
            <div className="global-alert" role="status">
              <span>{message}</span>
              <button
                  className="alert-close"
                  onClick={() => setMessage("")}
                  aria-label="Dismiss message"
              >
                ×
              </button>
            </div>
        )}

        <main>
          {page === "rooms" && !selectedRoom && (
              <>
                <section className="hero">
                  <div className="hero-image" />
                  <div className="hero-shade" />
                  <div className="hero-content">
                    <p className="eyebrow light-eyebrow">
                      A MORE CONSIDERED KIND OF ESCAPE
                    </p>
                    <h1>Make room for<br />something wonderful.</h1>
                    <p className="hero-description">
                      Thoughtful spaces, effortless comfort, and stays worth
                      remembering.
                    </p>
                    <button
                        className="button button-gold"
                        onClick={() =>
                            document.getElementById("rooms")?.scrollIntoView({
                              behavior: "smooth",
                            })
                        }
                    >
                      Discover our rooms <span aria-hidden="true">↗</span>
                    </button>
                  </div>
                  <div className="hero-caption">
                    <span>YOUR NEXT CHAPTER STARTS HERE</span>
                    <span>01 — HOSPITALITY</span>
                  </div>
                </section>

                <section className="intro-section">
                  <p className="eyebrow">THE HOTELPRO EXPERIENCE</p>
                  <h2>A little more than<br />just a place to stay.</h2>
                  <div className="intro-rule" />
                  <p>
                    Every detail matters. Find a room that feels right, reserve
                    your dates with ease, and let us take care of the rest.
                  </p>
                </section>

                <section className="rooms-section section-wrap" id="rooms">
                  <div className="section-heading">
                    <div>
                      <p className="eyebrow">FIND YOUR SPACE</p>
                      <h2>Rooms &amp; Suites</h2>
                    </div>
                    <p className="section-note">
                      Considered comfort. Timeless details.
                      <br />A stay made yours.
                    </p>
                  </div>

                  {availableRooms.length === 0 ? (
                      <div className="empty-state">
                        <h3>A moment of quiet.</h3>
                        <p>There are no available rooms at the moment. Please check again soon.</p>
                        <button className="button button-outline" onClick={fetchRooms}>
                          Refresh rooms
                        </button>
                      </div>
                  ) : (
                      <>
                        <div className="room-carousel">
                          {visibleRooms.map((room, index) => (
                              <article className="room-card" key={room.id}>
                                <div className="room-image-wrap">
                                  {room.imageUrl ? (
                                      <img src={room.imageUrl} alt={getRoomName(room)} />
                                  ) : (
                                      <div className="room-placeholder">
                                        <span>H</span>
                                        <small>HOTELPRO</small>
                                      </div>
                                  )}
                                  <span className="room-number">
                            {String(availableRooms.indexOf(room) + 1).padStart(2, "0")}
                          </span>
                                </div>

                                <div className="room-info">
                                  <div className="room-type">
                                    {room.type || "THE COLLECTION"}
                                  </div>
                                  <div className="room-title-row">
                                    <h3>{getRoomName(room)}</h3>
                                    <span className="room-capacity">
                              <span aria-hidden="true">♧</span>{" "}
                                      {room.capacity || room.maxGuests || "—"} guests
                            </span>
                                  </div>

                                  <p className="room-description">
                                    {room.description ||
                                        "A welcoming retreat with considered details and all the comforts you need."}
                                  </p>

                                  <div className="room-footer">
                                    <p className="room-price">
                                      <strong>₹{formatPrice(room.price)}</strong>
                                      <span> / night</span>
                                    </p>
                                    <button
                                        className="button button-dark button-small"
                                        onClick={() => startBooking(room)}
                                    >
                                      Reserve <span aria-hidden="true">↗</span>
                                    </button>
                                  </div>
                                </div>
                              </article>
                          ))}
                        </div>

                        {availableRooms.length > 1 && (
                            <div className="carousel-controls">
                      <span>
                        {String((roomIndex % availableRooms.length) + 1).padStart(2, "0")}
                        <span className="carousel-divider"> / </span>
                        {String(availableRooms.length).padStart(2, "0")}
                      </span>
                              <div>
                                <button
                                    className="carousel-arrow"
                                    aria-label="Previous rooms"
                                    onClick={() =>
                                        setRoomIndex(
                                            (roomIndex - 1 + availableRooms.length) %
                                            availableRooms.length
                                        )
                                    }
                                >
                                  ←
                                </button>
                                <button
                                    className="carousel-arrow"
                                    aria-label="Next rooms"
                                    onClick={() =>
                                        setRoomIndex((roomIndex + 1) % availableRooms.length)
                                    }
                                >
                                  →
                                </button>
                              </div>
                            </div>
                        )}
                      </>
                  )}
                </section>

                <section className="booking-section">
                  <div className="booking-copy">
                    <p className="eyebrow light-eyebrow">YOUR STAY, YOUR WAY</p>
                    <h2>Good stays begin<br />with a simple plan.</h2>
                    <p>
                      Choose your room, select your dates, and leave the details
                      to us. Your next comfortable stay is just a few steps away.
                    </p>
                    <button
                        className="button button-gold"
                        onClick={() =>
                            document.getElementById("rooms")?.scrollIntoView({
                              behavior: "smooth",
                            })
                        }
                    >
                      Find your room <span aria-hidden="true">↗</span>
                    </button>
                  </div>
                  <div className="booking-art">
                    <div className="booking-art-frame">
                      <span>H</span>
                      <p>REST WELL.<br />LIVE WELL.</p>
                    </div>
                  </div>
                </section>
              </>
          )}

          {page === "rooms" && selectedRoom && (
              <section className="section-wrap booking-page">
                <button
                    className="text-button back-button"
                    onClick={() => setSelectedRoom(null)}
                >
                  ← Back to rooms
                </button>

                <div className="booking-layout">
                  <div className="booking-copy booking-copy-light">
                    <p className="eyebrow">MAKE IT YOURS</p>
                    <h2>Reserve your<br />little escape.</h2>
                    <p>
                      You selected {getRoomName(selectedRoom)}. Choose your dates
                      and guest count to continue.
                    </p>

                    <div className="selected-room">
                      <span className="selected-room-label">YOUR SELECTION</span>
                      <h3>{getRoomName(selectedRoom)}</h3>
                      <p>{selectedRoom.type || "Guest room"}</p>
                      <p className="selected-price">
                        ₹{formatPrice(selectedRoom.price)}
                        <span> / night</span>
                      </p>
                    </div>
                  </div>

                  <form className="booking-form" onSubmit={handleBooking}>
                    <p className="eyebrow">YOUR RESERVATION</p>
                    <h2>Stay details</h2>
                    <p className="form-subtitle">
                      A few details before we prepare your stay.
                    </p>

                    <div className="form-row">
                      <label>
                        Check-in
                        <input
                            type="date"
                            required
                            min={new Date().toLocaleDateString("en-CA")}
                            value={bookingForm.checkIn}
                            onChange={(event) =>
                                setBookingForm((previous) => ({
                                  ...previous,
                                  checkIn: event.target.value,
                                  checkOut:
                                      previous.checkOut &&
                                      previous.checkOut <= event.target.value
                                          ? ""
                                          : previous.checkOut,
                                }))
                            }
                        />
                      </label>

                      <label>
                        Check-out
                        <input
                            type="date"
                            required
                            min={
                              bookingForm.checkIn
                                  ? bookingForm.checkIn
                                  : new Date().toLocaleDateString("en-CA")
                            }
                            value={bookingForm.checkOut}
                            onChange={(event) =>
                                setBookingForm((previous) => ({
                                  ...previous,
                                  checkOut: event.target.value,
                                }))
                            }
                        />
                      </label>
                    </div>

                    <label>
                      Number of guests
                      <select
                          required
                          value={bookingForm.guests}
                          onChange={(event) =>
                              setBookingForm((previous) => ({
                                ...previous,
                                guests: event.target.value,
                              }))
                          }
                      >
                        {Array.from(
                            {
                              length: Math.max(
                                  1,
                                  Number(selectedRoom.capacity || selectedRoom.maxGuests || 10)
                              ),
                            },
                            (_, index) => index + 1
                        ).map((count) => (
                            <option value={count} key={count}>
                              {count} {count === 1 ? "guest" : "guests"}
                            </option>
                        ))}
                      </select>
                    </label>

                    <div className="price-summary">
                      <span>Room rate</span>
                      <strong>₹{formatPrice(selectedRoom.price)} / night</strong>
                    </div>

                    <p className="form-hint">
                      Your final amount is calculated by the booking service.
                      Payment options appear under My Bookings after reservation.
                    </p>

                    <button className="button button-dark button-full" disabled={loading}>
                      {loading ? "Preparing reservation…" : "Confirm reservation"}
                    </button>
                  </form>
                </div>
              </section>
          )}

          {page === "auth" && (
              <section className="section-wrap auth-page">
                <div className="auth-modal">
                  <div className="modal-brand">
                    <span className="brand-mark">H</span>
                    <span className="brand-text">
                  HOTELPRO
                  <small>STAY BEAUTIFULLY</small>
                </span>
                  </div>
                  <p className="eyebrow">YOUR HOTELPRO ACCOUNT</p>
                  <h2>{authMode === "login" ? "Welcome back." : "A warm welcome."}</h2>
                  <p className="modal-description">
                    {authMode === "login"
                        ? "Sign in to manage your reservations and upcoming stays."
                        : "Create an account to make your next stay a little easier."}
                  </p>

                  <div className="auth-switch">
                    <button
                        className={authMode === "login" ? "auth-switch-active" : ""}
                        onClick={() => setAuthMode("login")}
                    >
                      Sign in
                    </button>
                    <button
                        className={authMode === "register" ? "auth-switch-active" : ""}
                        onClick={() => setAuthMode("register")}
                    >
                      Create account
                    </button>
                  </div>

                  <form className="auth-form" onSubmit={handleAuth}>
                    {authMode === "register" && (
                        <>
                          <label>
                            Full name
                            <input
                                name="name"
                                value={authForm.name}
                                onChange={updateAuthForm}
                                autoComplete="name"
                                required
                            />
                          </label>
                          <label>
                            Phone number
                            <input
                                name="phone"
                                type="tel"
                                value={authForm.phone}
                                onChange={updateAuthForm}
                                autoComplete="tel"
                                required
                            />
                          </label>
                        </>
                    )}

                    <label>
                      Email address
                      <input
                          name="email"
                          type="email"
                          value={authForm.email}
                          onChange={updateAuthForm}
                          autoComplete="email"
                          required
                      />
                    </label>

                    <label>
                      Password
                      <input
                          name="password"
                          type="password"
                          value={authForm.password}
                          onChange={updateAuthForm}
                          autoComplete={
                            authMode === "login" ? "current-password" : "new-password"
                          }
                          required
                      />
                    </label>

                    {authMode === "register" && (
                        <label>
                          Confirm password
                          <input
                              name="confirmPassword"
                              type="password"
                              value={authForm.confirmPassword}
                              onChange={updateAuthForm}
                              autoComplete="new-password"
                              required
                          />
                        </label>
                    )}

                    <button className="button button-dark button-full" disabled={loading}>
                      {loading
                          ? "Please wait…"
                          : authMode === "login"
                              ? "Sign in to HotelPro"
                              : "Create my account"}
                    </button>
                  </form>
                  <button
                      className="text-button auth-back"
                      onClick={() => changePage("rooms")}
                  >
                    ← Return to the rooms
                  </button>
                </div>
              </section>
          )}

          {page === "bookings" && (
              <section className="section-wrap bookings-section">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">YOUR HOTELPRO JOURNEY</p>
                    <h2>My Bookings</h2>
                  </div>
                  <button className="button button-outline button-small" onClick={fetchBookings}>
                    Refresh
                  </button>
                </div>

                {!user ? (
                    <div className="empty-state">
                      <h3>Your stays, all in one place.</h3>
                      <p>Please sign in to view your bookings.</p>
                      <button className="button button-dark" onClick={() => changePage("auth")}>
                        Sign in
                      </button>
                    </div>
                ) : bookings.length === 0 ? (
                    <div className="empty-state">
                      <h3>Your next stay awaits.</h3>
                      <p>You don't have any bookings yet. Find a room to get started.</p>
                      <button className="button button-dark" onClick={() => changePage("rooms")}>
                        Explore rooms
                      </button>
                    </div>
                ) : (
                    <div className="booking-list">
                      {bookings.map((booking) => {
                        const paymentStatus = booking.paymentStatus || "PENDING";
                        const bookingStatus = booking.status || "CONFIRMED";
                        const canPay =
                            paymentStatus !== "PAID" &&
                            bookingStatus !== "CANCELLED" &&
                            bookingStatus !== "COMPLETED";
                        const canCancel =
                            bookingStatus !== "CANCELLED" &&
                            bookingStatus !== "COMPLETED" &&
                            paymentStatus !== "PAID";

                        return (
                            <article className="booking-item" key={booking.id}>
                              <div className="booking-detail">
                                <p className="booking-id">
                                  RESERVATION #{booking.id}
                                </p>
                                <h3>{booking.roomName || `Room ${booking.roomId || ""}`}</h3>
                                <p className="booking-room-type">
                                  {booking.roomType || "HotelPro room"}
                                </p>

                                <div className="booking-meta">
                                  <p><span>Check-in</span><strong>{booking.checkIn || "—"}</strong></p>
                                  <p><span>Check-out</span><strong>{booking.checkOut || "—"}</strong></p>
                                  <p><span>Guests</span><strong>{booking.guests ?? "—"}</strong></p>
                                  <p><span>Total</span><strong>₹{formatPrice(booking.totalPrice)}</strong></p>
                                </div>
                              </div>

                              <div className="booking-side">
                        <span className={`status ${bookingStatus.toLowerCase()}`}>
                          {bookingStatus.replaceAll("_", " ")}
                        </span>
                                <p className="payment-label">Payment</p>
                                <span className={`status payment-${paymentStatus.toLowerCase()}`}>
                          {paymentStatus.replaceAll("_", " ")}
                        </span>

                                <div className="booking-actions">
                                  {canPay && (
                                      <button
                                          className="button button-gold button-full"
                                          disabled={busyBooking === booking.id}
                                          onClick={() => payForBooking(booking)}
                                      >
                                        {busyBooking === booking.id ? "Opening checkout…" : "Pay Now"}
                                      </button>
                                  )}

                                  {canCancel && (
                                      <button
                                          className="button button-outline button-full cancel-button"
                                          onClick={() => cancelBooking(booking.id)}
                                      >
                                        Cancel booking
                                      </button>
                                  )}
                                </div>
                              </div>
                            </article>
                        );
                      })}
                    </div>
                )}
              </section>
          )}

          {page === "admin" && isAdmin && (
              <section className="section-wrap admin-page">
                <div className="section-heading">
                  <div>
                    <p className="eyebrow">HOTELPRO MANAGEMENT</p>
                    <h2>Admin Dashboard</h2>
                  </div>
                  <button className="button button-outline button-small" onClick={fetchRooms}>
                    Refresh rooms
                  </button>
                </div>

                <div className="admin-stat-grid">
                  <article className="admin-stat">
                    <span>ROOM INVENTORY</span>
                    <strong>{rooms.length}</strong>
                    <p>Total rooms listed</p>
                  </article>
                  <article className="admin-stat">
                    <span>ACTIVE ROOMS</span>
                    <strong>{availableRooms.length}</strong>
                    <p>Rooms available to browse</p>
                  </article>
                  <article className="admin-stat">
                    <span>YOUR ROLE</span>
                    <strong>ADMIN</strong>
                    <p>Management access</p>
                  </article>
                </div>

                <div className="admin-panel">
                  <div className="admin-panel-heading">
                    <div>
                      <p className="eyebrow">PROPERTY OVERVIEW</p>
                      <h3>Room Inventory</h3>
                    </div>
                  </div>

                  <div className="table-scroll">
                    <table className="admin-table">
                      <thead>
                      <tr>
                        <th>Room</th>
                        <th>Type</th>
                        <th>Rate / night</th>
                        <th>Capacity</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                      </thead>
                      <tbody>
                      {rooms.map((room) => (
                          <tr key={room.id}>
                            <td>{getRoomName(room)}</td>
                            <td>{room.type || "—"}</td>
                            <td>₹{formatPrice(room.price)}</td>
                            <td>{room.capacity || room.maxGuests || "—"}</td>
                            <td>
                              {room.status ||
                                  (room.active === false ? "INACTIVE" : "ACTIVE")}
                            </td>
                            <td>
                              <button
                                  className="button button-outline button-small"
                                  onClick={() => updateRoomStatus(room)}
                              >
                                Toggle status
                              </button>
                            </td>
                          </tr>
                      ))}
                      </tbody>
                    </table>
                  </div>
                </div>
                <p className="form-hint">
                  Adding, editing, and deleting rooms requires the corresponding
                  admin endpoints in your backend.
                </p>
              </section>
          )}

          {page === "admin" && !isAdmin && (
              <section className="section-wrap empty-state">
                <h2>Access restricted.</h2>
                <p>This dashboard is available to administrators only.</p>
                <button className="button button-dark" onClick={() => changePage("rooms")}>
                  Return to rooms
                </button>
              </section>
          )}
        </main>

        <footer className="footer">
          <div className="footer-main">
            <button className="brand footer-brand" onClick={() => changePage("rooms")}>
              <span className="brand-mark">H</span>
              <span className="brand-text">
              HOTELPRO
              <small>STAY BEAUTIFULLY</small>
            </span>
            </button>
            <p>Thoughtful stays. Lasting memories.</p>
            <button className="text-button footer-top" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>
              Back to top ↑
            </button>
          </div>
          <div className="copyright">
            <span>© {new Date().getFullYear()} HotelPro. All rights reserved.</span>
            <span>MADE FOR BETTER STAYS</span>
          </div>
        </footer>
      </div>
  );
}

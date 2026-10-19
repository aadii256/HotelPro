# 🏨 HotelPro — Hotel Management System

HotelPro is a full-stack hotel management web application designed to simplify hotel room browsing, reservations, payment processing, and administrative operations through a user-friendly interface.

The application combines a **React frontend** with a **Java Spring Boot backend** and **MongoDB database** to provide a centralized platform for hotel booking and management.

## ✨ Features

### 👤 User Authentication
- User registration and login.
- Role-based access for guests and administrators.
- Secure password handling and token-based authentication.

### 🛏️ Room Management & Booking
- Browse available hotel rooms.
- View room information and pricing.
- Create hotel bookings with check-in and check-out dates.
- Manage personal booking history.
- Cancel eligible bookings.
- Prevent overlapping reservations for the same room.

### 💳 Online Payment Integration
- Razorpay payment gateway integration.
- Create payment orders for bookings.
- Verify payment signatures.
- Track payment status, including pending and paid payments.
- Test payment flow using Razorpay test mode.

### 🛠️ Admin Functionality
- Dedicated administrator access.
- Administrative booking and room management operations.
- Role-based protection for administrative endpoints.

### 🎨 User Interface
- Elegant, luxury-inspired design.
- Cream, olive, and gold color palette.
- React-based frontend.

## 🧰 Technology Stack

| Component | Technologies |
|---|---|
| Frontend | React, JavaScript, Vite, CSS |
| Backend | Java, Spring Boot |
| Database | MongoDB |
| Data Access | Spring Data MongoDB |
| Security | Spring Security, JWT authentication |
| Payment Gateway | Razorpay |
| Build Tool | Maven |
| Development Tools | IntelliJ IDEA, VS Code |
| Version Control | Git and GitHub |

## 📁 Project Structure

```text
HotelPro/
├── hotel-pro-backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hotelpro/
│   │   │   └── resources/
│   ├── pom.xml
│   └── mvnw
│
├── hotelpro-frontend/
│   ├── public/
│   ├── src/
│   │   ├── App.jsx
│   │   ├── App.css
│   │   └── index.css
│   ├── package.json
│   └── vite.config.js
│
└── README.md
```

## ⚙️ Prerequisites

Install the following before running the project:

- Java JDK
- Node.js and npm
- MongoDB Community Server or a MongoDB Atlas account
- IntelliJ IDEA or another Java IDE
- Git

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/aadii256/HotelPro.git
cd HotelPro
```

### 2. Configure MongoDB

1. Start your local MongoDB server or create a MongoDB Atlas cluster.
2. Create or select a database for HotelPro.
3. Configure the MongoDB connection URI in your Spring Boot application's local configuration.
4. Set the database name and authentication credentials as required.

Example connection URI for a local MongoDB instance:

```text
mongodb://localhost:27017/hotelpro
```

Keep database credentials and private connection details out of GitHub.

### 3. Configure Razorpay

To test online payments:

1. Create a Razorpay account and obtain your test API keys.
2. Configure the Razorpay key ID and secret using environment variables or a local configuration file.
3. Never commit your secret key to GitHub.

Use Razorpay test mode during development.

### 4. Run the Backend

Open a terminal inside `hotel-pro-backend` and run:

```bash
mvnw.cmd spring-boot:run
```

The backend runs at:

`http://localhost:8080`

### 5. Run the Frontend

Open another terminal inside `hotelpro-frontend` and run:

```bash
npm install
npm run dev
```

Open the local frontend URL shown in the terminal. The development server may use:

`http://localhost:5174`

## 🔗 Main API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/rooms` | Retrieve rooms |
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Authenticate a user |
| GET/POST | `/api/bookings` | Retrieve or create bookings, as supported by the controller |
| GET | `/api/bookings/mine` | Retrieve the current user's bookings |
| POST | `/api/bookings/{id}/cancel` | Cancel a booking |
| POST | `/api/payments/bookings/{bookingId}/order` | Create a Razorpay order |
| POST | `/api/payments/verify` | Verify a payment signature |

Administrative endpoints are also available and protected by role-based authorization. Refer to the backend controllers for the complete routes and supported operations.

## 🔐 Security Notes

- Authentication is required for protected operations.
- Administrative endpoints use role-based access control.
- Payment signatures are verified by the backend.
- Database credentials and private API keys must not be committed to GitHub.
- Use Razorpay test mode while developing and testing payments.

## 🎯 Project Objective

The objective of HotelPro is to provide a centralized hotel management platform that simplifies room reservations, booking management, payment processing, and administrative operations while offering a clean and intuitive user experience.

## 🔮 Future Enhancements

- Email confirmations and booking notifications.
- Enhanced room availability search and filtering.
- Booking analytics and revenue reports.
- Automated handling of unpaid or expired bookings.
- Production-grade payment status verification and webhook processing.
- Deployment to a cloud hosting platform.

## 👨‍💻 Author

**Aditya Shrivastav**

GitHub: [@aadii256](https://github.com/aadii256)

---

*HotelPro — Simplifying hotel bookings and management through technology.*

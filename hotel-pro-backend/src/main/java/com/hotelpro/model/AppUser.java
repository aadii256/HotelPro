
package com.hotelpro.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(unique = true, nullable = false)
    public String email;

    @JsonIgnore
    @Column(nullable = false)
    public String password;

    @Column(length = 20)
    public String phone = "";

    @Column(nullable = false)
    public String role = "GUEST";

    public AppUser() {
    }

    public AppUser(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}

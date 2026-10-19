
package com.hotelpro.model;

import jakarta.persistence.*;

@Entity
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public String type;

    @Column(length = 2000)
    public String description;

    public double price;
    public int capacity;
    public String imageUrl;
    public boolean active = true;

    public Room() {}
}

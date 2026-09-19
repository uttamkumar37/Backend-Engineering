package com.backendplan.datalayer.intermediate;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    Author author;

    public Book() {}
    public Book(String title, Author author) {
        this.title = title;
        this.author = author;
    }

    public String getTitle() { return title; }
}

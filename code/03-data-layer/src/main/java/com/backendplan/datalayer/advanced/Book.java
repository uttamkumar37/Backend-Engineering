package com.backendplan.datalayer.advanced;

import jakarta.persistence.*;

@Entity
@Table(name = "books_fixed")
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
}

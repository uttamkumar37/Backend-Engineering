package com.backendplan.datalayer.advanced;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "authors_fixed")
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String name;

    @OneToMany(mappedBy = "author")
    List<Book> books = new ArrayList<>();

    public Author() {}
    public Author(String name) { this.name = name; }

    public Long getId() { return id; }
    public List<Book> getBooks() { return books; }
}

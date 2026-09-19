package com.backendplan.datalayer.intermediate;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "authors")
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String name;

    // default fetch type for @OneToMany is LAZY - this is intentional here, not a mistake
    @OneToMany(mappedBy = "author")
    List<Book> books = new ArrayList<>();

    public Author() {}
    public Author(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Book> getBooks() { return books; }
}

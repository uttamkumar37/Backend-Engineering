package com.backendplan.datalayer.intermediate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorService {
    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Transactional
    public int accessAllBooksNaively() {
        List<Author> authors = authorRepository.findAll();
        int totalBooks = 0;
        for (Author author : authors) {
            // each call below triggers a SEPARATE lazy-load query - this is the N+1
            totalBooks += author.getBooks().size();
        }
        return totalBooks;
    }
}

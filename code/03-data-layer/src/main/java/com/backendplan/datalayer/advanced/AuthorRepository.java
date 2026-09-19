package com.backendplan.datalayer.advanced;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // JOIN FETCH forces the books to load in the SAME query as the authors -
    // one round trip instead of one-plus-N.
    @Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books")
    List<Author> findAllWithBooksFetched();
}

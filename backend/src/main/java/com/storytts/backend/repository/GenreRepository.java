package com.storytts.backend.repository;

import com.storytts.backend.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Genre> findAllByOrderByNameAsc();
}

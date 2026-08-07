package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/** Bảng authors — tác giả truyện. */
@Entity
@Table(name = "authors", uniqueConstraints = @UniqueConstraint(name = "uk_authors_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String bio;
}

package com.storytts.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/** Bảng genres — thể loại truyện. */
@Entity
@Table(name = "genres", uniqueConstraints = @UniqueConstraint(name = "uk_genres_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;
}

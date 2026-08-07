package com.storytts.backend.controller.admin;

import com.storytts.backend.dto.catalog.AuthorDto;
import com.storytts.backend.dto.catalog.AuthorRequest;
import com.storytts.backend.dto.catalog.GenreDto;
import com.storytts.backend.dto.catalog.GenreRequest;
import com.storytts.backend.service.AuthorService;
import com.storytts.backend.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** CRUD thể loại và tác giả. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Danh mục", description = "Quản lý thể loại và tác giả")
public class AdminCatalogController {

    private final GenreService genreService;
    private final AuthorService authorService;

    @PostMapping("/genres")
    @Operation(summary = "Thêm thể loại")
    public ResponseEntity<GenreDto> createGenre(@Valid @RequestBody GenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(genreService.create(request));
    }

    @PutMapping("/genres/{id}")
    @Operation(summary = "Sửa thể loại")
    public GenreDto updateGenre(@PathVariable Long id, @Valid @RequestBody GenreRequest request) {
        return genreService.update(id, request);
    }

    @DeleteMapping("/genres/{id}")
    @Operation(summary = "Xóa thể loại (chỉ khi không còn truyện nào dùng)")
    public ResponseEntity<Void> deleteGenre(@PathVariable Long id) {
        genreService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/authors")
    @Operation(summary = "Thêm tác giả")
    public ResponseEntity<AuthorDto> createAuthor(@Valid @RequestBody AuthorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorService.create(request));
    }

    @PutMapping("/authors/{id}")
    @Operation(summary = "Sửa tác giả")
    public AuthorDto updateAuthor(@PathVariable Long id, @Valid @RequestBody AuthorRequest request) {
        return authorService.update(id, request);
    }

    @DeleteMapping("/authors/{id}")
    @Operation(summary = "Xóa tác giả (chỉ khi không còn truyện nào dùng)")
    public ResponseEntity<Void> deleteAuthor(@PathVariable Long id) {
        authorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

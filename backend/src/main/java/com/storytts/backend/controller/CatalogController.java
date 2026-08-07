package com.storytts.backend.controller;

import com.storytts.backend.dto.catalog.AuthorDto;
import com.storytts.backend.dto.catalog.GenreDto;
import com.storytts.backend.service.AuthorService;
import com.storytts.backend.service.GenreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Dữ liệu dùng cho bộ lọc ở trang danh sách truyện. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Danh mục", description = "Thể loại và tác giả")
public class CatalogController {

    private final GenreService genreService;
    private final AuthorService authorService;

    @GetMapping("/api/genres")
    @Operation(summary = "Tất cả thể loại")
    public List<GenreDto> genres() {
        return genreService.findAll();
    }

    @GetMapping("/api/authors")
    @Operation(summary = "Tất cả tác giả")
    public List<AuthorDto> authors() {
        return authorService.findAll();
    }
}

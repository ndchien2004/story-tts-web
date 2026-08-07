package com.storytts.backend.service;

import com.storytts.backend.domain.Genre;
import com.storytts.backend.dto.catalog.GenreDto;
import com.storytts.backend.dto.catalog.GenreRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.GenreRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD thể loại (mục 4.2 [NC] đề bài). */
@Service
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository;
    private final StoryRepository storyRepository;

    @Transactional(readOnly = true)
    public List<GenreDto> findAll() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(GenreDto::from).toList();
    }

    @Transactional
    public GenreDto create(GenreRequest request) {
        String name = request.name().trim();
        if (genreRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Thể loại '%s' đã tồn tại.".formatted(name));
        }
        Genre genre = Genre.builder()
                .name(name)
                .description(request.description())
                .build();
        return GenreDto.from(genreRepository.save(genre));
    }

    @Transactional
    public GenreDto update(Long id, GenreRequest request) {
        Genre genre = findEntity(id);
        String name = request.name().trim();
        genreRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BadRequestException("Thể loại '%s' đã tồn tại.".formatted(name));
                });
        genre.setName(name);
        genre.setDescription(request.description());
        return GenreDto.from(genreRepository.save(genre));
    }

    @Transactional
    public void delete(Long id) {
        Genre genre = findEntity(id);
        long inUse = storyRepository.countByGenreId(id);
        if (inUse > 0) {
            throw new BadRequestException(
                    "Không thể xóa: còn %d truyện thuộc thể loại '%s'.".formatted(inUse, genre.getName()));
        }
        genreRepository.delete(genre);
    }

    /** Tìm theo tên, chưa có thì tạo mới — tiện cho form thêm truyện của Admin. */
    @Transactional
    public Genre findOrCreateByName(String name) {
        String trimmed = name.trim();
        return genreRepository.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> genreRepository.save(Genre.builder().name(trimmed).build()));
    }

    @Transactional(readOnly = true)
    public Genre findEntity(Long id) {
        return genreRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("thể loại", id));
    }
}

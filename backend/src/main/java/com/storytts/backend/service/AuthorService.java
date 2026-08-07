package com.storytts.backend.service;

import com.storytts.backend.domain.Author;
import com.storytts.backend.dto.catalog.AuthorDto;
import com.storytts.backend.dto.catalog.AuthorRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AuthorRepository;
import com.storytts.backend.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD tác giả (mục 4.2 [NC] đề bài). */
@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final StoryRepository storyRepository;

    @Transactional(readOnly = true)
    public List<AuthorDto> findAll() {
        return authorRepository.findAllByOrderByNameAsc().stream().map(AuthorDto::from).toList();
    }

    @Transactional
    public AuthorDto create(AuthorRequest request) {
        String name = request.name().trim();
        if (authorRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Tác giả '%s' đã tồn tại.".formatted(name));
        }
        Author author = Author.builder().name(name).bio(request.bio()).build();
        return AuthorDto.from(authorRepository.save(author));
    }

    @Transactional
    public AuthorDto update(Long id, AuthorRequest request) {
        Author author = findEntity(id);
        String name = request.name().trim();
        authorRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BadRequestException("Tác giả '%s' đã tồn tại.".formatted(name));
                });
        author.setName(name);
        author.setBio(request.bio());
        return AuthorDto.from(authorRepository.save(author));
    }

    @Transactional
    public void delete(Long id) {
        Author author = findEntity(id);
        long inUse = storyRepository.countByAuthorId(id);
        if (inUse > 0) {
            throw new BadRequestException(
                    "Không thể xóa: còn %d truyện của tác giả '%s'.".formatted(inUse, author.getName()));
        }
        authorRepository.delete(author);
    }

    @Transactional
    public Author findOrCreateByName(String name) {
        String trimmed = name.trim();
        return authorRepository.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> authorRepository.save(Author.builder().name(trimmed).build()));
    }

    @Transactional(readOnly = true)
    public Author findEntity(Long id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("tác giả", id));
    }
}

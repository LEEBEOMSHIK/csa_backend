package org.example.csa_backend.character;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.character.dto.CharacterDto;
import org.example.csa_backend.character.dto.CharacterSaveRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping
    public ResponseEntity<CharacterDto> create(
            @RequestBody CharacterSaveRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(characterService.create(userId(authentication), request));
    }

    @GetMapping
    public ResponseEntity<List<CharacterDto>> getMyCharacters(Authentication authentication) {
        return ResponseEntity.ok(characterService.getMyCharacters(userId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CharacterDto> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(characterService.get(userId(authentication), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CharacterDto> update(
            @PathVariable Long id, @RequestBody CharacterSaveRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(characterService.update(userId(authentication), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        characterService.delete(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    // /characters/** 는 SecurityConfig에서 인증 필수 경로이므로 authentication 은 항상 존재
    private Long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}

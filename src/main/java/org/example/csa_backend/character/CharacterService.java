package org.example.csa_backend.character;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.character.dto.CharacterDto;
import org.example.csa_backend.character.dto.CharacterSaveRequest;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;

    @Transactional
    public CharacterDto create(Long userId, CharacterSaveRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Character character = new Character(owner, request.name(), joinVariants(request.variants()));
        characterRepository.save(character);
        return CharacterDto.from(character);
    }

    public List<CharacterDto> getMyCharacters(Long userId) {
        return characterRepository.findByOwnerIdOrderByIdDesc(userId).stream()
                .map(CharacterDto::from)
                .toList();
    }

    public CharacterDto get(Long userId, Long characterId) {
        return CharacterDto.from(getOwnedCharacter(userId, characterId));
    }

    @Transactional
    public CharacterDto update(Long userId, Long characterId, CharacterSaveRequest request) {
        Character character = getOwnedCharacter(userId, characterId);
        character.update(request.name(), joinVariants(request.variants()));
        return CharacterDto.from(character);
    }

    @Transactional
    public void delete(Long userId, Long characterId) {
        characterRepository.delete(getOwnedCharacter(userId, characterId));
    }

    private Character getOwnedCharacter(Long userId, Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!character.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return character;
    }

    private String joinVariants(List<Integer> variants) {
        if (variants == null || variants.isEmpty()) {
            return "";
        }
        return variants.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}

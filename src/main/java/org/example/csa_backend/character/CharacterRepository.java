package org.example.csa_backend.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    List<Character> findByOwnerIdOrderByIdDesc(Long ownerId);
}

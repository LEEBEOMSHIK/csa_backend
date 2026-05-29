package org.example.csa_backend.character.dto;

import java.util.List;

public record CharacterSaveRequest(String name, List<Integer> variants) {}

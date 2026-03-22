package org.example.csa_backend.fairytale.dto;

import java.util.List;

public record HomePageDto(
        List<FairytaleDto> themes,
        List<FairytaleDto> newItems,
        List<FairytaleDto> recommended
) {}

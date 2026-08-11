package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LegacyContractMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void typedMetadataRoundTripsAsAnObjectWithoutLosingNullOrScalarTypes() throws Exception {
        LegacyContractMetadata metadata = new LegacyContractMetadata(
            "2024-02-03T04:05:06.123456",
            4.75,
            "#123456",
            "bedtime",
            false,
            "작가",
            "作家",
            "6-8",
            7,
            9,
            "전체 본문",
            "全文",
            "legacy-rich-v2",
            true,
            "dad",
            "forest",
            "adventure",
            "courage",
            1
        );

        String json = objectMapper.writeValueAsString(metadata);

        assertThat(objectMapper.readTree(json).isObject()).isTrue();
        assertThat(objectMapper.readValue(json, LegacyContractMetadata.class)).isEqualTo(metadata);
    }

    @Test
    void typedMetadataRejectsNegativeCountsAndTimestamps() {
        assertThatThrownBy(() -> new LegacyContractMetadata(
            "-1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyEpochFieldNameRemainsReadableWithoutChangingItsScalarValue() throws Exception {
        LegacyContractMetadata metadata = objectMapper.readValue(
            "{\"createdAtEpochMillis\":1706933106123}",
            LegacyContractMetadata.class
        );

        assertThat(metadata.createdAt()).isEqualTo("1706933106123");
    }

    @Test
    void emptyMetadataKeepsAllNullableHashPositionsDeterministically() {
        assertThat(LegacyContractMetadata.empty().hashParts())
            .hasSize(22)
            .allMatch(java.util.Objects::isNull);
    }
}

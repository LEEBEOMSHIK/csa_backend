package org.example.csa_backend.fairytale;

import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiFairytaleRepositoryAdminSearchTest {

    @Autowired
    private AiFairytaleRepository aiFairytaleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void statusOnlyFilterIgnoresSharedAndReturnsCorrectTotalElements() {
        User owner = userRepository.saveAndFlush(new User("fairytale-owner@example.com", "pw"));

        AiFairytale completedShared = new AiFairytale(
                "shared title", "settings", "genre", "theme", 3, "voice", "ko", "IMAGE", "COMPLETED");
        completedShared.assignOwner(owner);
        completedShared.updateShared(true);

        AiFairytale completedUnshared = new AiFairytale(
                "unshared title", "settings", "genre", "theme", 3, "voice", "ko", "IMAGE", "COMPLETED");
        completedUnshared.assignOwner(owner);
        completedUnshared.updateShared(false);

        AiFairytale pendingShared = new AiFairytale(
                "pending title", "settings", "genre", "theme", 3, "voice", "ko", "IMAGE", "PENDING");
        pendingShared.assignOwner(owner);
        pendingShared.updateShared(true);

        aiFairytaleRepository.saveAllAndFlush(List.of(completedShared, completedUnshared, pendingShared));

        // status=COMPLETED only, no shared filter -> must include both shared and unshared COMPLETED fairytales,
        // must exclude the PENDING one, and totalElements must reflect the filtered count.
        Page<AiFairytale> result = aiFairytaleRepository.searchForAdmin(null, "COMPLETED", null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AiFairytale::getStatus)
                .containsOnly("COMPLETED");
        assertThat(result.getContent())
                .extracting(AiFairytale::getTitle)
                .containsExactlyInAnyOrder("shared title", "unshared title");
    }
}

package org.example.csa_backend.report;

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
class ReportRepositoryAdminSearchTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void targetTypeOnlyFilterIgnoresStatusAndReturnsCorrectTotalElements() {
        User reporter = userRepository.saveAndFlush(new User("reporter@example.com", "pw"));

        Report fairytalePending = new Report(reporter, "FAIRYTALE", 1L, "SPAM", "detail-1");
        Report fairytaleResolved = new Report(reporter, "FAIRYTALE", 2L, "SPAM", "detail-2");
        fairytaleResolved.resolve("RESOLVED", "checked", reporter);
        Report commentPending = new Report(reporter, "COMMENT", 3L, "SPAM", "detail-3");

        reportRepository.saveAllAndFlush(List.of(fairytalePending, fairytaleResolved, commentPending));

        // targetType=FAIRYTALE only, no status filter -> must include both PENDING and RESOLVED fairytale reports,
        // must exclude the comment report, and totalElements must reflect the filtered count.
        Page<Report> result = reportRepository.searchForAdmin("FAIRYTALE", null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(Report::getTargetType)
                .containsOnly("FAIRYTALE");
        assertThat(result.getContent())
                .extracting(Report::getTargetId)
                .containsExactlyInAnyOrder(1L, 2L);
    }
}

package org.example.csa_backend.storycontent;

import java.util.Optional;

public interface ContentVersionAggregateRepository {

    Optional<ContentVersionAggregate> findForPublish(Long versionId);

    Optional<ContentVersionAggregate> findPublished(Long versionId);
}

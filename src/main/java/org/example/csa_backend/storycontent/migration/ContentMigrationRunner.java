package org.example.csa_backend.storycontent.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.LegacyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("content-migration")
public class ContentMigrationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ContentMigrationRunner.class);
    private static final int BATCH_SIZE = 500;

    private final ContentCutoverService cutoverService;
    private final LegacyStoryImportService importService;
    private final ContentMigrationActor actor;
    private final ContractChecksum checksum;

    public ContentMigrationRunner(
        ContentCutoverService cutoverService,
        LegacyStoryImportService importService,
        ContentMigrationActor actor,
        ContractChecksum checksum
    ) {
        this.cutoverService = cutoverService;
        this.importService = importService;
        this.actor = actor;
        this.checksum = checksum;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        ContentMigrationCommand command = ContentMigrationCommand.parse(
            requiredOption(arguments, "content.migration.command")
        );
        long epoch = positiveLong(requiredOption(arguments, "content.migration.epoch"));
        if (command.confirmationRequired()) {
            long confirmation = positiveLong(requiredOption(
                arguments,
                "content.migration.confirm-epoch"
            ));
            if (confirmation != epoch) {
                throw new IllegalArgumentException("CONTENT_MIGRATION_CONFIRM_EPOCH_MISMATCH");
            }
        }

        switch (command) {
            case BULK_IMPORT -> bulkImport(arguments, epoch);
            case REQUEST_FREEZE -> requestFreeze(epoch);
            case FINALIZE -> finalizeCutover(epoch);
            case SMOKE -> verifySmoke(epoch);
            case OPEN_WRITES -> cutoverService.openCanonicalWrites(epoch);
            case ROLLBACK -> cutoverService.rollbackToLegacy(
                epoch,
                optionalOption(arguments, "content.migration.rollback-reason", "operator-cli")
            );
        }
    }

    private void requestFreeze(long epoch) {
        if (cutoverService.nextFreezeEpoch() != epoch) {
            throw new IllegalArgumentException("CONTENT_MIGRATION_EPOCH_MISMATCH");
        }
        MigrationEpoch requested = cutoverService.requestFreeze(actor.value());
        if (requested.value() != epoch) {
            throw new IllegalStateException("CONTENT_MIGRATION_EPOCH_MISMATCH");
        }
    }

    private void finalizeCutover(long epoch) {
        ReconciliationReport report = cutoverService.runFinalReconciliation(epoch);
        if (!report.complete()) {
            throw new IllegalStateException("FINAL_RECONCILIATION_FAILED");
        }
        cutoverService.cutover(epoch);
    }

    private void verifySmoke(long epoch) {
        SmokeResult result = cutoverService.verifySmoke(epoch);
        if (!result.passed()) {
            throw new IllegalStateException(result.code());
        }
    }

    private void bulkImport(ApplicationArguments arguments, long epoch) {
        boolean dryRun = requiredBoolean(arguments, "content.migration.dry-run");
        if (dryRun) {
            Map<Long, String> curated = importService.sourceHashes(LegacyType.CURATED);
            Map<Long, String> ai = importService.sourceHashes(LegacyType.AI);
            List<String> parts = new ArrayList<>();
            parts.add("CONTENT_MIGRATION_DRY_RUN_V1");
            parts.add(Long.toString(epoch));
            appendHashes(parts, LegacyType.CURATED, curated);
            appendHashes(parts, LegacyType.AI, ai);
            LOG.info(
                "Content migration dry run epoch={} curatedCount={} aiCount={} checksum={}",
                epoch,
                curated.size(),
                ai.size(),
                checksum.ofParts(parts)
            );
            return;
        }
        importAll(LegacyType.CURATED);
        importAll(LegacyType.AI);
    }

    private void importAll(LegacyType type) {
        long cursor = 0;
        while (true) {
            ImportBatchResult result = type == LegacyType.CURATED
                ? importService.importCuratedBatch(cursor, BATCH_SIZE)
                : importService.importAiBatch(cursor, BATCH_SIZE);
            if (result.complete()) {
                return;
            }
            if (result.nextLegacyId() <= cursor) {
                throw new IllegalStateException("CONTENT_MIGRATION_IMPORT_CURSOR_STALLED");
            }
            cursor = result.nextLegacyId();
        }
    }

    private void appendHashes(
        List<String> parts,
        LegacyType type,
        Map<Long, String> hashes
    ) {
        hashes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                parts.add(type.name());
                parts.add(Long.toString(entry.getKey()));
                parts.add(entry.getValue());
            });
    }

    private String requiredOption(ApplicationArguments arguments, String name) {
        List<String> values = arguments.getOptionValues(name);
        if (values == null || values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException("CONTENT_MIGRATION_ARGUMENT_REQUIRED: " + name);
        }
        return values.get(0);
    }

    private String optionalOption(
        ApplicationArguments arguments,
        String name,
        String fallback
    ) {
        List<String> values = arguments.getOptionValues(name);
        if (values == null) {
            return fallback;
        }
        if (values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException("CONTENT_MIGRATION_ARGUMENT_INVALID: " + name);
        }
        return values.get(0);
    }

    private long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CONTENT_MIGRATION_EPOCH_INVALID", exception);
        }
    }

    private boolean requiredBoolean(ApplicationArguments arguments, String name) {
        return switch (requiredOption(arguments, name)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                "CONTENT_MIGRATION_ARGUMENT_INVALID: " + name
            );
        };
    }
}

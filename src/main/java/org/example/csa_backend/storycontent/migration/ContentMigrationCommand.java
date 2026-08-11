package org.example.csa_backend.storycontent.migration;

import java.util.Arrays;

public enum ContentMigrationCommand {
    BULK_IMPORT("bulk-import", false),
    REQUEST_FREEZE("request-freeze", true),
    FINALIZE("finalize", true),
    SMOKE("smoke", false),
    OPEN_WRITES("open-writes", true),
    ROLLBACK("rollback", true);

    private final String argument;
    private final boolean confirmationRequired;

    ContentMigrationCommand(String argument, boolean confirmationRequired) {
        this.argument = argument;
        this.confirmationRequired = confirmationRequired;
    }

    public boolean confirmationRequired() {
        return confirmationRequired;
    }

    public static ContentMigrationCommand parse(String value) {
        return Arrays.stream(values())
            .filter(command -> command.argument.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("CONTENT_MIGRATION_COMMAND_INVALID"));
    }
}

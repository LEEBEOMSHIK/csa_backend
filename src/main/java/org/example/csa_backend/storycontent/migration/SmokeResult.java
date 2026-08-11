package org.example.csa_backend.storycontent.migration;

public record SmokeResult(boolean passed, String code, String checksum) {

    public static SmokeResult passed(String checksum) {
        return new SmokeResult(true, "CUTOVER_SMOKE_PASSED", checksum);
    }

    public static SmokeResult failed(String code) {
        return new SmokeResult(false, code, null);
    }
}

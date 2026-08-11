package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.example.csa_backend.storycontent.migration.LegacyMediaSnapshotStore;
import org.example.csa_backend.storycontent.migration.LegacyProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class StoryRuntimeSecurityPathTest {

    private static final Path MEDIA_ROOT = createMediaRoot();
    private static final String MEDIA_PREFIX = "phase1/content";

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("csa.media.storage-mode", () -> "local");
        registry.add("csa.media.storage-root", MEDIA_ROOT::toString);
        registry.add("csa.media.prefix", () -> MEDIA_PREFIX);
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private LegacyMediaSnapshotStore legacyMediaSnapshotStore;

    @MockitoBean
    private StoryRuntimeService storyRuntimeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void publishedRuntimeGetIsPermittedWithoutJwt() throws Exception {
        when(storyRuntimeService.getPublishedRuntime(eq(7L), any())).thenReturn(response());

        mockMvc.perform(get("/stories/7/runtime").param("locale", "ko"))
            .andExpect(status().isOk());
    }

    @Test
    void mutationOnRuntimePathStillRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/stories/7/runtime"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adjacentStoryPathStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/stories/7/runtime/authoring"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void promotedStoryAssetIsPubliclyReadableFromTheExactPrefixedRoute() throws Exception {
        byte[] bytes = "published-image".getBytes(StandardCharsets.UTF_8);
        Path asset = MEDIA_ROOT.resolve(MEDIA_PREFIX).resolve("story-assets/7/versions/17/image.png");
        Files.createDirectories(asset.getParent());
        Files.write(asset, bytes);

        mockMvc.perform(get("/uploads/phase1/content/story-assets/7/versions/17/image.png"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(bytes));
    }

    @Test
    void importedAssetAndManifestUseThePrefixedPromotedPublicRoute() throws Exception {
        byte[] imageBytes = "imported-image".getBytes(StandardCharsets.UTF_8);
        byte[] manifestBytes = "{}".getBytes(StandardCharsets.UTF_8);
        Path source = MEDIA_ROOT.resolve("legacy-import-source/page.png");
        Files.createDirectories(source.getParent());
        Files.write(source, imageBytes);
        LegacyProjection projection = importedProjection(
            909L,
            "/uploads/legacy-import-source/page.png"
        );

        LegacyMediaSnapshotStore.PreparedImport preparedImport =
            legacyMediaSnapshotStore.prepare(projection);
        legacyMediaSnapshotStore.materialize(preparedImport);
        LegacyMediaSnapshotStore.PreparedAsset image =
            preparedImport.media().assets().get("page-0-image");
        LegacyMediaSnapshotStore.PreparedAsset manifest = legacyMediaSnapshotStore.writeManifest(
            preparedImport.projection(),
            909L,
            1_909L,
            manifestBytes
        );

        mockMvc.perform(get(URI.create(image.publicUrl()).getPath()))
            .andExpect(status().isOk())
            .andExpect(content().bytes(imageBytes));
        mockMvc.perform(get(URI.create(manifest.publicUrl()).getPath()))
            .andExpect(status().isOk())
            .andExpect(content().bytes(manifestBytes));
        assertThat(image.storageKey()).startsWith(
            MEDIA_PREFIX + "/story-assets/imports/curated/909/"
        );
        assertThat(manifest.storageKey()).startsWith(
            MEDIA_PREFIX + "/story-assets/imports/curated/909/"
        );
    }

    @Test
    void quarantineAndUnprefixedMediaRoutesRemainProtected() throws Exception {
        Path quarantine = MEDIA_ROOT.resolve("quarantine/secret.json");
        Files.createDirectories(quarantine.getParent());
        Files.writeString(quarantine, "secret", StandardCharsets.UTF_8);

        mockMvc.perform(get("/uploads/quarantine/secret.json"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/uploads/story-assets/7/versions/17/image.png"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void traversalAndNonGetRequestsDoNotExposePromotedBytes() throws Exception {
        mockMvc.perform(get("/uploads/phase1/content/story-assets/%2e%2e/%2e%2e/quarantine/secret.json"))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/uploads/phase1/content/story-assets/7/versions/17/image.png"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void promotedRouteDoesNotFollowAWindowsJunctionOutsideItsRoot() throws Exception {
        Path promotedRoot = MEDIA_ROOT.resolve(MEDIA_PREFIX).resolve("story-assets");
        Files.createDirectories(promotedRoot);
        Path outside = Files.createTempDirectory("csa-published-outside-");
        byte[] secret = "outside-secret".getBytes(StandardCharsets.UTF_8);
        Files.write(outside.resolve("secret.png"), secret);
        Path junction = promotedRoot.resolve("escape");
        createDirectoryJunction(junction, outside);

        try {
            byte[] body = mockMvc.perform(
                    get("/uploads/phase1/content/story-assets/escape/secret.png")
                )
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200))
                .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("outside-secret");
        } finally {
            Files.deleteIfExists(junction);
            Files.deleteIfExists(outside.resolve("secret.png"));
            Files.deleteIfExists(outside);
        }
    }

    private StoryRuntimeManifestResponse response() {
        StoredRuntimeManifest manifest = new StoredRuntimeManifest(
            1, 7L, 17L, "3", "CURATED", "SLIDE", "SLIDE", "1", List.of("ko"), "ko", "mom",
            Map.of("ko", List.of("mom")), Map.of("ko", "mom"), List.of(), List.of(), List.of(), List.of(), null
        );
        return StoryRuntimeManifestResponse.flat(
            manifest, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
    }

    private LegacyProjection importedProjection(long legacyId, String imageUrl) {
        return new LegacyProjection(
            LegacyType.CURATED,
            legacyId,
            StoryOrigin.CURATED,
            Long.toString(legacyId),
            null,
            StoryVisibility.PUBLISHED,
            "title",
            "title",
            "description",
            "description",
            List.of(),
            ContentVersionStatus.PUBLISHED,
            true,
            "slide",
            "COMPLETED",
            "ko",
            null,
            Map.of("ko", List.of()),
            Map.of(),
            List.of(new LegacyProjection.SceneProjection(
                "page-0",
                0,
                1_000,
                1,
                1,
                Map.of("ko", "text"),
                imageUrl,
                List.of(),
                null
            )),
            List.of(),
            null,
            "a".repeat(64)
        );
    }

    private static Path createMediaRoot() {
        try {
            return Files.createTempDirectory("csa-published-media-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private void createDirectoryJunction(Path junction, Path target) throws Exception {
        Process process = new ProcessBuilder(
            "cmd.exe", "/c", "mklink", "/J", junction.toString(), target.toString()
        ).redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}

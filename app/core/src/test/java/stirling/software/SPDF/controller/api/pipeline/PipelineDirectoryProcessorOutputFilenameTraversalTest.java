package stirling.software.SPDF.controller.api.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import stirling.software.SPDF.model.PipelineConfig;
import stirling.software.SPDF.service.ApiDocService;
import stirling.software.common.configuration.RuntimePathConfig;
import stirling.software.common.service.PostHogService;
import stirling.software.common.service.ToolMetadataService;
import stirling.software.common.util.FileReadinessChecker;

import tools.jackson.databind.ObjectMapper;

class PipelineDirectoryProcessorOutputFilenameTraversalTest {

    @TempDir Path tempDir;

    private Path finished;
    private Path jobDir;
    private PipelineDirectoryProcessor processor;

    @BeforeEach
    void setUp() {
        finished = tempDir.resolve("pipeline").resolve("finishedFolders").toAbsolutePath();
        Path watched = tempDir.resolve("pipeline").resolve("watchedFolders").toAbsolutePath();
        jobDir = watched.resolve("job");
        RuntimePathConfig paths = mock(RuntimePathConfig.class);
        when(paths.getPipelineWatchedFoldersPaths()).thenReturn(List.of(watched.toString()));
        when(paths.getPipelineFinishedFoldersPath()).thenReturn(finished.toString());
        processor =
                new PipelineDirectoryProcessor(
                        new ObjectMapper(),
                        mock(ApiDocService.class),
                        mock(ToolMetadataService.class),
                        mock(PipelineProcessor.class),
                        mock(PostHogService.class),
                        mock(FileReadinessChecker.class),
                        paths);
    }

    @Test
    void outputFilenamePatternCanEscapeFinishedFolder() throws Exception {
        PipelineConfig config = new PipelineConfig();
        config.setOutputPattern("../../escaped");

        Resource output =
                new ByteArrayResource("proof".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "result.txt";
                    }
                };

        Method moveAndRenameFiles =
                PipelineDirectoryProcessor.class.getDeclaredMethod(
                        "moveAndRenameFiles", List.class, PipelineConfig.class, Path.class);
        moveAndRenameFiles.setAccessible(true);
        moveAndRenameFiles.invoke(processor, List.of(output), config, jobDir);

        // The configured output root is .../pipeline/finishedFolders. The pattern's ../../
        // segments cause the write to land in the parent temp directory instead.
        assertTrue(Files.exists(tempDir.resolve("escaped.txt")));
        assertTrue(!Files.exists(finished.resolve("escaped.txt")));
    }
}

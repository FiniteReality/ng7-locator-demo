package finitereality.modlocator;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public abstract class GenerateModLocatorInfoTask extends DefaultTask
{
    // The list of output folders from the sourceset.
    // Since this represents a classpath, we only need to check changes that
    // would necessitate recompilation.
    @InputFiles
    @CompileClasspath
    abstract ConfigurableFileCollection getInputSources();

    // The written metadata file
    @OutputFile
    abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void execute()
    {
        var fileLocations = getInputSources()
            .getFiles()
            .stream()
            .map(File::toURI)
            .map(URI::toString)
            .collect(Collectors.toSet());

        try
        {
            // Write the entire classpath to a file so we can reference it later
            Files.write(
                getOutputFile().get().getAsFile().toPath(),
                fileLocations,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE);
            // TODO: make this task incremental and only mark us as doing work
            // when getInputSources changes
            setDidWork(true);
        }
        catch (IOException e)
        {
            // TODO: should this be debug?
            getLogger().warn("Error occured while writing modlocator metadata", e);
            throw new RuntimeException(e);
        }
    }
}

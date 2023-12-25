package finitereality.modlocator;

import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.neoforged.neoforgespi.locating.IModLocator;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GradleModLocator extends AbstractJarFileModProvider implements IModLocator
{
    @Override
    public List<IModLocator.ModFileOrException> scanMods()
    {
        return scanCandidates().map(this::readModMetadata).toList();
    }

    private ModFileOrException readModMetadata(Path path)
    {
        try
        {
            return createMod(
                Files.readAllLines(path)
                    .stream()
                    .map(URI::create)
                    .map(Path::of)
                    .toArray(Path[]::new));
        }
        catch (IOException e)
        {
            var ex = new ModFileLoadingException("Unable to read mod metadata from " + path);
            ex.addSuppressed(e);
            return new ModFileOrException(null, ex);
        }
    }

    private Stream<Path> scanCandidates()
    {
        return Arrays.stream(
            System.getProperty("finitereality.modlocator.additionalMods", "")
                .split(","))
            .map(URI::create)
            .map(Path::of);
    }

    @Override
    public String name()
    {
        return "gradle";
    }

    @Override
    public void initArguments(final Map<String, ?> arguments)
    { }
}
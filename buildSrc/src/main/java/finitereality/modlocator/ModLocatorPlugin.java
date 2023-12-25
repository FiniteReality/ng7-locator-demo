package finitereality.modlocator;

import net.neoforged.gradle.common.CommonProjectPlugin;
import net.neoforged.gradle.common.util.constants.RunsConstants;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.*;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ModLocatorPlugin implements Plugin<Project>
{
    public static final String MODS_ELEMENTS = "mods";

    record AttributeValue<T extends Named>(Attribute<T> attribute, Class<T> clazz, String value) { }

    // These are the attributes that get added to the modClasspath configuration.
    // Other than LIBRARY_ELEMENTS_ATTRIBUTE, these values match the Java project conventions.
    private static final List<AttributeValue<?>> ModConfigurationAttributes =
        List.of(
            new AttributeValue<>(
                Category.CATEGORY_ATTRIBUTE,
                Category.class,
                Category.LIBRARY),
            new AttributeValue<>(
                Bundling.BUNDLING_ATTRIBUTE,
                Bundling.class,
                Bundling.EXTERNAL),
            new AttributeValue<>(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                LibraryElements.class,
                MODS_ELEMENTS), // Mark us as resolving our specific "mod metadata"
            new AttributeValue<>(
                Usage.USAGE_ATTRIBUTE,
                Usage.class,
                Usage.JAVA_RUNTIME)); // We're used at runtime

    // These are the attributes that get added to the mods variant which contains our metadata artifact.
    // Other than LIBRARY_ELEMENTS_ATTRIBUTE, these values match the Java project conventions.
    private static final List<AttributeValue<?>> ModVariantAttributes =
        List.of(
            new AttributeValue<>(
                Category.CATEGORY_ATTRIBUTE,
                Category.class,
                Category.LIBRARY),
            new AttributeValue<>(
                Bundling.BUNDLING_ATTRIBUTE,
                Bundling.class,
                Bundling.EXTERNAL),
            new AttributeValue<>(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                LibraryElements.class,
                MODS_ELEMENTS), // Mark us as producing our specific "mod metadata"
            new AttributeValue<>(
                Usage.USAGE_ATTRIBUTE,
                Usage.class,
                Usage.JAVA_RUNTIME));

    // A convenience function for applying the above attributes to an attribute container.
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applyAttributes(
        AttributeContainer container,
        ObjectFactory objectFactory,
        List<AttributeValue<?>> attributes)
    {
        for (final var value : attributes)
        {
            final var namedObject = objectFactory.named(value.clazz(), value.value());
            container.attribute((Attribute)value.attribute(), namedObject);
        }
    }

    @Override
    public void apply(Project project)
    {
        // Ensure that one of the NeoGradle plugins have been applied
        // (based on the mixin plugin)
        if (project.getPlugins().findPlugin(CommonProjectPlugin.class) == null)
            throw new IllegalStateException("The modlocator plugin requires the common plugin to be applied first.");

        final var objectFactory = project.getObjects();
        final var configurations = project.getConfigurations();
        final var tasks = project.getTasks();

        // Add our compatibility rule for dependency matching.
        // TODO: is this necessary?
        project.getDependencies().attributesSchema(schema -> {
            schema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attribute -> {
                attribute.getCompatibilityRules().add(ModCompatibilityRule.class);
            });
        });

        // Transitive to consumers, mod dependencies of this mod become
        // dependencies of downstream mods
        // TODO: this is likely not necessary and modClasspath can be built entirely from other sources
        var mod = configurations.create("mod", config -> {
            config.setCanBeResolved(false);
            config.setCanBeConsumed(false);
            config.setCanBeDeclared(true); // Requires Gradle 8.5 (build.gradle can add to this configuration)
            config.setTransitive(true);

            config.setDescription("NeoForge mod dependencies.");
            applyAttributes(config.getAttributes(), objectFactory, ModConfigurationAttributes);
        });

        // Not transitive, virtual "classpath" of mods that need to be located
        var modClasspath = configurations.create("modClasspath", config -> {
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setCanBeDeclared(false); // Requires Gradle 8.5 (build.gradle CANNOT add to this configuration)
            config.setTransitive(false);

            config.extendsFrom(mod);

            config.setDescription("Combined mod classpath.");
            applyAttributes(config.getAttributes(), objectFactory, ModConfigurationAttributes);
            // We're internal, so we don't need to show this to the user.
            config.setVisible(false);
        });
        // Mod dependencies let you use their classes/resources
        configurations.getByName("compileClasspath").extendsFrom(modClasspath);
        // Ensure that the mods are visible at runtime, too
        // TODO: is this necessary?
        configurations.getByName("runtimeClasspath").extendsFrom(modClasspath);


        // Turns the compiled classes and resources into a mod metadata for the
        // locator to load
        // TODO: this should pull info from gradle features (to enable optional deps)
        // TODO: the output file name should be based on the mod id
        var prepareModLocator = tasks.register("prepareModLocator",
            GenerateModLocatorInfoTask.class,
            t -> {
                t.setDescription("Prepares mod locator information for this project.");

                // TODO: This could likely be done another way, but it's an
                // easy fix to make this project's dependencies generate the task
                t.dependsOn(
                    modClasspath.getTaskDependencyFromProjectDependency(
                        true, "prepareModLocator"));

                // TODO: Right now we just use the main sourceset directly, but
                // this should use Gradle features to correctly support optional
                // features (read: inter-mod compat)
                t.getInputSources()
                    .from(project.getExtensions()
                        .findByType(JavaPluginExtension.class)
                        .getSourceSets()
                        .findByName("main")
                        .getOutput());

                t.getOutputFile().convention(project.getLayout()
                    .getBuildDirectory()
                    .file("mods/mymod.meta"));
            });

        // Add the mod metadata to the API and runtime elements so they can be
        // referenced elsewhere
        configurations.named("apiElements", config -> {
            // This is a secondary variant which provides a more specific match
            // for the mod metadata
            config.getOutgoing().getVariants().create(MODS_ELEMENTS, variant -> {
                variant.artifact(prepareModLocator, artifact -> {
                    // TODO: is this necessary? It should at least prevent
                    // jars/classes/resources from being resolved by accident.
                    artifact.setType("mod-metadata");
                });
                applyAttributes(variant.getAttributes(), objectFactory, ModVariantAttributes);
            });
        });
        configurations.named("runtimeElements", config -> {
            config.getOutgoing().getVariants().create(MODS_ELEMENTS, variant -> {
                variant.artifact(prepareModLocator, artifact -> {
                    // TODO: is this necessary? It should at least prevent
                    // jars/classes/resources from being resolved by accident.
                    artifact.setType("mod-metadata");
                });
                applyAttributes(variant.getAttributes(), objectFactory, ModVariantAttributes);
            });
        });

        // Make the run depend on the task which generates the metadata file
        project.getExtensions().<NamedDomainObjectContainer<Run>>configure(
            RunsConstants.Extensions.RUNS,
            runs -> runs.configureEach(
                run -> run.dependsOn(prepareModLocator)));

        // Add the system property to the run
        project.afterEvaluate(ModLocatorPlugin::afterEvaluate);
    }

    private static void afterEvaluate(Project project)
    {
        // Ensure we resolve the complete classpath, which contains all of our mod metadata files.
        var modClasspath = project.getConfigurations()
            .getByName("modClasspath").resolve();

        project.getExtensions().<NamedDomainObjectContainer<Run>>configure(
            RunsConstants.Extensions.RUNS,
            runs -> runs.configureEach(
                run -> configureRun(run, modClasspath)));
    }

    private static void configureRun(Run run, Set<File> files)
    {
        // Combine our dependency mod metadata file paths into one system
        // property
        var additionalMods = files.stream()
            .map(f -> f.toPath().toUri().toString())
            .collect(Collectors.joining(","));

        run.getSystemProperties()
            .put("finitereality.modlocator.additionalMods", additionalMods);
    }
}

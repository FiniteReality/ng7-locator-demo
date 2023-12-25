package finitereality.modlocator;

import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.LibraryElements;

public abstract class ModCompatibilityRule implements AttributeCompatibilityRule<LibraryElements>
{
    @Override
    public void execute(final CompatibilityCheckDetails<LibraryElements> details)
    {
        var consumerValue = details.getConsumerValue();
        var producerValue = details.getProducerValue();

        // If the consumer isn't looking for a specific library element, we are
        // *technically* compatible
        if (consumerValue == null)
        {
            details.compatible();
            return;
        }

        var consumerValueName = consumerValue.getName();
        var producerValueName = producerValue.getName();

        // TODO: is this necessary? We likely don't need to pass jars since they
        // get thrown on the classpath anyway
        if (ModLocatorPlugin.MODS_ELEMENTS.equals(consumerValueName)
            && LibraryElements.JAR.equals(producerValueName))
        {
            details.compatible();
            return;
        }
    }
}

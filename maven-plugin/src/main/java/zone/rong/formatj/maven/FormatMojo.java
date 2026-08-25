package zone.rong.formatj.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Formats the project's Java sources in place. */
@Mojo(
        name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES,
        requiresDependencyResolution = ResolutionScope.NONE, threadSafe = true)
public class FormatMojo extends AbstractFormatJMojo {

    @Override
    protected boolean checkOnly() {
        return false;
    }

}

package zone.rong.formatj.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Fails the build when a Java source is not formatted to the configured style. */
@Mojo(
        name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.NONE,
        threadSafe = true)
public class CheckMojo extends AbstractFormatJMojo {

    @Override
    protected boolean checkOnly() {
        return true;
    }

}

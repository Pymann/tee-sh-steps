package io.jenkins.plugins.teesteps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist;

/**
 * Jenkins only loads Groovy source files shipped in plugins when they are explicitly
 * allowed (SECURITY-2824). This allows exactly the implementation of teeSh/teeBat.
 */
@Extension
public class TeeShellAllowlist extends GroovySourceFileAllowlist {

    @Override
    public boolean isAllowed(String groovyResourceUrl) {
        return groovyResourceUrl.endsWith("/io/jenkins/plugins/teesteps/TeeShell.groovy");
    }
}

package io.jenkins.plugins.teesteps

import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Implementation of teeSh / teeBat.
 *
 *   teeSh script: 'make', file: 'build.log'
 *   teeSh(script: './test.sh', file: 'out/test.log', append: true, returnStatus: true)
 *   teeSh 'make', 'build.log'
 *
 * All parameters except 'file' and 'append' are passed to sh/bat unchanged, and the
 * return value (exit code with returnStatus, text with returnStdout) is passed through.
 */
class TeeShell implements Serializable {

    private static final long serialVersionUID = 1L

    private final CpsScript script
    private final String step

    TeeShell(CpsScript script, String step) {
        this.script = script
        this.step = step
    }

    def call(Map args) {
        if (args == null || !args.file) {
            throw new IllegalArgumentException("tee${step.capitalize()}: parameter 'file' is missing")
        }
        if (!args.script) {
            throw new IllegalArgumentException("tee${step.capitalize()}: parameter 'script' is missing")
        }
        Map stepArgs = new LinkedHashMap(args)
        String file = stepArgs.remove('file') as String
        boolean append = (stepArgs.remove('append') ?: false) as boolean

        return script.teeOutput(file: file, append: append) {
            script."${step}"(stepArgs)
        }
    }

    def call(String command, String file) {
        return call([script: command, file: file])
    }
}

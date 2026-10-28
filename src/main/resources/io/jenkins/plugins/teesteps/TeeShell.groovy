package io.jenkins.plugins.teesteps

import org.jenkinsci.plugins.workflow.cps.CpsScript

/**
 * Implementation of teeSh / teeBat.
 *
 *   teeSh script: 'make', file: 'build.log'
 *   teeSh(script: './test.sh', file: 'out/test.log', append: true, returnStatus: true)
 *   teeSh 'make', 'build.log'
 *
 * The build log shows the commands as usual ("+ cmd" for sh, "C:\ws>cmd" for bat), but by
 * default the file only receives the output of the commands. To achieve this, the command
 * trace/echo is prefixed with a marker (via PS4 for sh, PROMPT for bat) that TeeDecorator
 * strips from the build log and uses to keep those lines out of the file.
 * With includeCommands: true the commands are written to the file as well.
 * A sh script with its own shebang line is left untouched.
 *
 * All other parameters are passed to sh/bat unchanged, and the return value
 * (exit code with returnStatus, text with returnStdout) is passed through.
 */
class TeeShell implements Serializable {

    private static final long serialVersionUID = 1L

    /** Must match TeeDecorator.COMMAND_MARKER. */
    private static final String MARKER = '\u001eTEE\u001e'

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
        boolean includeCommands = (stepArgs.remove('includeCommands') ?: false) as boolean

        if (!includeCommands) {
            stepArgs.script = markCommands(stepArgs.script as String)
        }

        return script.teeOutput(file: file, append: append) {
            script."${step}"(stepArgs)
        }
    }

    def call(String command, String file) {
        return call([script: command, file: file])
    }

    private String markCommands(String body) {
        if (step == 'bat') {
            // cmd echoes each command after the prompt; mark the prompt.
            return '@prompt ' + MARKER + '$P$G\r\n' + body
        }
        // A script with its own shebang decides for itself.
        if (body.startsWith('#!')) {
            return body
        }
        // Same as Jenkins' default "sh -xe", but with a marked trace prefix. PS4 is set in the
        // script rather than via the environment, because bash ignores an inherited PS4 when
        // running as root.
        return '#!/bin/sh -e\n' + "PS4='+" + MARKER + " '\n" + 'set -x\n' + body
    }
}

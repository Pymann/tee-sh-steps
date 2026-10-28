package io.jenkins.plugins.teesteps;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

/**
 * Provides {@code teeSh} and {@code teeBat} as global pipeline variables.
 * The logic lives in {@code TeeShell.groovy} (same pattern as {@code docker} in the
 * docker-workflow plugin): it runs the real {@code sh}/{@code bat} step inside
 * {@code teeOutput}, so every sh/bat option (returnStatus, returnStdout, label,
 * encoding, ...) keeps working unchanged.
 */
public abstract class TeeShellVariable extends GlobalVariable {

    private static final String IMPL = "io.jenkins.plugins.teesteps.TeeShell";

    /** Name of the underlying step ({@code sh} or {@code bat}). */
    protected abstract String delegateStep();

    @NonNull
    @Override
    public Object getValue(@NonNull CpsScript script) throws Exception {
        Binding binding = script.getBinding();
        String name = getName();
        if (binding.hasVariable(name)) {
            return binding.getVariable(name);
        }
        Object value = script.getClass()
                .getClassLoader()
                .loadClass(IMPL)
                .getConstructor(CpsScript.class, String.class)
                .newInstance(script, delegateStep());
        binding.setVariable(name, value);
        return value;
    }

    @Extension
    public static final class TeeSh extends TeeShellVariable {
        @NonNull
        @Override
        public String getName() {
            return "teeSh";
        }

        @Override
        protected String delegateStep() {
            return "sh";
        }
    }

    @Extension
    public static final class TeeBat extends TeeShellVariable {
        @NonNull
        @Override
        public String getName() {
            return "teeBat";
        }

        @Override
        protected String delegateStep() {
            return "bat";
        }
    }
}

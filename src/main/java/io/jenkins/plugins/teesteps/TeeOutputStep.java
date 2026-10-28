package io.jenkins.plugins.teesteps;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Block step: everything written to the build log inside the block
 * ({@code sh}, {@code bat}, {@code powershell}, {@code echo}, ...) is also written to {@code file}.
 *
 * <pre>
 * teeOutput(file: 'logs/build.log', append: false) {
 *     sh 'make all'
 * }
 * </pre>
 */
public class TeeOutputStep extends Step implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String file;
    private boolean append;

    @DataBoundConstructor
    public TeeOutputStep(String file) {
        if (Util.fixEmptyAndTrim(file) == null) {
            throw new IllegalArgumentException("teeOutput: parameter 'file' must not be empty");
        }
        this.file = file;
    }

    public String getFile() {
        return file;
    }

    public boolean isAppend() {
        return append;
    }

    @DataBoundSetter
    public void setAppend(boolean append) {
        this.append = append;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, file, append);
    }

    static final class Execution extends StepExecution {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String file;
        private final boolean append;

        Execution(StepContext context, String file, boolean append) {
            super(context);
            this.file = file;
            this.append = append;
        }

        @Override
        public boolean start() throws Exception {
            StepContext ctx = getContext();
            FilePath workspace = ctx.get(FilePath.class);
            // FilePath.child resolves absolute paths as-is and relative paths against the workspace
            FilePath target = workspace.child(file);
            FilePath parent = target.getParent();
            if (parent != null) {
                parent.mkdirs();
            }
            // Create/truncate the file exactly once here; the decorator only ever appends.
            // That way it does not matter how often decorate() is invoked
            // (controller and agent side, after a controller restart, ...).
            if (!append || !target.exists()) {
                target.write("", StandardCharsets.UTF_8.name());
            }

            ctx.get(TaskListener.class).getLogger().println("[teeOutput] Also writing output to " + target.getRemote());

            ctx.newBodyInvoker()
                    .withContext(
                            TaskListenerDecorator.merge(ctx.get(TaskListenerDecorator.class), new TeeDecorator(target)))
                    .withCallback(BodyExecutionCallback.wrap(ctx))
                    .start();
            return false;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "teeOutput";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Write the output of the enclosed steps to the build log and to a file";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(FilePath.class, TaskListener.class);
        }
    }
}

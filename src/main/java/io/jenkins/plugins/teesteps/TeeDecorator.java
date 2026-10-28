package io.jenkins.plugins.teesteps;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.console.ConsoleNote;
import hudson.console.LineTransformationOutputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;

/**
 * Duplicates every log line into a file. The decorator is serializable and, for durable
 * tasks such as {@code sh}/{@code bat}, is applied directly on the agent. There the
 * {@link FilePath} is local, so the file is written without a round trip to the controller.
 */
final class TeeDecorator extends TaskListenerDecorator {

    @Serial
    private static final long serialVersionUID = 1L;

    private final FilePath target;

    TeeDecorator(FilePath target) {
        this.target = target;
    }

    @NonNull
    @Override
    public OutputStream decorate(@NonNull OutputStream logger) throws IOException, InterruptedException {
        OutputStream fileOut = target.act(new OpenForAppend());
        return new TeeStream(logger, fileOut);
    }

    /** Opens the file in append mode on the machine that holds it. */
    private static final class OpenForAppend extends MasterToSlaveFileCallable<OutputStream> {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public OutputStream invoke(File f, VirtualChannel channel) throws IOException {
            return new RemoteOutputStream(new FileOutputStream(f, true));
        }
    }

    /**
     * Writes each line unchanged to the build log and, without Jenkins console annotations
     * (hyperlinks etc.), to the file. Lines without annotations are copied byte for byte.
     */
    static final class TeeStream extends LineTransformationOutputStream {

        private final OutputStream logger;
        private final OutputStream file;

        TeeStream(OutputStream logger, OutputStream file) {
            this.logger = logger;
            this.file = file;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            logger.write(b, 0, len);
            if (ConsoleNote.findPreamble(b, 0, len) >= 0) {
                String plain = ConsoleNote.removeNotes(new String(b, 0, len, StandardCharsets.UTF_8));
                file.write(plain.getBytes(StandardCharsets.UTF_8));
            } else {
                file.write(b, 0, len);
            }
        }

        @Override
        public void flush() throws IOException {
            logger.flush();
            file.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                super.close(); // flushes a trailing line without newline
            } finally {
                try {
                    file.close();
                } finally {
                    logger.close();
                }
            }
        }
    }
}

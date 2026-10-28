package io.jenkins.plugins.teesteps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.FilePath;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TeeShellStepsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private String readWorkspaceFile(WorkflowJob p, String path) throws Exception {
        FilePath ws = j.jenkins.getWorkspaceFor(p);
        return ws.child(path).readToString();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void teeShWritesConsoleAndFile() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  teeSh script: 'echo hello-stdout; echo hello-stderr 1>&2', file: 'out/log.txt'
                  def rc = teeSh(script: 'echo second; exit 3', file: 'out/log.txt', append: true, returnStatus: true)
                  echo "rc=${rc}"
                  def txt = teeSh(script: 'echo captured', file: 'out/stdout.txt', returnStdout: true).trim()
                  echo "txt=${txt}"
                }
                """, true));
        WorkflowRun b = j.buildAndAssertSuccess(p);

        j.assertLogContains("hello-stdout", b);
        j.assertLogContains("hello-stderr", b);
        j.assertLogContains("rc=3", b);
        j.assertLogContains("txt=captured", b);

        String log = readWorkspaceFile(p, "out/log.txt");
        assertThat(log, containsString("hello-stdout"));
        assertThat(log, containsString("hello-stderr"));
        assertThat(log, containsString("second"));
        assertThat(log, not(containsString("\u001B[8mha:"))); // no console annotations
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void shortSyntax() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "short");
        p.setDefinition(new CpsFlowDefinition("node { teeSh 'echo short-form', 'short.log' }", true));
        j.buildAndAssertSuccess(p);
        assertThat(readWorkspaceFile(p, "short.log"), containsString("short-form"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void appendFalseTruncatesFile() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "truncate");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  teeSh script: 'echo first-run', file: 't.log'
                  teeSh script: 'echo second-run', file: 't.log'
                }
                """, true));
        j.buildAndAssertSuccess(p);
        String log = readWorkspaceFile(p, "t.log");
        assertThat(log, containsString("second-run"));
        assertThat(log, not(containsString("first-run")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void failingScriptStillWritesFile() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "fail");
        p.setDefinition(
                new CpsFlowDefinition("node { teeSh script: 'echo before-failure; exit 1', file: 'f.log' }", true));
        WorkflowRun b = j.buildAndAssertStatus(Result.FAILURE, p);
        j.assertLogContains("before-failure", b);
        assertThat(readWorkspaceFile(p, "f.log"), containsString("before-failure"));
    }

    @Test
    void missingFileParameterFails() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "nofile");
        p.setDefinition(new CpsFlowDefinition("node { teeSh script: 'echo x' }", true));
        WorkflowRun b = j.buildAndAssertStatus(Result.FAILURE, p);
        j.assertLogContains("parameter 'file' is missing", b);
    }

    @Test
    void teeOutputBlock() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "block");
        p.setDefinition(new CpsFlowDefinition("node { teeOutput(file: 'all.log') { echo 'from-echo' } }", true));
        j.buildAndAssertSuccess(p);
        assertThat(readWorkspaceFile(p, "all.log"), containsString("from-echo"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void worksOnAgent() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        WorkflowJob p = j.createProject(WorkflowJob.class, "agent");
        p.setDefinition(new CpsFlowDefinition(
                "node('" + agent.getNodeName() + "') { teeSh script: 'echo on-agent', file: 'agent.log' }", true));
        WorkflowRun b = j.buildAndAssertSuccess(p);
        j.assertLogContains("on-agent", b);
        FilePath ws = agent.getWorkspaceFor(p);
        assertThat(ws.child("agent.log").readToString(), containsString("on-agent"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void teeBatWritesConsoleAndFile() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "bat");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  def rc = teeBat(script: '@echo hello-bat\\r\\n@exit /b 4', file: 'out\\\\bat.log', returnStatus: true)
                  echo "rc=${rc}"
                }
                """, true));
        WorkflowRun b = j.buildAndAssertSuccess(p);
        j.assertLogContains("hello-bat", b);
        j.assertLogContains("rc=4", b);
        assertThat(readWorkspaceFile(p, "out/bat.log"), containsString("hello-bat"));
    }
}

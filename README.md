# Tee Shell Steps

## Introduction

This plugin adds pipeline steps that behave like `sh` and `bat`, but write the
command's stdout/stderr **to the build log and to a file at the same time** –
like the Unix `tee` command.

| Step        | Kind             | Description                                                          |
|-------------|------------------|----------------------------------------------------------------------|
| `teeSh`     | like `sh`        | runs `sh`, output additionally goes to `file`                        |
| `teeBat`    | like `bat`       | runs `bat`, output additionally goes to `file`                       |
| `teeOutput` | block step       | everything inside the block (also `powershell`, `echo`, …) goes to `file` as well |

Typical use cases: archiving the full output of a single build step, handing the
log of a test run to a report parser, or attaching tool output to a notification –
without hiding it from the build log.

## Getting started

### Scripted Pipeline

```groovy
node {
    // simple form
    teeSh script: 'make all', file: 'logs/build.log'

    // short form: command, file
    teeSh 'make test', 'logs/test.log'

    // every sh option keeps working and the return value is passed through
    def rc = teeSh(script: './run-tests.sh', file: 'logs/test.log',
                   append: true, returnStatus: true, label: 'Tests')
    if (rc != 0) {
        unstable("Tests failed with exit code ${rc}")
    }

    // Windows
    teeBat script: 'build.cmd', file: 'logs\\build.log'

    // several steps into one file
    teeOutput(file: 'logs/all.log') {
        sh 'make'
        sh 'make install'
    }

    archiveArtifacts 'logs/*.log'
}
```

### Declarative Pipeline

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                teeSh script: 'make all', file: 'logs/build.log'
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'logs/*.log', allowEmptyArchive: true
        }
    }
}
```

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `file`    | yes      | Path relative to the workspace (absolute paths are allowed). Missing directories are created. |
| `append`  | no       | `true` appends to an existing file; default `false` truncates it when the step starts. |
| anything else | –    | `script`, `returnStatus`, `returnStdout`, `label`, `encoding`, … are passed to `sh`/`bat` unchanged. |

### Behavior

- Exit codes behave exactly like `sh`/`bat`: a non-zero exit fails the build unless
  `returnStatus: true` is used. The file is complete in either case.
- stdout and stderr both end up in the file, exactly as they appear in the build log.
- Jenkins console annotations (hyperlinks, markers) are stripped from the file.
- With `returnStdout: true`, stdout is captured and – just like with `sh` – does not
  appear in the build log, so it is not written to the file either; stderr still is.
- On agents the file is written directly on the agent, not through the controller.
- Parallel branches writing to the **same** file may interleave lines – use one file per branch.

## How it works

`teeOutput` installs a `TaskListenerDecorator` on the log stream of its body.
`teeSh`/`teeBat` are global variables that call the real `sh`/`bat` step inside
`teeOutput`, so they inherit all of `sh`/`bat`'s options, durability and
resume-after-restart behavior instead of reimplementing them.

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md).

## License

Licensed under MIT, see [LICENSE](LICENSE.md).

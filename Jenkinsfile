pipeline {
    agent none
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 8, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '8', daysToKeepStr: '14'))
    }
    stages {
        stage('Required source and native validation') {
            parallel {
                stage('Linux policy contracts') {
                    agent { label 'ci-general && ci-linux-container && ci-memory-small' }
                    steps {
                        script {
                            if (env.CHANGE_ID || env.BRANCH_NAME != 'main') { error('Reviewed main only') }
                            checkout scm
                        }
                        sh '''#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-evidence/policy
python3 -m unittest discover -s scripts/tests -v 2>&1 | tee ci-evidence/policy/contracts.log
git rev-parse HEAD > ci-evidence/policy/source-sha.txt
'''
                    }
                    post {
                        always {
                            script {
                                try { archiveArtifacts artifacts: 'ci-evidence/policy/**', allowEmptyArchive: true }
                                finally { cleanWs(deleteDirs: true, disableDeferredWipeout: true) }
                            }
                        }
                    }
                }
                stage('Native Forge compilation and package') {
                    agent { label 'ci-native-gradle-ready && ci-windows-native' }
                    options { timeout(time: 80, unit: 'MINUTES') }
                    steps {
                        script {
                            if (env.CHANGE_ID || env.BRANCH_NAME != 'main') { error('Reviewed main only') }
                            powershell '''$ErrorActionPreference = 'Stop'
$free = [math]::Floor((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024)
$required = if ($env:NODE_NAME -eq 'on-prem-1-windows-ci') { 4096 } else { 2048 }
if ($free -lt $required) { throw "Insufficient RAM for native CI ($free MiB available; require $required MiB)" }
'''
                            checkout scm
                            env.CI_SPICE_CACHE = pwd(tmp: true) + '/spice-gradle-cache'
                            withEnv(['GRADLE_USER_HOME=' + env.CI_SPICE_CACHE]) {
                                powershell '''$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME, 'ci-evidence/native' | Out-Null
$homePath = $env:JAVA_HOME_17.Replace([char]92,[char]47)
@("org.gradle.java.installations.paths=$homePath", 'org.gradle.java.installations.auto-download=false') | Set-Content -LiteralPath (Join-Path $env:GRADLE_USER_HOME 'gradle.properties') -Encoding ASCII
'''
                            }
                            withEnv(['JAVA_HOME=' + env.JAVA_HOME_17, 'PATH+JDK=' + env.JAVA_HOME_17 + '\\bin',
                                     'GRADLE_USER_HOME=' + env.CI_SPICE_CACHE,
                                     'GRADLE_OPTS=-Dorg.gradle.workers.max=1 -Dorg.gradle.jvmargs=-Xmx1024m']) {
                                bat '''@echo off
call gradlew.bat test build jarJar --no-daemon --max-workers=1 --stacktrace > ci-evidence\\native\\build.log 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
type ci-evidence\\native\\build.log
exit /b %BUILD_RESULT%
'''
                            }
                        }
                        powershell '''$ErrorActionPreference = 'Stop'
$jars = @(Get-ChildItem -Path 'build/libs' -Filter '*.jar' -File | Where-Object { $_.Name -notmatch '-(slim|sources|javadoc)[.]jar$' })
$reports = @(Get-ChildItem -Path 'build/test-results/test' -Filter '*.xml' -File)
if (-not $jars.Count -or -not $reports.Count) { throw 'Production JAR or JUnit evidence missing' }
$sha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[a-f0-9]{40}$') { throw 'Source revision unavailable' }
$files = @($jars | ForEach-Object { @{ name=$_.Name; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } })
@{ source_sha=$sha; production_jars=$files } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath 'ci-evidence/native/production.json' -Encoding UTF8
'''
                    }
                    post {
                        always {
                            script {
                                try {
                                    junit testResults: 'build/test-results/test/*.xml', allowEmptyResults: true
                                    archiveArtifacts artifacts: 'ci-evidence/native/**,build/libs/*.jar,build/reports/**', allowEmptyArchive: true, fingerprint: true
                                } finally {
                                    try {
                                        if (env.CI_SPICE_CACHE) { dir(env.CI_SPICE_CACHE) { deleteDir() } }
                                    } finally {
                                        cleanWs(deleteDirs: true, disableDeferredWipeout: true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Library('nstut-ci') _

// Reviewed main only. Build and unit tests are portable to the native Windows
// Jenkins pool; GameTests, PR validation and tagged publishing stay hosted.
nstutNativeGradle(
    agentLabel: 'ci-native-gradle-ready && ci-windows-native',
    targets: [
        [
            id: 'forge1201',
            name: 'Forge 1.20.1 policy contracts, JUnit and reobfuscated package',
            java: 17,
            minutes: 80,
            tasks: ['ciContracts', 'test', 'build', 'jarJar']
        ]
    ]
)

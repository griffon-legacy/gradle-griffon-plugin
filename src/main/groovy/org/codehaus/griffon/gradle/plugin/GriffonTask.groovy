package org.codehaus.griffon.gradle.plugin

import org.codehaus.griffon.launcher.GriffonLauncher
import org.codehaus.griffon.launcher.NameUtils
import org.codehaus.griffon.launcher.RootLoader
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskAction

class GriffonTask extends DefaultTask {

    private String command = null
    private String args = null
    private String env = null

    private boolean useRuntimeClasspathForBootstrap = false

    String griffonHome = null

    void command(String command) {
        setCommand(command)
    }

    void setCommand(String command) {
        this.command = command
    }

    String getCommand() {
        this.command
    }

    void args(String args) {
        setArgs(args)
    }

    void setArgs(String args) {
        this.args = args
    }

    String getArgs() {
        this.args
    }

    void env(String env) {
        setEnv(env)
    }

    void setEnv(String env) {
        this.env = env
    }

    String getEnv() {
        this.env
    }

    void configuration(Configuration configuration) {
        configurations(configuration)
    }

    void configuration(String configuration) {
        configurations(configuration)
    }

    void configurations(Configuration[] configurations) {
        configurations.each {
            dependsOn it.getTaskDependencyFromProjectDependency(true, "jar")
        }
    }

    void configurations(String[] configurations) {
        this.configurations configurations.collect { project.configurations[it] } as Configuration[]
    }

    void useRuntimeClasspathForBootstrap(boolean flag) {
        setUseRuntimeClasspathForBootstrap(flag)
    }

    void setUseRuntimeClasspathForBootstrap(boolean flag) {
        this.useRuntimeClasspathForBootstrap = flag
    }

    boolean isUseRuntimeClasspathForBootstrap() {
        this.useRuntimeClasspathForBootstrap
    }

    @TaskAction
    def executeCommand() {
        verifyGriffonDependencies()

        def launchArgs = [NameUtils.toScriptName(effectiveCommand), args ?: ""]
        if (env) launchArgs << end
        def result = createLauncher().launch(* launchArgs)

        if (result != 0) {
            throw new RuntimeException("[GriffonPlugin] Griffon returned non-zero value: " + result)
        }
    }

    // TODO - use a convention for this
    String getEffectiveCommand() {
        command ?: name
    }

    String getEffectiveGriffonHome() {
        griffonHome ?: (project.hasProperty('griffonHome') ? project.griffonHome : null)
    }

    protected void verifyGriffonDependencies() {
        def runtimeDeps = project.configurations.runtime.resolvedConfiguration.resolvedArtifacts
        def griffonDep = runtimeDeps.find { it.resolvedDependency.moduleGroup == 'org.codehaus.griffon' && it.name.startsWith('griffon-') }
        if (!griffonDep) {
            throw new RuntimeException("[GriffonPlugin] Your project does not contain any 'griffon-*' dependencies in 'compile' or 'runtime'.")
        }

        /*
        def loggingDep = runtimeDeps.find { it.resolvedDependency.moduleGroup == 'org.slf4j' && it.name.startsWith('slf4j-') }
        if (!loggingDep) {
            throw new RuntimeException("[GriffonPlugin] Your project does not contain an SLF4J logging implementation dependency.")
        }
        */
    }

    protected void addToolsJarIfNecessary(Collection<URL> classpath) {
        // Add the "tools.jar" to the classpath so that the Griffon
        // scripts can run native2ascii. First assume that "java.home"
        // points to a JRE within a JDK.
        def javaHome = System.getProperty("java.home");
        def toolsJar = new File(javaHome, "../lib/tools.jar");
        if (!toolsJar.exists()) {
            // The "tools.jar" cannot be found with that path, so
            // now try with the assumption that "java.home" points
            // to a JDK.
            toolsJar = new File(javaHome, "tools.jar");
        }

        // There is no tools.jar, so native2ascii may not work. Note
        // that on Mac OS X, native2ascii is already on the classpath.
        if (!toolsJar.exists() && !System.getProperty('os.name') == 'Mac OS X') {
            project.logger.warn "[GriffonPlugin] Cannot find tools.jar in JAVA_HOME, so native2ascii may not work."
        }

        if (toolsJar.exists()) {
            classpath << toolsJar.toURI().toURL()
        }
    }

    Configuration getEffectiveBootstrapConfiguration() {
        project.configurations.bootstrap
    }

    protected Collection<URL> getEffectiveBootstrapClasspath() {
        def classpath = effectiveBootstrapConfiguration.files.collect { it.toURI().toURL() }
        addToolsJarIfNecessary(classpath)
        classpath
    }

    protected GriffonLauncher createLauncher() {
        def rootLoader = new RootLoader(getEffectiveBootstrapClasspath() as URL[], ClassLoader.systemClassLoader)
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(rootLoader)
        def griffonLauncher = new GriffonLauncher(rootLoader, effectiveGriffonHome, project.projectDir.absolutePath)
        griffonLauncher.dependenciesExternallyConfigured = true
        applyProjectLayout(griffonLauncher)
        Class buildListenerClass = groovyClassLoader.parseClass('''
        import org.codehaus.griffon.resolve.IvyDependencyManager
        class GradleGriffonBuildListener implements griffon.build.GriffonBuildListener {
            def griffonLauncher

            void receiveGriffonBuildEvent(String eventName, Object... args) {
                if ('ResolveDependenciesEnd' != eventName) return

                def configurations = [
                    build: IvyDependencyManager.BUILD_CONFIGURATION,
                    runtime: IvyDependencyManager.RUNTIME_CONFIGURATION,
                    compile: IvyDependencyManager.COMPILE_CONFIGURATION,
                    test: IvyDependencyManager.TEST_CONFIGURATION
                ]
                configurations.each { gconf, iconf ->
                    def report = griffonLauncher.buildSettings.dependencyManager.resolveDependencies(iconf)
                    def jars = report.getArtifactsReports(null, false).findAll {it.downloadStatus.toString() != 'failed'}.localFile
                    def dependencyContainer = griffonLauncher."${gconf}Dependencies"
                    for(jar in jars) {
                        if (!dependencyContainer.find { it.name == jar.name }) {
                            dependencyContainer << jar
                        }
                    }
                }
                // update runtime & test with compile
                griffonLauncher.compileDependencies.each { dep ->
                    if (!griffonLauncher.runtimeDependencies.find { it.name == dep.name }) {
                        griffonLauncher.runtimeDependencies << dep
                    }
                    if (!griffonLauncher.testDependencies.find { it.name == dep.name }) {
                        griffonLauncher.testDependencies << dep
                    }
                }
                // update test with runtime
                griffonLauncher.runtimeDependencies.each { dep ->
                    if (!griffonLauncher.testDependencies.find { it.name == dep.name }) {
                        griffonLauncher.testDependencies << dep
                    }
                }
            }
        }
        ''')
        def buildListener = buildListenerClass.newInstance()
        buildListener.griffonLauncher = griffonLauncher
        griffonLauncher.addBuildEventListener(buildListener)
        System.setProperty('griffon.command.launcher', 'gradle')
        griffonLauncher
    }

    protected void applyProjectLayout(GriffonLauncher griffonLauncher) {
        griffonLauncher.buildDependencies = project.configurations.bootstrap.files as List
        griffonLauncher.compileDependencies = project.configurations.compile.files as List
        griffonLauncher.testDependencies = project.configurations.test.files as List
        griffonLauncher.runtimeDependencies = project.configurations.runtime.files as List
        String userHome = System.getProperty('user.home')
        griffonLauncher.griffonWorkDir = new File("${userHome}/.griffon/${project.griffonVersion}")
        griffonLauncher.projectWorkDir = new File("${griffonLauncher.griffonWorkDir}/projects/${project.name}")
        griffonLauncher.classesDir = new File("${griffonLauncher.projectWorkDir}/classes")
        griffonLauncher.testClassesDir = new File("${griffonLauncher.projectWorkDir}/test-classes")
        griffonLauncher.resourcesDir = new File("${griffonLauncher.projectWorkDir}/resources")
        griffonLauncher.testReportsDir = new File("${project.buildDir}/test-reports")
        griffonLauncher.projectPluginsDir = new File("${griffonLauncher.projectWorkDir}/plugins")
    }

    protected void logClasspaths() {
        project.logger.with {
            if (infoEnabled) {
                info "Classpath for Griffon root loader:\n  ${classpath.join('\n  ')}"
                info "Compile classpath:\n  ${project.configurations.compile.files.join('\n  ')}"
                info "Test classpath:\n  ${project.configurations.test.files.join('\n  ')}"
                info "Runtime classpath:\n  ${project.configurations.runtime.files.join('\n  ')}"
            }
        }
    }

    protected boolean isPluginProject() {
        project.projectDir.listFiles({ dir, name -> name ==~ /.*GriffonPlugin.groovy/} as FilenameFilter)
    }

    protected boolean isArchetypeProject() {
        project.projectDir.listFiles({ dir, name -> name ==~ /.*GriffonArchetype.groovy/} as FilenameFilter)
    }
}

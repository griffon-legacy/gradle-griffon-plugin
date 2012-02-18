package org.codehaus.griffon.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class GriffonPlugin implements Plugin<Project> {
    static public final GRIFFON_TASK_PREFIX = "griffon-"

    void apply(Project project) {
        if (!project.hasProperty("griffonVersion")) {
            throw new RuntimeException("[GriffonPlugin] the 'griffonVersion' project property is not set - you need to set this before applying the plugin")
        }
        
        project.configurations {
            compile
            runtime.extendsFrom compile
            test.extendsFrom compile
            
            bootstrap//.extendsFrom logging
            bootstrapRuntime.extendsFrom bootstrap, runtime
        }

        project.repositories {
            mavenRepo name: 'Griffon - Codehaus',       url: 'http://repository.codehaus.org'
            mavenRepo name: 'Griffon - SpringSource',   url: 'http://repository.springsource.com/maven/bundles/release'
            mavenRepo name: 'Griffon - Sonatype',       url: 'http://repository.sonatype.org/content/groups/public'
            mavenRepo name: 'Griffon - Grails Central', url: 'http://repo.grails.org/grails/core/'
        }
        
        project.dependencies {
            ["rt", "cli", "scripts", "resources"].each {
                bootstrap("org.codehaus.griffon:griffon-$it:${project.griffonVersion}") {
                    // exclude group: "org.slf4j"
                }
            }
            
            bootstrap "org.apache.ivy:ivy:2.1.0"
        }
        
        project.task("init", type: GriffonTask) {
            onlyIf {
                !project.file("application.properties").exists() && !project.file("griffon-app").exists()
            }
            
            doFirst {
                if (project.version == "unspecified") {
                    throw new RuntimeException("[GriffonPlugin] Build file must specify a 'version' property.")
                }
            }

            def projName = project.hasProperty("args") ? project.args : project.projectDir.name
            
            command "create-app"
            args "--inplace --appVersion=$project.version $projName"
        }

        project.task("clean", type: GriffonTask, overwrite: true)

        project.task("test", type: GriffonTask, overwrite: true) {
            command "test-app"
            configurations "compile", "test"
        }

        project.task("assemble", type: GriffonTask, overwrite: true) {
            command "package"
            configuration "compile"
        }
        
        // Convert any task executed from the command line 
        // with the special prefix into the Griffon equivalent command.
        project.gradle.afterProject { p, ex ->
            if (p == project) {
                project.tasks.addRule("Griffon command") { String name ->
                    if (name.startsWith(GRIFFON_TASK_PREFIX)) {
                        project.task(name, type: GriffonTask) {
                            command name - GRIFFON_TASK_PREFIX
                            
                            // We don't really know what configurations are necessary, but compile is a good default
                            configuration "compile"
                        }
                    }
                }
            }
        }
    }
}

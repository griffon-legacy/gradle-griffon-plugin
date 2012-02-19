Griffon Gradle Plugin
====================

This plugin for Gradle allows you to build Griffon projects. To use it, simply include the required JARs via `buildscript {}` and 'apply' the plugin:

	buildscript {
	    repositories {
	        mavenCentral()
	        mavenRepo name: 'Codehaus',       url: 'http://repository.codehaus.org'
	        mavenRepo name: 'SpringSource',   url: 'http://repository.springsource.com/maven/bundles/release'
	        mavenRepo name: 'Sonaytpe',       url: 'http://repository.sonatype.org/content/groups/public'
	        mavenRepo name: 'Grails Central', url: 'http://repo.grails.org/grails/core'
	        mavenRepo name: 'JavaNet',        url: 'http://download.java.net/maven/2'
	    }

	    dependencies {
	        classpath('org.codehaus.griffon:gradle-griffon-plugin:1.1.0')
	        classpath('org.codehaus.griffon:griffon-scripts:0.9.5')
	    }
	}

	griffonVersion = '0.9.5'
	version = '0.1'

	apply plugin: 'griffon'

	repositories {
	    mavenCentral()
	    mavenRepo name: 'Codehaus', url: 'http://repository.codehaus.org/'
	}

	dependencies {
	    compile "org.codehaus.griffon:griffon-rt:$griffonVersion"
	}

You must include a version of the 'griffon-scripts' artifact in the 'classpath' configuration. You should also add whichever Griffon artifacts you need. 'griffon-rt' will give you everything you need for a standard Griffon application.

Once you have this build file, you can create a Griffon application with the 'init' task:

    gradle init

Other standard tasks include:

* clean
* compile
* test
* assemble

You can also access any Griffon command by prefixing it with 'griffon-'. For example, to run the application:

    gradle griffon-run-app

If you want to pass in some arguments, you can do so via the `args` project property:

    gradle -Pargs='-group=Dialog' griffon-create-mvc

You can also change the environment via the `env` project property:

    gradle -Penv=prod griffon-run-app

*Warning* The plugin does not allow you to execute multiple tasks in one command line. So `gradle clean test` will fail even if `clean` and `test` individually succeed.

Troubleshooting
===============

* Caused by: org.apache.tools.ant.BuildException: java.lang.NoClassDefFoundError: org/apache/commons/cli/Options

  This happens if your project depends on the 'groovy' JAR rather than 'groovy-all'. Change your dependency to the latter and all will be well.

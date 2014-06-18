# Jenkins Literate Plugin

This plugin exposes a new project type in Jenkins, the literate multi-branch project. 
This project type expects that the build steps of a job are described in the `README.md`
at the root of the project's SCM checkout (just like this file). It will look for branches
that contain a special marker file (which by default is `.cloudbees.md`) if that file is
in the root of a branch, then it will create a sub-project for building that branch. The
marker file can use one of two different methods to indicate the build steps:

1. An empty marker file indicates that the build steps are defined in `README.md`.
2. A marker file with content will contain the build steps. 
 
In each case the content will be searched for specific sections.

The first section that contains the word `build` (ignore case, and this default can be changed)
will be considered the definition of the build steps.
 
The first section that contains the word `environments` (ignore case, and this default can be 
changed) will be considered the definition of the environments against which the build
steps will be verified.
 
The simplest literate project, a hello world if you like, would be
 
    # Hello world literate project
   
    This is a hello world literate project
   
    # Build
   
    Now let's say hello
   
        echo Hello world
       
All the literate code blocks in the `build` section are treated as the actual build steps
of the project.

If we wanted to have a matrix style project that said hello on multiple operating systems,
we could specify an `environments` section, e.g.

    # Hello world literate project
   
    This is a hello world literate project

    # Environments
    
    * `linux`
    * `windows`
    * `osx`

    # Build
   
    Now let's say hello
   
        echo Hello world

We use bullet points to indicate each environment and then in code snippet blocks we 
provide the node labels/tool installer names that make up the environment to execute on.

This gives rise to the next lession, e.g. how do you handle the build commands being different
for different environments (also known as the "Windows problem")

Well you just need to provide bullet points in the `build` section with the appropriate 
code snippets to match the environments to be built, e.g.

    # Hello world literate project
   
    This is a hello world literate project

    # Environments
    
    * `linux`
    * `windows`
    * `osx`

    # Build
   
    * With `windows` we say hello with some quotes
    
            @echo "Hello world"
            
    * On `linux` we can say hello like this
   
            echo 'Hello world'
            
    * Once we provide any bullet points we must ensure that there are bullet points to
      match every target environment, so we need to say what happens on `osx`
      
            echo 'Hello world'
            
In the environments section you can nest bullets to save having to be verbose

    # Maven and Java
    
    Lets query the Maven and Java versions on multiple operating systems
    
    # Environments
    
    * `linux`
        * `java-1.6`
            * `maven-2.2.1`
            * `maven-3.0.4`
            * `maven-3.1.0`
        * `java-1.7`
            * `maven-2.2.1`
            * `maven-3.0.4`
            * `maven-3.0.5`
            * `maven-3.1.0`
    * `osx`
        * `java-1.6`
            * `maven-3.0.4`
            * `maven-3.0.5`
        * `java-1.7`
            * `maven-3.0.5`
            * `maven-3.1.0`
    * `windows`
        * `java-1.6`
            * `maven-3.0.3`
            * `maven-3.0.4`
        * `java-1.7`
            * `maven-2.2.1`
            * `maven-3.1.0`

    # Build
    
    The first best (i.e. the one with the most matches) match wins, thus the windows
    problem is solved and we ensure there is a match for every environment by matching
    against the java version.
    
    * on `windows` 
    
            call mvn.bat -version
            
    * on `java-1.6`
    
            mvn -version
            
    * on `java-1.7`
    
            mvn -version
            
The above would give you a build which executes on 15 different environments.  

The Jenkins specific Publishers and Notifiers that are to be run after the build steps can also
be specified by creating a `.jenkins` directory in the root of your project's SCM.

There are two ways to specify a Publisher or a Notifier's configuration. If a Publisher/Notifier
implements a special helper interface (`org.cloudbees.literate.jenkins.publishers.Agent`)
then it can provide its own DSL for configuring the Publisher/Notifier. For example the 
literate plugin provides DSLs for the Artifact Archiving and JUnit test reports. In both
cases the DSL is just a simple text file with lines of ANT-style glob paths. Most Maven
based projects can get JUnit test results by creating a `.jenkins/junit.lst` file with the
following content:

    **/target/surefire-reports/*.xml
    **/target/failsafe-reports/*.xml
    
Similarly most of the needs of Maven based projects for archiving artifacts can be met with
a `.jenkins/artifacts.lst` file with the following content:

    **/target/*.?ar
    **/target/*.zip
    
If the Publisher/Notifier does not implement the `Agent` interface, or if you are an XML fanboy,
you can just put in the Jenkins serialized form of the Publisher/Notifier to have that
be used. So for example instead of `.jenkins/junit.lst` we could have created a 
`.jenkins/hudson.tasks.junit.JUnitResultArchiver.xml` file with the following content:

    <hudson.tasks.junit.JUnitResultArchiver>
      <testResults>**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml</testResults>
      <keepLongStdio>true</keepLongStdio>
      <testDataPublishers/>
    </hudson.tasks.junit.JUnitResultArchiver>
    
If you provide both forms one will get picked up and the other ignored, so don't be silly.

The plugin adds an `Export as literate-style` action to Free-style projects that lets you
select the Publishers/Notifiers to export and creates a `.zip` file to download with
the `.jenkins` directory containing the equivalent Literate-style configuration, so if you
have a Jenkins job that has Publishers/Notifiers very close to the way you want them it should
be easy to grab their configuration.

See also this [plugin's wiki page][wiki]

# Environment

The following build environment is required to build this plugin

* `java-1.6` and `maven-3.0.5`

# Build

To build the plugin locally:

    mvn clean verify

# Release

To release the plugin:

    mvn release:prepare release:perform -B

# Test local instance

To test in a local Jenkins instance

    mvn hpi:run

  [wiki]: http://wiki.jenkins-ci.org/display/JENKINS/Literate+Plugin

import groovy.text.SimpleTemplateEngine

args = [/C:\dev\repositories\salog\salog.parties\salog.parties.server/].toArray()

println "Migrating maven Modules from ${args[0]}}"

def rootDir=new File(args[0]).absolutePath

@Grab(group='org.apache.maven', module='maven-ant-tasks', version='2.0.9', transitive=false)
def loadDependencies() {}

String file = rootDir + /\pom.xml/

def m2Home = System.getenv("M2_HOME")
println "Using mvn from ${m2Home}"

def ant = new AntBuilder()
ant.typedef(resource:"org/apache/maven/artifact/ant/antlib.xml")



def pom = ant.pom(id:file, file: file)
import groovy.util.XmlSlurper
pom.mavenProject.buildPlugins.each { org.apache.maven.model.Plugin p ->
    if (p.key == 'org.codehaus.mojo:build-helper-maven-plugin') {
        println p.dump()
        p.executions.each {  org.apache.maven.model.PluginExecution e ->
            def rootNode = new XmlSlurper().parseText(e.configuration.toString())
            rootNode.sources.source.each {
                println it
            }
        }
    }
}
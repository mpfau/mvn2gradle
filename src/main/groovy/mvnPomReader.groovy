import groovy.text.SimpleTemplateEngine
import groovy.util.XmlSlurper

/**
 * Config section
 */
if (args.length == 0) {
    println 'Please provide the path to your root maven module'
    System.exit(0)
}
println "Migrating maven Modules from ${args[0]}}"


def rootDir=new File(args[0]).absolutePath

@Grab(group='org.apache.maven', module='maven-ant-tasks', version='2.0.9', transitive=false)
def loadDependencies() {}

def importer = MavenImporter.instance
importer.run(rootDir + /\pom.xml/)

class MavenImporter {
    final static MavenImporter instance = new MavenImporter()
    def ant = new AntBuilder()
    // Key is the artifactId
    def Map<String, Module> modules = new HashMap()
    def Module root
    
    private MavenImporter() {
        ant.typedef(resource:"org/apache/maven/artifact/ant/antlib.xml")
    }
        
    private Module parsePom(String file) {
        println 'adding POM: ' + file
        def pom = ant.pom(id:file, file: file)
        def module = getModule(pom.mavenProject.artifact.artifactId)
        module.project = pom.mavenProject
        if (pom.mavenProject.hasParent()) {
            module.parent = getModule(pom.mavenProject.parent.artifactId)
        }
        for(dependency in pom.dependencies) {
            module.dependencies << new Dependency(mavenDependency: dependency)
        }
        for(managedVersionDependency in pom.mavenProject.managedVersionMap.entrySet()) {
            module.managedArtifactVersions << managedVersionDependency
        }
        for(repository in pom.mavenProject.remoteArtifactRepositories) {
            module.repositories << repository
        }
        // addAdditionalSourceFolders
        pom.mavenProject.buildPlugins.each { org.apache.maven.model.Plugin p ->
            if (p.key == 'org.codehaus.mojo:build-helper-maven-plugin') {
                p.executions.each {  org.apache.maven.model.PluginExecution e ->
                    def rootNode = new XmlSlurper().parseText(e.configuration.toString())
                    rootNode.sources.source.each {
                        println "adding source Folder ${it}"
                        module.additionalSourceDirectories << it.toString()
                    }
                }
            }
        }
        for(subModule in pom.modules) {
            module.modules << parsePom(new File(module.getBasedir(), subModule + "/pom.xml").absolutePath)
        }
        return module
    }
    
    Module getModule(String artifact) {
        if (!modules.containsKey(artifact)) {
            modules[artifact] = new Module()
        }
        return modules[artifact]
    }
    
    private void writeGradleFiles() {
        String include = 'include '
        for(module in modules.values()) {
            if (module != root) {
                include += """"${module.name}", """
            }
        }
        include = include.substring(0, include.length() - 2)
        def settings = new File(root.basedir.absolutePath, "/settings.gradle")
        settings.text = include 
        println "wrote settings.gradle at ${settings.absolutePath}"
        
        def modulesWithoutRoot = modules.values()
        modulesWithoutRoot.remove(root)
        def binding= ['root': root, 'modules': modulesWithoutRoot]
        def engine = new SimpleTemplateEngine()
        def template = engine.createTemplate(new File(/build.gradle.template/).text).make(binding)
        def buildScript = new File(root.basedir.absolutePath, '/build.gradle')
        buildScript.text = template.toString()
        println "wrote build.gradle to ${buildScript.absolutePath}"
    }
    
    void run(String file) {
        root = parsePom(file)
        writeGradleFiles()
    }
}

class Module {
    org.apache.maven.project.MavenProject project
    Module parent
    List<Module> modules = []
    List<Dependency> dependencies = []
    List<org.apache.maven.artifact.DefaultArtifact> managedArtifactVersions = []
    List<org.apache.maven.artifact.repository.DefaultArtifactRepository> repositories = []
    List<String> additionalSourceDirectories = []
    
    File getBasedir() {
        return project.basedir
    }
    
    String getGroup() {
        return project.artifact.groupId
    }
    
    String getName() {
        if (hasParent() && !parent.isRoot()) {
            return "${parent.name}:${project.artifact.artifactId}"
        }
        return project.artifact.artifactId
    }
    
    String getVersion() {
        return project.artifact.version
    }
    
    List<String> getCompileSourceRoots() {
        return project.compileSourceRoots
    }
    
    List<String> getTestCompileSourceRoots() {
        return project.testCompileSourceRoots
    }
    
    boolean hasParent() {
        return parent != null
    }
    
    boolean isRoot() {
        return !hasParent()
    }
}

class Dependency {
    org.apache.maven.model.Dependency mavenDependency
    
    String getName() {
        return mavenDependency.artifactId
    }
    
    String getGroup() {
        return mavenDependency.groupId
    }
    
    String getVersion() {
        return mavenDependency.version
    }
    
    String getScope() {
        if (mavenDependency.scope == null) return 'compile'
        if (mavenDependency.scope == 'test') return 'testCompile'
        return mavenDependency.scope
    }
    
    String toString() {
        // dependency to another module of this project?
        if (MavenImporter.instance.modules.containsKey(name)) {
            return "${scope} project(':${MavenImporter.instance.getModule(name).name}')"
        }
        return "${scope} '${group}:${name}:${version}'"
    }
}
import groovy.text.SimpleTemplateEngine
/**
 * Config section
 */

def rootDir=new File('test/resources').absolutePath

@Grab(group='org.apache.maven', module='maven-ant-tasks', version='2.0.9', transitive=false)
def loadDependencies() {}

def importer = new MavenImporter()
importer.run(rootDir + /\pom.xml/)

class MavenImporter {
    def ant = new AntBuilder()
    // Key is the artifactId
    def Map<String, Module> modules = new HashMap()
    def Module root
    
    MavenImporter() {
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
            if (modules.containsKey(dependency.artifactId)) {
                module.dependencies << new ModuleDependency(module: getModule(dependency.artifactId), mavenDependency: dependency)
            } else {
                module.dependencies << new Dependency(mavenDependency: dependency)
            }
        }
        for(managedVersionDependency in pom.mavenProject.managedVersionMap.entrySet()) {
            module.managedArtifactVersions << managedVersionDependency
        }
        for(subModule in pom.modules) {
            module.modules << parsePom(new File(module.getBasedir(), subModule + "/pom.xml").absolutePath)
        }
        for(repository in pom.mavenProject.remoteArtifactRepositories) {
            module.repositories << repository
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
                include += """"${module.basedir.name}", """
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
    List<Module> modules = new LinkedList()
    List<Dependency> dependencies = new LinkedList()
    List<org.apache.maven.artifact.DefaultArtifact> managedArtifactVersions = new LinkedList()
    List<org.apache.maven.artifact.repository.DefaultArtifactRepository> repositories = new LinkedList()
    
    File getBasedir() {
        return project.basedir
    }
    
    String getGroup() {
        return project.artifact.groupId
    }
    
    String getName() {
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
        return "${scope} '${group}:${name}:${version}'"
    }
}

class ModuleDependency extends Dependency {
    Module module
    String toString() {
        return "${scope} project(':${module.name}')"
    }
}
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir	= "target/test-reports"
grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
	repositories {
		flatDir name:"localJars",dirs:"test/lib"
	}
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        compile( 'org.hibernate:hibernate-core:3.3.1.GA', [export: false]) {
			excludes 'ehcache', 'xml-apis', 'commons-logging'
		}
		compile('org.hibernate:hibernate-commons-annotations:3.1.0.GA', [export: false]) {
			excludes 'hibernate'
		}
        compile 'org.hibernate:hibernate-annotations:3.4.0.GA', [export: false]
        compile 'org.hibernate:hibernate-entitymanager:3.4.0.GA', [export: false]
				
				
		runtime 'javassist:javassist:3.11.0.GA', [export: false]
		runtime 'antlr:antlr:2.7.6', [export: false]		
		runtime( 'dom4j:dom4j:1.6.1' , [export: false]) {
			excludes 'xml-apis'
		}				
		runtime( 'org.hibernate:hibernate-ehcache:3.3.1.GA', [export: false] ) {
			excludes 'ehcache'
		}
		
    }

}

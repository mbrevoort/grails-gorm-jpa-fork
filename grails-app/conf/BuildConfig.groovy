def hibernateJars = new File("${basedir}/test/lib").listFiles().findAll { it.name.endsWith(".jar") }

grailsSettings.compileDependencies.addAll hibernateJars
grailsSettings.runtimeDependencies.addAll hibernateJars
grailsSettings.testDependencies.addAll hibernateJars
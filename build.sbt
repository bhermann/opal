import sbtunidoc.Plugin.UnidocKeys.unidoc

name := "OPAL Library"

version in ThisBuild := "0.9-SNAPSHOT"

organization in ThisBuild := "de.opal-project"

homepage in ThisBuild := Some(url("http://www.opal-project.de"))

licenses in ThisBuild := Seq("BSD-2-Clause" -> url("http://opensource.org/licenses/BSD-2-Clause"))

// [for sbt 0.13.8 onwards] crossPaths in ThisBuild := false

scalaVersion in ThisBuild := "2.11.8"
//scalaVersion in ThisBuild := "2.12.0-M5"

scalacOptions in (ThisBuild,Compile, compile) ++= Seq(
	"-deprecation", "-feature", "-unchecked", "-Xlint", "-Xfuture", "-Xfatal-warnings", 
	"-Ywarn-numeric-widen", "-Ywarn-nullary-unit", "-Ywarn-nullary-override",
	"-Ywarn-unused", "-Ywarn-unused-import", "-Ywarn-dead-code" )

scalacOptions in (ScalaUnidoc, unidoc) ++= Opts.doc.title("OPAL - OPen Analysis Library")
	
scalacOptions in (ScalaUnidoc, unidoc) ++= Opts.doc.version(version.value)
	
resolvers in ThisBuild += Resolver.jcenterRepo

parallelExecution in ThisBuild := false // the tests/analysis are already parallelized

parallelExecution in Global := false // the tests/analysis are already parallelized

javacOptions in ThisBuild ++= Seq("-encoding", "utf8")

testOptions in ThisBuild <<=
  baseDirectory map { bd =>
    Seq(Tests.Argument("-u",  bd.getAbsolutePath + "/shippable/testresults"))
  }

testOptions in ThisBuild += Tests.Argument("-o")

// Required to get relative links in the generated source code documentation.
scalacOptions in (ScalaUnidoc, unidoc) <<=
  baseDirectory map {
    bd => Seq ("-sourcepath", bd.getAbsolutePath)
  }

scalacOptions in (ScalaUnidoc, unidoc) ++=
	Opts.doc.sourceUrl( 
		"https://bitbucket.org/delors/opal/src/HEAD€{FILE_PATH}.scala?"+
			(if (isSnapshot.value) "at=develop" else "at=master")
  )

javaOptions in ThisBuild ++= Seq(
	  "-Xmx3G", "-Xms1024m", "-Xnoclassgc",
		"-XX:NewRatio=1", "-XX:SurvivorRatio=8", "-XX:+UseParallelGC","-XX:+AggressiveOpts")

addCommandAlias("compileAll","; test:compile ; it:scalariformFormat ; it:compile")

addCommandAlias("cleanAll","; clean ; cleanFiles ; cleanCache ; cleanLocal ")

addCommandAlias("cleanBuild","; project OPAL ; cleanAll ; eclipse ; compileAll ; unidoc ;  publishLocal ")

//EclipseKeys.createSrc := EclipseCreateSrc.Default 

EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Unmanaged, EclipseCreateSrc.Source, EclipseCreateSrc.Resource)

EclipseKeys.executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE17)

EclipseKeys.withSource := true

//
//
// Publish jars to Eclipse plugin project
//
//
//addCommandAlias("copyToEclipsePlugin", "; set publishTo in ThisBuild := Some(Resolver.file(\"file\", new File(\"TOOLS/ep/lib\"))) ; publish")

//
//
// SETTINGS REQUIRED TO PUBLISH OPAL ON MAVEN CENTRAL
//
//

publishMavenStyle in ThisBuild := true

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomExtra in ThisBuild := (
  <scm>
    <url>git@bitbucket.org:delors/opal.git</url>
    <connection>scm:git:git@bitbucket.org:delors/opal.git</connection>
  </scm>
  <developers>
    <developer>
      <id>eichberg</id>
      <name>Michael Eichberg</name>
      <url>http://www.michael-eichberg.de</url>
    </developer>
    <developer>
      <id>reif</id>
      <name>Michael Reif</name>
    </developer>	
  </developers>)

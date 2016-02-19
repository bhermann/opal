name := "Bytecode Representation"

version := "0.8.1" // LAST RELEASE
// version := "0.8.1-SNAPSHOT"

// We don't want the build to be aborted by inter-project links that are reported by
// scaladoc as errors if we publish the projects; hence, we do not use the 
// standard compiler settings!
scalacOptions in (Compile, doc) := Opts.doc.title("OPAL - Bytecode Representation") 

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

libraryDependencies += "net.ceedubs" %% "ficus" % "1.1.2"

//scalacOptions in Compile := Seq("-deprecation", "-feature", "-unchecked", "-Xlint", "-Xdisable-assertions")
//scalacOptions in Compile += "-Xdisable-assertions"

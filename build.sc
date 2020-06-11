import mill._, scalalib._, scalajslib._
import mill.scalajslib.api.ModuleKind
import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
//import $ivy.`com.lihaoyi::mill-scalajslib:$MILL_VERSION`

trait DottyProject extends ScalaModule {
  val scalaVersion = "0.25.0-RC1"
  def scalacOptions = Seq("-indent", "-Yindent-colons", "-Ycheck-init")

}

trait Ops extends ScalaModule {
  def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:0.6.2".withDottyCompat(scalaVersion())
  )
}

/** All examples must define a `@main def run()` function. */
trait Example extends ScalaModule {
  def moduleDeps = Seq(interop)
  def runJS() = T.command {
    val cps = runClasspath().map(_.path.toIO.getAbsolutePath)
    val cp = cps.mkString(java.io.File.pathSeparator)
    os.proc(
        "node",
        "--experimental-worker",
        "--jvm",
        "--vm.cp",
        cp,
        "interop/resources/nodejs/boot.js",
        "--",
        artifactName() + ".run"
      )
      .call(stdout = os.Inherit)
  }

  def dumpPath() = T.command {
    val cps = runClasspath().map(_.path.toIO.getAbsolutePath)
    val cp = cps.mkString(java.io.File.pathSeparator)
    println(cp)
  }
}

object interop extends DottyProject {
  def ivyDeps = Agg(
    ivy"org.graalvm.sdk:graal-sdk:20.1.0"
  )
}

object example1 extends DottyProject with Example

object example2 extends DottyProject with Example with Ops

// default to commonjs modules, which is what we need
object scalajs extends ScalaJSModule {
  def scalaVersion = "2.13.2"
  // mill scalajs 1.1.0 support not ready yet
  def scalaJSVersion = "1.0.1"
  def moduleKind = T { ModuleKind.CommonJSModule }
}

object example3 extends DottyProject with Example with Ops

object exampleX extends DottyProject with Example {
  def moduleDeps = Seq(interop)
  def ivyDeps = Agg(
    ivy"com.lihaoyi::requests:0.5.1".withDottyCompat(scalaVersion()),
    ivy"com.lihaoyi::ammonite-ops:2.0.4".withDottyCompat(scalaVersion()),
    ivy"org.scala-lang.modules:scala-xml_2.13:2.0.0-M1".withDottyCompat(
      scalaVersion()
    )
  )

}

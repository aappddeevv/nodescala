package app

import scala.scalajs.js
import js.annotation._

// Access the export via the toplevel ScalaJS object.
// We could also export the value at the module level as well.
@JSExportTopLevel("ScalaJS")
@JSExportAll
object ScalaJSExports {
  val processData = 42
  def processDataPromise() = js.Promise.resolve[Int](42)
}

object AnotherSetOfExports {
  @JSExportTopLevel("anotherValue")
  val blah = 43
}

package example3

import scala.language.postfixOps
import nodejs.interop.NodeJS
import nodejs.helpers._
import nodejs.helpers.ValueOps._
import org.graalvm.polyglot.Value
import concurrent._, duration._
import concurrent.ExecutionContext.Implicits.global
import scala.jdk.FunctionConverters._, scala.jdk.FutureConverters._

@main def run() =
    println("Loading scalajs output into nodejs, you must run mill scalajs.fastOpt prior to running this.")
    // Module path is relative to where the boot script is.
    // You should move the scalajs script to a more convenient location.
    val modulePath = "../../../out/scalajs/fastOpt/dest/out"    
    val simpleInt = nodeJSThread:
        // modulePath will go into as a string literal
        eval(s"""
    const myScalaJS = require("$modulePath")
    // we could return values via polyglot top level bindings
    Polyglot.export("returnValue", myScalaJS.ScalaJS.processData)
    Polyglot.export("anotherValue", myScalaJS.anotherValue)
    // or as the last value here
    myScalaJS.ScalaJS.processData
            """)

    // We can't access nodejs Values outside the nodejs thread so go back into nodejs thread
    // to access the last result and the binding values.
    nodeJSThread:
        println(s"Previous result is still around since its a Value: $simpleInt")
        val otherValue = NodeJS.polyglotContext.getPolyglotBindings().getMember("returnValue").asInt
        println(s"returnValue $otherValue")
        val anotherValue = NodeJS.polyglotContext.getPolyglotBindings().getMember("anotherValue").asInt
        println(s"anotherValue $anotherValue")
    
    // We will need to convert this result to polyglot or jvm objects inside the nodejs thread
    // to use them in JS land. Since the "eval" function is just nodejs eval, it creates
    // an environment for it to run but that environment is not persistent so
    // we need to re-import that module again.
    val jsPromise = nodeJSThread:
       eval(s"""
       const myScalaJS = require("$modulePath")
       myScalaJS.ScalaJS.processDataPromise()
       """)

    // We will use this scala structure to bring the result back.
    val scalaPromise = Promise[Int]
    scalaPromise.future.onComplete(tryResult => println(s"scala Promise1 result $tryResult"))
    
    nodeJSThread:
        // We have a "Value" that is js Promise to get that value back
        // into our thread, we need to go into the nodejs thread
        // and complete our scala Promise (which lives in the application thread).
        val res = (value: Int) => scalaPromise.success(value)
        val err = (value: Int) => scalaPromise.failure(RuntimeException(s"Failed accessing result of promise."))
        //jsPromise.invokeMember("then", res).invokeMember("catch", err)
        jsPromise.invokeMember("then", res, err)
        
    Await.ready(scalaPromise.future, 5 seconds)

    //
    // In "eval", a failed promise that is not handled by catch from the 
    // nodejs thread and cannot be caught on the JVM side. You can only obtain the 
    // error by using process.on("unhandledRejection", error => {}) in nodejs.
    //
    val scalaPromise2 = Promise[String]
    scalaPromise2.future.onComplete(tryResult => println(s"scala Promise2 result $tryResult"))
    nodeJSThread:
        try {
            val jsPromise = eval("""Promise.reject(new Error("BOOM")).catch(err => "CAUGHT ERROR IN JS!")""")
            val res = (value: String) => scalaPromise2.success(value)
            val err = (value: Any) => scalaPromise2.failure(RuntimeException(s"Error running code: $value"))
            //jsPromise.invokeMember("then", res).invokeMember("catch", err)
            // does this work?
            jsPromise.invokeMember("then", res, err)
        } catch { 
            case scala.util.control.NonFatal(e) => println(s"Eval failed and caught: $e")
        }
    Await.ready(scalaPromise2.future, 5 seconds)

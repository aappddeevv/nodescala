package example2

import scala.language.postfixOps
import nodejs.helpers._
import org.graalvm.polyglot.Value
import concurrent._, duration._
import concurrent.ExecutionContext.Implicits.global

import java.io.File

@main def run() =
    // In this example we define the promise outside the nodejs thread
    // Values from the nodejs thread, such as JS values, should not be 
    // accessed outside that thread so we just expect unit return here.
    val scalaPromise = Promise[Unit]
    scalaPromise.future.onComplete(tryResult => println(s"Scala promise completed $tryResult"))

    println("Downloading a file from the network using nodejs download module.")
    val downloadPath = os.pwd / "download"
    if(os.exists(downloadPath)) os.remove.all(downloadPath)
    os.makeDir.all(downloadPath)
    println(s"Downloads to be placed into folder: ${downloadPath.toIO.getName}")

    // We ignore the return value, which is a nodejs promise. We 
    // set the scala promise value inside the js promise success and error handlers.
    nodeJSThread:
        implicit val ec: ExecutionContext = nodeJSEC
        bindings.putMember("url", "http://i3.ytimg.com/vi/J---aiyznGQ/mqdefault.jpg")
        expose("downloadPath", downloadPath.toIO.getName)
        val delayedJSPromise = eval("""
// returns a promise using legacy API
const fs = require("fs")
const util = require("util")
const writeFile = util.promisify(fs.writeFile)
const download = require("download")
const url = Polyglot.import("url")
console.log("URL to fetch via bindings: ", url)
console.log("Output path from top level polyglot scope: ", downloadPath)
const doit = async() => writeFile(`${downloadPath}/piano-kitten.jpg`, await download(url))
// return the function that when run returns a promise--a delayed promise
doit
         """)
        val res = (value: Value) => {
            println(s"Download complete! Enjoy piano kitty: $value")
            scalaPromise.success(())
        }
        val err = (value: Value) => {
            println(s"Download failed: $value")
            scalaPromise.failure(RuntimeException("Download failed: $value"))
        }
        // run the js promise
        val jsPromise: Value = delayedJSPromise.execute()

        // Add callbacks 1 at a time...
        jsPromise.invokeMember("then", res).invokeMember("catch", err)

        // or both together, does this work? Not sure this works yet...
        //jsPromise.invokeMember("then", res, err)
    
    // Since jspromise, a Value, is returned from js land, we cannot access it on this
    // thread. The following line would throw an exception.
    //println(s"Return value from nodejs is $jspromise")

    // Back on our jvm application thread...

    // If you don't have some form stopping exit, the program may terminate
    // too quickly. As a last resort, you could sleep the jvm application thread.
    // But that's bad as well.
    //Thread.sleep(5000)

    // Not a good practice to call await...
    Await.ready(scalaPromise.future, 5 seconds)

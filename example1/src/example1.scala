package example1

import nodejs.helpers._
import org.graalvm.polyglot.Value

@main def run() =
    val result = nodeJSThread:
        // By converting to an int inside the loop, result
        // can be access outside the nodejs therad.
        eval("2 + 3 + 4").asInt()
    println(s"result $result")
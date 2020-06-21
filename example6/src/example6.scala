package example6

import zio._
import zio.console._
import nodejs.helpers._
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import scala.concurrent._
import java.util.concurrent.LinkedBlockingDeque

/** Runs. And fails since we intentially fail the effect.
 */
def run1() =
    println("run1")
    val program: ZIO[Console, Throwable, Unit] = 
        for {
            _ <- putStrLn("starting effect")
            _ <- IO.fail(new RuntimeException("BOOM"))
        } yield ()

    val exit = Runtime.default.unsafeRunSync(program)
    println(s"Exit: $exit")
    "completed"

/** Accessing js values on non-nodejs thread causes error!
 * Primitive values are converted and are safe to access on any thread.
 */
def run2(primitive: Int, value: Value) =
    println("run2")
    println(s"args before effect, on same thread: $primitive, $value")
    val program: ZIO[Console, Throwable, Unit] = 
        for {
            _ <- putStrLn("starting effect")
            // fork will cause this effect to potentially run on a different thread
            fiber <- IO.effect {
                println(s"Primitive value: $primitive")
                // accessing value here will cause a thread access exception
                println(s"Value: $value")
            }.fork
            _ <- fiber.join
        } yield ()
    // Running on the same thread as nodejs!
    Runtime.default.unsafeRunSync(program) match { 
        case Exit.Success(r) => println("never called")
        case Exit.Failure(e) => 
        // IllegalStateOption about multi-threaded access
        val err = e.failureOption
        println(s"Failed: ${err}")
    }
    "completed"

/** Succeeds as the fork forks onto the nodejs thread. */
def run3(
    primitive: Int, 
    value: Value, 
    queue: java.util.concurrent.LinkedBlockingDeque[Runnable]
) =
    println("run3")
    implicit val ec2 = makeEC(queue)
    implicit val context = Context.getCurrent

    println(s"args before effect, on same thread: $primitive, $value")
    queue.add(new Runnable { def run(): Unit = println("Run from JVM but on nodejs thread.") })
    
    // effect is an immutable value and can be created on any thread
    val effect: ZIO[Console, Throwable, Unit] = 
            for {
                _ <- putStrLn("starting effect")
                fiber <- IO.effect {
                    println(s"Value accessed inside zio effect but forked on nodejs thread: $value")
                }.forkOn(ec2)
                _ <- fiber.join
            } yield ()
    val program = effect

    // We can get the value back to nodejs different ways, one way
    // is to use scala concurrent machinery.
    val scalaPromise = scala.concurrent.Promise[String]()

    // We don't want the main zio processing to be on the nodejs thread and 
    // blocking it, so run inside another thread. We could also create
    // a separate java Executor and submit it that way, etc. Or even run 
    // it inside a scala Future :-).
    Thread{() => 
        queue.add(new Runnable { def run(): Unit = 
                println("Called from inside the new 'main' zio runner thread.") })
        Runtime.default.unsafeRunSync(program) match { 
            case Exit.Success(r) => scalaPromise.success("completed")
            case Exit.Failure(e) => 
                val err = e.failureOption
                println(s"Failed: ${err}")
                scalaPromise.failure(new RuntimeException("failed"))
        }
        // needed for "java" type inference
        ()
    }.start()

    // and convert the scala Promise "future" to a js Promise
    scalaPromise.future.toJSPromise

// def run4(
//     primitive: Int, 
//     value: Value, 
//     queue: java.util.concurrent.LinkedBlockingDeque[Runnable]
// ) =
//     println("run4")
//     implicit val ec2 = makeEC(queue)
//     implicit val context = Context.getCurrent
//     println(s"args before effect, on same thread: $primitive, $value")
//     queue.add(new Runnable { def run(): Unit = println("Run from JVM but on nodejs thread.") })
//     val program: ZIO[Console, Throwable, Unit] = 
//             for {
//                 _ <- putStrLn("starting effect")
//                 fiber <- IO.effect {
//                     println(s"Value accessed inside zio effect but forked on nodejs thread: $value")
//                 }.forkOn(ec2)
//                 _ <- fiber.join
//             } yield ()
//     val result = Future {
//         queue.add(new Runnable { def run(): Unit = 
//                 println("Called from inside the new 'main' zio runner thread.") })
//         Runtime.default.unsafeRunSync(program) match { 
//             case Exit.Success(r) => "completed"
//             case Exit.Failure(e) => 
//                 val err = e.failureOption
//                 println(s"Failed: ${err}")
//                 throw new RuntimeException("failed")
//         }
//         ()
//     }
//     result.toJSPromise

package example4

import scala.language.implicitConversions
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import concurrent._
import concurrent.ExecutionContext.Implicits.global
// import a single given
import nodejs.helpers.{toJSPromise, toJSPromiseLock, currentThreadExecutionContext}
import scala.jdk.FunctionConverters._

//
// Most of these are translations from: 
// https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test/src/com/oracle/truffle/js/test/interop/AsyncInteropTest.java
//

trait Thenable:
    /** Each of these values will be executable. */
    def `then`(resolve: Value, reject: Value): Unit

trait Thenable1Arg:
    /** Each of these values will be executable. */
    def `then`(resolve: Value): Unit

trait ThenableAll:
    def `then`(resolve: Value, reject: Value): Unit
    def `then`(resolve: Value): Unit
    def `catch`(resolve: Value): Unit

trait PromiseExecutor:
    // this could be any name, its the signature that counts
    def onPromiseCreation(resolve: Value, reject: Value): Unit

@java.lang.FunctionalInterface
trait PromiseExecutorFI:
    // this could be any name
    def onPromiseCreation(resolve: Value, reject: Value): Unit

// this works just like PromiseExecutor
trait Whacky:
    def blah(resolve: Value, reject: Value): Unit

@java.lang.FunctionalInterface
trait ValueConsumer extends java.util.function.Consumer[Value]:
    def accept(value: Value): Unit

trait ValueConsumerNoFI extends java.util.function.Consumer[Value]:
    def accept(value: Value): Unit

@java.lang.FunctionalInterface
trait ScalaValueConsumer:
    def accept(value: Value): Unit

trait ScalaValueConsumerNoFI:
    def accept(value: Value): Unit

//
// Basic function calling and arg conversion
//

// js: Packages.example4.example4$package.process("hah", "hah")
// sychronous
def process(arg1: String, arg2: String): Int =
    println(s"args: $arg1, $arg2")
    42

// js: Packages.example4.example4$package.process2("hah")
// future completes and prints but nodejs is *not* blocked
def process2(arg1: String): Unit =
    Future {
        println("Run from a future.") 
    }

//
// Create Thenables in java, call js await.
// Note that calling .catch or .then (1 arg) on the return values would be like
// calling the function .catch or .then (1 arg) directly which is not
// the semantics we want.
//

// js: var x = (async () => await Packages.example4.example4$package.process4_d(42))().catch(err => console.log("err", err))
// js: Packages.example4.example4$package.process3("hah").then(result => console.log(result), void 0)
// return a "thenable" which can be chained and awaited
// Note that only 2-arity "then" method is treated as a thenable in JS
def process4_a(arg1: Value) =
    new Thenable:
        def `then`(resolve: Value, reject: Value) = resolve.execute(arg1)

// js: new Promise(Packages.example4.example4$package.process4("hah")).then(result => console.log("value", result)).catch(err => console.log("err", err))
// this fails in dotty as the lambda is not a SAM and there is no "then" method
// defined anywhere
def process4_b(arg1: Value) = 
    (resolve: Value, reject: Value) => resolve.execute(arg1)

// this throws an arity error as a 2 arg "then" method was expected
def process4_c(arg1: Value) =
    new Thenable1Arg:
        def `then`(resolve: Value) = resolve.execute(arg1)

// rejects arg in an await correctly
def process4_d(arg1: Value) =
    new Thenable:
        def `then`(resolve: Value, reject: Value) = reject.execute(arg1)

// must call with process4_1("arg").onPromiseCreation, otherwise you are calling the promise on the wrapper object itself
def process4_1(arg1: Value) = 
    new PromiseExecutor {
        def onPromiseCreation(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    }

// this works because we force it into a SAM through ascription
def process4_2(arg1: Value) = 
    def executor(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    executor: PromiseExecutorFI

// this works because we force it into a SAM through ascription
// however, this generates a warning from dotty
// "method executor is eta-expanded even though example4.PromiseExecutor does not have the @FunctionalInterface annotation."
def process4_2_1(arg1: Value) = 
    def executor(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    executor: PromiseExecutor

// this will fail because the func becomes a PolyglotMapAndFunction and cannot be cast to a Value at runtime
def process4_2_2(arg1: Value) = 
    (resolve: Value, reject: Value) => resolve.executeVoid(arg1)

// same as 4_2, success!
def process4_2_3(arg1: String) = 
    {(resolve: Value, reject: Value) => resolve.executeVoid(arg1)}: PromiseExecutorFI

// this fails to run because the return value is not executable!
def process4_2_4(arg1: String) = 
  {(resolve: Value, reject: Value) => resolve.executeVoid(arg1)}: PromiseExecutor

// same as process4_1, you must access `.blah` as the promise executor.
def process4_3(arg1: String) = 
    new Whacky {
        def blah(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    }

// scala standard collection converters translate into SAMs which is what graal wants
// you *don't* have to use interfaces that are annotated with @FunctionalInterface.
// this does not work!
def process4_4() = 
    // must box the primitive! to make it an AnyRef (Object)
    //def promiseExecutor(resolve: Value, reject: Value): Unit = resolve.executeVoid(Int.box(41))
    def promiseExecutor(resolve: Value, reject: Value): Unit = resolve.executeVoid("blah")
    promiseExecutor.asJavaBiConsumer
    //promiseExecutor: java.util.function.BiConsumer[Value,Value]


//
// JS promise, adding a java thenables
//

// run with:

// Packages.example4.example4$package.process5(Promise.resolve(42)).then(r => console.log("value", r)).catch(err => console.log("err", err))
// Packages.example4.example4$package.process5(Promise.resolve("blah")).then(r => console.log("value", r)).catch(err => console.log("err", err))
//
// but note that a type error, if thrown, is *not* caught in the Promise at all! and can only be caught with a try/catch!

// Runtime errors out because the 42 on the js side cannot be cast to Value
// hence the `value:Value` is not being seen...everything must be public
// class, public members and public types to be seen corrrectly.
def process5(jsp: Value) = jsp.invokeMember("then", (value: Value) => Int.box(41))

// works with Int, error if passed String, that's good!
def process5_1(jsp: Value) = jsp.invokeMember("then", (value: Int) => Context.getCurrent().asValue(41))

// works with Int, error if passed String, that's good!
def process5_2(jsp: Value) = jsp.invokeMember("then", (value: Int) => 41)

// works with Int, error if passed String
def process5_3(jsp: Value) = 
    val thunk: java.util.function.Consumer[Int] =  
        new java.util.function.Consumer[Int] { def accept(v: Int) = println(s"callback: $v") }
    jsp.invokeMember("then", thunk)

// works with different completion typse, 41 is passed through as expected
def process5_4(jsp: Value) = 
    jsp.invokeMember("then", (value: Object) => {println(s"value $value, ${value.getClass}"); 41})

// works like 5_4
def process5_5(jsp: Value) =
    def thunk(value: Object) = 41
    jsp.invokeMember("then", thunk)

// cast errors due to callback requiring Value and cannot cast
def process5_6(jsp: Value) =
    val thunk: java.util.function.Consumer[Value] = (v: Value) => println(s"v: $v, ${v.getClass}")
    jsp.invokeMember("then", thunk)

// fails to convert to Value from the js Promise resolved value
// https://github.com/graalvm/graaljs/issues/120 made me think it should work
def process5_7_a(jsp: Value) =
    val thunk: ValueConsumer = (v: Value) => println(s"v: $v, ${v.getClass}")
    jsp.invokeMember("then", thunk)

// runs but has incorrect results as if the thunk is never applied
def process5_7_b(jsp: Value) =
    val thunk: ValueConsumerNoFI = (v: Value) => println(s"v: $v, ${v.getClass}")
    jsp.invokeMember("then", thunk)

// this works but needs the @FunctionalInterface annotation
// compiler warning about 101 at the end :-), that's good, 101 is also ignored which is good
def process5_8(jsp: Value) =
    val thunk = new ScalaValueConsumer:
        override def accept(v: Value): Unit = { println(s"v: $v, ${v.getClass}"); 101 }
    jsp.invokeMember("then", thunk)

// This completely fails to run the thunk as if it was never added in the then clause! 
// I think this should error out.
// compiler warning about 101 at the end :-), that's good, 101 is also ignored which is good
def process5_9(jsp: Value) =
    val thunk = new ScalaValueConsumerNoFI:
        override def accept(v: Value): Unit = { println(s"v: $v, ${v.getClass}"); 101 }
    jsp.invokeMember("then", thunk)




//
// scala Future => JS Promise through Promise instantiation
//


// Function runs on the main nodejs thread and so does the 
// Future creation and the conversion to a JS promise.
def process6(value: Int) = 
    implicit val c = Context.getCurrent
    implicit val ec = currentThreadExecutionContext
    Future(value).toJSPromise

// try{ Packages.example4.example4$package.process6_1(10) } catch (err) { console.log("err", err.getMessage()) }
// err Context instances that were received using Context.get() cannot be entered.
// Won't work, can enter().
// see the bottom of: https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test.threading/src/com/oracle/truffle/js/test/threading/ConcurrentAccess.java
def process6_1(value: Int) =
    implicit val c = Context.getCurrent
    Future(value).toJSPromiseLock

/** Thread hop to gain access to nodejs context. */
/*
> try{ Packages.example4.example4$package.process6_2(10).then(x => console.log("result", x)) } catch (err) { console.log("err", err.getMessage()) }
blah
Promise { <pending> }
result 10
*/
def process6_2(value: Int) =
    implicit val c = Context.getCurrent
    implicit val ec = currentThreadExecutionContext
    Future(value)(global).map{_ => println("blah"); value}(ec).toJSPromise

//
// misc tests
//

// can accepty pretty much any input
def process7(value: Value) =
    println(s"value: $value")

// Packages.example4.example4$package.process7_1(10)
// gives error about converting number to string
def process7_1(value: String) =
    println(s"value: $value")
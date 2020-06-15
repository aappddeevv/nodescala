package example4

import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import concurrent._
import concurrent.ExecutionContext.Implicits.global
// import a single given
import nodejs.helpers.{toJSPromise, toJSPromiseLock, currentThreadExecutionContext}

//
// Most of these are translations from: 
// https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test/src/com/oracle/truffle/js/test/interop/AsyncInteropTest.java
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
// Thenables
//

trait Thenable:
    /** Each of these values must be executables. */
    def `then`(resolve: Value, reject: Value): Unit

// js: Packages.example4.example4$package.process3("hah").then(result => console.log(result), void 0)
// return a "thenable" which can be chained and awaited
def process3(arg1: String) =
    new Thenable:
        def `then`(resolve: Value, reject: Value) = {
            resolve.executeVoid(arg1)
        }

trait PromiseExecutor:
    // this could be any name
    def onPromiseCreation(resolve: Value, reject: Value): Unit

@java.lang.FunctionalInterface
trait PromiseExecutorFI:
    // this could be any name
    def onPromiseCreation(resolve: Value, reject: Value): Unit

// this works just like PromiseExecutor
trait Whacky:
    def blah(resolve: Value, reject: Value): Unit

// js: new Promise(Packages.example4.example4$package.process4("hah")).then(result => console.log("value", result)).catch(err => console.log("err", err))
// this fails in dotty as the lambda is not a SAM
def process4(arg1: String) = 
    (resolve: Value, reject: Value) => resolve.executeVoid(arg1)

// must call with process4_1("arg").onPromiseCreation, otherwise you are calling the promise on the object itself
def process4_1(arg1: String) = 
    new PromiseExecutor {
        def onPromiseCreation(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    }

// this works because we force it into a SAM through ascription
def process4_2(arg1: String) = 
    def executor(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    executor: PromiseExecutorFI

// this works because we force it into a SAM through ascription
// however, this generates a warning from dotty
// "method executor is eta-expanded even though example4.PromiseExecutor does not have the @FunctionalInterface annotation."
def process4_2_1(arg1: String) = 
    def executor(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    executor: PromiseExecutor

// this will fail because the func becomes a PolyglotMapAndFunction and cannot be cast to a Value at runtime
def process4_2_2(arg1: String) = 
    (resolve: Value, reject: Value) => resolve.executeVoid(arg1)

// same as 4_2, success!
def process4_2_3(arg1: String) = 
    {(resolve: Value, reject: Value) => resolve.executeVoid(arg1)}: PromiseExecutorFI

// this fails to run because the return value is not executable!
def process4_2_4(arg1: String) = 
  {(resolve: Value, reject: Value) => resolve.executeVoid(arg1)}: PromiseExecutor

// same as process4_1
def process4_3(arg1: String) = 
    new Whacky {
        def blah(resolve: Value, reject: Value) = resolve.executeVoid(arg1)
    }


//
// JS promise, java thenables
//

// input a js promise  `..process5(Promise.resolve(42)).then(r => console.log("value", r))`.
// Runtime errors out because the 41 on the js side cannot be cast to Value
def process5(jsp: Value) = jsp.invokeMember("then", (value: Value) => 41)

// works
def process5_1(jsp: Value) = jsp.invokeMember("then", (value: Int) => Context.getCurrent().asValue(41))

// works
def process5_2(jsp: Value) = jsp.invokeMember("then", (value: Int) => 41)

// works
def process5_3(jsp: Value) = 
    val thunk: java.util.function.Consumer[Int] =  
        new java.util.function.Consumer[Int] { def accept(v: Int) = println(s"callback: $v") }
    jsp.invokeMember("then", thunk)

// works
def process5_4(jsp: Value) = 
    jsp.invokeMember("then", (value: Object) => 41)


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

# nodescala

Notes on blending scala and javascript together via node and graaljs.

This repo is an concentration of blogs and another repo that does the same thing in kotlin. 

All credit for clever code should go to Mike Hearn and others for figuring this out.

You can always call scala code directly from node quite easily and create
and process js code from the jvm easily. If you want nodejs infrastructure, such as
node modules and other nodejs functions, you must use graaljs as the entry point.
If your scala jvm code calls into nodejs, it must synchronize on the nodejs
thread. This repo describes some ways to handle nodejs interop using graaljs.
The online docs and unit tests for running js code from the jvm and vice-versa
are good enough. It's only with the presence of nodejs that complicates interop.

Toplevel nodejs/graaljs docs are at https://github.com/graalvm/graaljs/blob/master/docs/user.
See the interop and multithread documents in particular.


# Running Examples

The example demonstrate calling nodejs code from scala, after starting a scala
process via nodejs. You can call out to scala quite easily and this scenario
is described in another section.

The graal installation should be first on your path so that the graal node (aka graaljs) is 
picked up when you run node.

First install the node files:

* `npm i`

Then run the examples via the mill build tool or a shell:

* mill [-w] example1.runJS
* node --experimental-worker --jvm --vm.cp $CP interop/resources/nodejs/boot.js -- example1.run

The CP env var can be created by running `mill example1.dumpPath` and exporting CP to the value it produces.

If you are changing the code and want to run the interop automatically, use the `-w` mill option
to watch for source code changes.

The boot script expects `--` at the end of the node arguments and the start of
the jvm program arguments. The first argument must be the name of the main class or 
`-jar jarfile` with the `Main-Class` attribute set. 

When using
dotty and the `@main` attribute, the main class is usually the name of the function
prepended with the enclosing package. `@main` creates a sythesized class with 
a proper "main" function. mill automatically adds a `Main-Class` attribute if you run `mill example1.jar`.

To run the examples, install mill (I did not include the mill self-installing script):

* mill example1.runJS: Simple eval.
* mill example2.runJS: Eval but with javascript promises.
* mill scalajs.fastOpt: Create a scala.js module.
* mill example3.runJS: Load the scalajs module and return primitives and promises.

Running the example through mill uses a "boot" script that allows the scala code to
take over running the application. When the scala program exits, the entire
application instances exits.  Hence the concept of "boot."

There are other ways to run scala code while still entering in through nodejs.
For example, you may be running a HTTP server on the main node thread but 
also  want to run a scala program as if its "main" had been called.

# Running Examples Again!

Install dependencies via `npm i`.

You can run a JVM program that calls back into the nodejs thread using the 
`appstart.js` script. This script exports a function that can start
a new JVM thread that runs your "main" static function in a class.

Start node aftering building a fat jar (fat jar makes this easy otherwise
you need a full classpath):

```sh
# get scala, interop and dependencies together in 1 jar
mill example1.assembly
node --jvm --experimental-worker --vm.cp=out/example1/assembly/dest/out.jar
```

Then run each "main" class. You can start multiple `main`s but
you will need to create the right classpath or assembly/jars
with the programs in them. In this case, we just run this on
example 1, 2 and 3. Don't forget to run `mill scalajs.fastOpt` 
before running example3.

```javascript
const start = require("./interop/resources/nodejs/appstarter")
start("example1.run")
```

# Running Examples Again Again!

You can call scala code methods directly from nodejs, you don't have to
only call `main` methods. But if you want to run multi-threaded code
you need to remeber to call `appstarter` to start the infrastructure
regardless of whether you use the `start` function to run `main` functions
that call into nodejs.

Your scala code can run on the nodejs thread, but that's not recommended
so it is recommended that you immediately start a jvm thread and 
call back into the nodejs thread if needed.

If you need merely call a scala function from javascript, you can do that
as well and return a js promise. You can create a js promise directly,
in scala code, on the nodejs thread. Inside the js promise, you can
use jvm threads as needed.

You can also return a jvm object that looks like a promise. You can
create and manage the jvm object on any thread, so you should create
the promise-looking object as the return value from the js nodejs function
call and have the jvm code run on another thread. By making a jvm
object "look" like a promise, the js code can `await` or `then` on it.
In fact, similar to scala.js, as long as that object has a `then`
method with the proper two-arg-function signature, your jvm
object is a js promise.

Example 4 has a single function in open code. Build and load the
assembly as above, then run the code on the nodejs thread.

```javascript
// don't start the interop infrastructure
> Packages.example4.example4$package.process("hah", "blah")
args: hah, blah
42
```
Node the scala byte code manipulation requires us to find the 
function using mangled JVM names.

None of this code required calling back into the nodejs
thread from a separate jvm thread, so we could call this
function directly without starting the infrastructure. Also,
the values were simple primitives so they could be translated
by the graal engine directly without going through the `Value`
class.

The individual functions in example4 show some
async examples that you would encounter when using
the jvm for fulfilling async requests initiated in nodejs.

Example 4 source code also has translations of the interop tests from
https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test/src/com/oracle/truffle/js/test/interop/AsyncInteropTest.java

# Interop Concept

* Nodejs is single threaded. You should only access it when on that thread.
* To use nodejs with jvm you *must* use graaljs (node on the jvm). You cannot just use a js context from java.
  * Only the graaljs entry point has the nodejs infrastructure builtin.
* Start from graaljs. 
* Start a js worker. These are like threads in js land.
  * The js worker watches a queue of requests coming from the jvm side.
* Call into the JVM with the scala interop worker thread. That thread is the "main" thread for your scala program.
* When you need to call back into node/javascript either ensure you are on that thread using the nodejs
executor. To eval js code use a queue to push messages back and forth to the js worker to run "eval" and return the result.

See the resources above to understand the details.

Is this hard? 

No, the key is to not block the event loop in nodejs on the java side. If you want to run cool multi-threaded computations on the jvm, you still need a way to coordinate between threads and this is the way to coordinate with the nodejs thread.

Some issues that need to be resolved:

* A way to catch unhandled promise failures. nodejs only posts these to `process.on("unhandledReject", ...)`
events. We would need a global handler that calls back into the jvm side so that when js code evals fail, we
can handle those.
* Some syntax and sugar and javascript promises and scala futures or any other scala effects. The async test code
in graaljs has some good starting points.
  * See: https://thecodebarbarian.com/unhandled-promise-rejections-in-node.js.html
* Performance tests.

# mill

I'm still learning mill but the following example build.sc files were helpful as examples:

* https://gitlab.com/hmf/srllab/-/blob/master/build.sc

# Global objects in JS namespace

See the graaljs documentation for their APIs: https://github.com/graalvm/graaljs/blob/master/docs/user/JavaScriptCompatibility.md

* Polyglot: Add and remove data from special "maps" that are shared between languages.
* Graal: Graal version
* Java: Access JVM types (and hence values).
* Packages: Package root to access classes.

# Running graajs (aka node)

Some helpful things:

* --vm.cp yourclasspath
	* NODE_JVM_CLASSPATH can also hold the classpath for the node process
 	* Have not seen NODE_JVM_CLASSPATH before, is that real?
* Need --jvm to run on the jvm and have jvm available
  * Note I ran it without --jvm and jvm still seems accessible!?!?!
* --experimental-workers to turn on workers
* --experimental-sourcemaps to turn on sourcemap, especially if using scala.js/typescript/babel

Lots of VM options to tune graal.

# Resources

* Mike Hearn:
  * https://blog.plan99.net/vertical-architecture-734495f129c4: Mike's article on how it works.
  * https://github.com/mikehearn/nodejvm: Code for Mike's work.
  * https://mikehearn.github.io/nodejvm/: Doc site. Start here.
* Laurynas Lubys: Early versions of interop. Both vidoes are about the same
  * https://www.youtube.com/watch?v=3SRjPHnWHa0&t=460s
  * https://www.youtube.com/watch?v=o4rRWckkUyE&t=456s
* A question on this topic that helps formulate the solution space:
  * https://www.gitmemory.com/issue/graalvm/graaljs/12/493957872
* General interop:
  * https://technology.amis.nl/2019/11/05/node-js-application-running-on-graalvm-interoperating-with-java-python-r-and-more/
* graaljs async interop tests, steal some of this code: https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test/src/com/oracle/truffle/js/test/interop/AsyncInteropTest.java
* Key jvm docs for jvm polyglot. Open these during development:
  * https://www.graalvm.org/sdk/javadoc/index.html?org/graalvm/polyglot/Context.html
  * https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Value.html
  * https://github.com/graalvm/graaljs/blob/master/docs/user/JavaInterop.md
  * https://github.com/graalvm/graaljs/blob/master/docs/user/JavaScriptCompatibility.md
  * multithreading: https://github.com/graalvm/graaljs/blob/master/docs/user/Multithreading.md
  * https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.test.threading/src/com/oracle/truffle/js/test/threading/JavaAsyncTaskScheduler.java
  * worker threads and jvm interop: https://github.com/graalvm/graaljs/blob/master/graal-nodejs/test/graal/unit/worker.js

Note on mill and dotty:

* https://appddeevvmeanderings.blogspot.com/2020/03/mill-for-dotty.html

This repo translates Mike's work to dotty and all credit for anything clever
goes to everyone else.

# JS Source on Classpath

If you place a js file inside a jar, you can access it directly assuming you used
`node --jvm --vm.cp=yourjar.jar`:

```javascript
const cl =  Packages.java.lang.ClassLoader=.getSystemClassLoader()
const source = Buffer.from(cl.getResource("nodejs/yoursource.js").getContent().readAllBytes()).toString("utf-8")
```

# License

Apache, just like Mike Hearn's since there is code from his repo.

Note that alot of his code is actually commented out because it was kotlin code. 
Most of the original pure java code is still being used.

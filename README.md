# nodescala

Notes on blending scala and javascript together via node and graaljs.

This repo is an concentration of blogs and another repo that does the same thing in kotlin. 

All credit for clever code should go to Mike Hearn and others for figuring this out.

# Running

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

# License

Apache, just like Mike Hearn's since there is code from his repo.

Note that alot of his code is actually commented out because it was kotlin code. 
Most of the original pure java code is still being used.

# nodescala

Some notes on blending scala and javascript together via node and graaljs.

This repo is an assembly of blogs and another repo that does the same thing in kotlin. All credit for code should go to Mike Hearn.



Resources:

* Mike Hearn:
  * https://blog.plan99.net/vertical-architecture-734495f129c4: Mike's article on how it works.
  * https://github.com/mikehearn/nodejvm: Code for Mike's work.
* Laurynas Lubys: Early versions of interop. Both vidoes are about the same
  * https://www.youtube.com/watch?v=3SRjPHnWHa0&t=460s
  * https://www.youtube.com/watch?v=o4rRWckkUyE&t=456s
* A question on this topic that helps formulate the solution space:
  * https://www.gitmemory.com/issue/graalvm/graaljs/12/493957872



This repo translates this to dotty.

# Interop Concept



* Nodejs is single threaded. You should only access it when on that thread.
* To use nodejs with jvm you *must* use graaljs (node on the jvm). You cannot just use a js context from java.
  * Only the graaljs entry point has the nodejs infrastructure builtin.
* Start from graaljs. 
* Start a js worker. These are like threads in js land.
  * The js worker watches a queue of requests coming from the jvm side.
* Call into the JVM with the scala interop worker thread. That thread is the "main" thread for your scala program.
* When you need to call back into node/javascript, use a queue to push messages back and forth to the js worker.
* Callbacks written on the jvm must be "bound" to be usable in javascript.

See the resources above to understand the details.



Is this hard? No, the key is to not block the event loop in nodejs on the java side. If you want to run cool multi-threaded computations on the jvm, you still need a way to coordinated between threads and this is the way to coordinate with the nodejs thread.
//
// Use this to boot a JVM program that has a main or use this 
// to start the JVM main program but also as part of starting
// your own nodejs program.
//

// Set up a task queue that we'll proxy onto the NodeJS main thread.
//
// We have to do it this way because NodeJS/V8 are not thread safe,
// and JavaScript has no shared memory concurrency support, only
// message passing. It's like Visual Basic 6 all over again except
// this time without DCOM to wallpaper over what's really happening.
//
// Fortunately, we can call Java objects from JS and those CAN be
// shared memory. So we create an intermediate JS thread here that
// will spend its time blocked waiting for lambdas to be placed on
// the queue. Then it'll transmit them to the main event loop for
// execution.
const javaToJSQueue = new java.util.concurrent.LinkedBlockingDeque();
const {
    Worker, isMainThread, parentPort, workerData
} = require('worker_threads');

// code running in the worker
const worker = new Worker(`
    const { workerData, parentPort } = require('worker_threads');
    while (true) {
        parentPort.postMessage(workerData.take());
    }
`, { eval: true, workerData: javaToJSQueue });

// code running in the main nodejs thread
worker.on('message', (callback) => {
    try {
        callback();
    } catch (e) {
        console.log(`Error running callback from JVM: ${e}`);
        console.log(e)
    }
});

const sliceStart = process.argv ? process.argv.indexOf("--") : -1

// We need this wrapper around eval because GraalJS barfs if we try to call eval() directly from Java context, it assumes
// it will only ever be called from JS context.
Packages.nodejs.interop.NodeJS.boot(
// Java.type('nodejs.interop.NodeJS').boot(
    javaToJSQueue,
    function (str) {
        return eval(str);
        //return vm.runInThisContext(str)
    },
    // Passed your main program, but first arg after -- needs to be 
    // the main class.
    sliceStart > 0 ? process.argv.slice(sliceStart + 1) : []
);

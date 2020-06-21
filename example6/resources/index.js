
console.log("example6 - zio thread management")
const arg1 = { a: 1 }
const arg2 = "arg2"
const arg3 = 10
const arg4 = Promise.resolve("async resource")

const result1 = Packages.example6.example6$package.run1()
console.log("Result1: ", result1)

const result2 = Packages.example6.example6$package.run2(arg3, arg1)
console.log("Result2: ", result2)

/** Returns objects related to the queue and worker. */
function startChannel() {
    // Blocks the thread that .take is called on, so run in a worked thread.
    const javaToJSQueue = new java.util.concurrent.LinkedBlockingDeque();
    const { Worker } = require('worker_threads');

    // code running in the worker
    const worker = new Worker(`
    const { workerData, parentPort } = require('worker_threads');
    while (true) {
        // Blocks worker thread taking 1 element. We could enhance to take N
        // callbacks at a time.
        const runnable = workerData.take()
        // Send to listener on main thread to run the runnable.
        // Since its "message" it is inserted into the main event loop.
        // You can change the message type to add different "run" protocols
        // specific to your application e.g. { type: STOP } or { type: "RUN", callback }.        
        parentPort.postMessage(runnable);
    }
`, { eval: true, workerData: javaToJSQueue });

    // callbacks run on the main nodejs thread
    worker.on('message', (callback) => {
        try {
            callback();
        } catch (e) {
            console.error(`Error running callback from JVM`);
            console.error(e)
        }
    });
    Polyglot.export("javaToJSQueue", javaToJSQueue)
    Polyglot.export("javaToJSQueueWorker", worker)
    return {
        queue: javaToJSQueue,
        worker
    }
}

const channel = startChannel()

// pass a function that is called on the nodejs thread
//const result3 = Packages.example6.example6$package.run3(arg3, arg1, channel.queue)
const result3P = Packages.example6.example6$package.run3(arg3, arg1, channel.queue)
result3P
    .then(result => console.log("Result3: ", result))
    .catch(err => console.log("error", err))
    .then(() => channel.worker.unref())

// const result4P = Packages.example6.example6$package.run4(arg3, arg1, channel.queue)
// result4P
//     .then(result => console.log("Result4: ", result))
//     .catch(err => console.log("error", err))
//     .then(() => channel.worker.unref())





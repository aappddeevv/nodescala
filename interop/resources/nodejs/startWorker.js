// 
// Run this script from the jvm side to start
// a nodejs worker and return its communication
// queue. This code should be run by using a Context.
// The return value is a java message queue.
//

const javaToJSQueue = new java.util.concurrent.LinkedBlockingDeque();
const {
    Worker, isMainThread, parentPort, workerData
} = require('worker_threads');

// code running in the worker
const worker = new Worker(`
    const { workerData, parentPort } = require('worker_threads');
    while (true) {
        parentPort.postMessage({ command: "EVAL", callback: workerData.take()});
    }
`, { eval: true, workerData: javaToJSQueue });

worker.on('message', ({ command, callback }) => {
    try {
        if (command === "EXIT") process.exit()
        else if (command === "EVAL" && callback) callback();
    } catch (e) {
        console.log(`Error running callback from JVM: ${e}`);
        console.log(e)
    }
});

[worker, javaToJSQueue]

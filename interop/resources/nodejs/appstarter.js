// export a "start" function that allows you to start
// multiple main functions each in their own thread.

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

/** Start a main method in a JVM class with the args specified. A new
 * JVM thread is created each time you call this but the same underlying
 * infrastucture is used to move JS computations to the nodejs thread
 * *when* you do that in your JVM code. 
 */
function start(jvmClassName, options) {
    const exitOnError = options && options.exitOnError ? options.exitOnError : false
    const exitOnComplete = options && options.exitOnComplete ? options.exitOnComplete : false
    const args = options && options.args ? options.args : []
    Packages.nodejs.interop.NodeJS.runMain(jvmClassName, javaToJSQueue, str => eval(str), args, exitOnComplete, exitOnError)
}

// can't pass a class object directly
/** Use a class object, e.g. `Java.type(Packages.exampleX.run)`, to start a thread to run main. */
// function startClass(jvmClass, options) {
//     const exitOnError = options && options.exitOnError ? options.exitOnError : false
//     const exitOnComplete = options && options.exitOnComplete ? options.exitOnComplete : false
//     const args = options && options.args ? options.args : []
//     Packages.nodejs.interop.NodeJS.runClass(jvmClass, javaToJSQueue, str => eval(str), args, exitOnComplete, exitOnError)
// }

// the default export is a function
// module.exports = {
//     start,
//     startClass
// }

module.exports = start
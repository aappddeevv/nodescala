package nodejs
package helpers

import org.graalvm.polyglot._
import scala.jdk.CollectionConverters._
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import scala.language.implicitConversions
import scala.util.control.Exception._
import java.{util => ju}
import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters._
import scala.jdk.FunctionConverters._

import interop.NodeJS

/**
  * Enters the NodeJS event loop thread and makes the JavaScript API available in scope. You can return JavaScript
  * objects from a nodejs block, however, you can't access them again until you're back inside. Attempting to use
  * them outside a nodejs block will trigger a threading violation exception.
  *
  * Note that this function schedules the lambda onto the NodeJS event loop and waits for it to be executed. If Node
  * is busy, it'll block and wait for it.
  */
//fun <T> nodejs(body: NodeJSAPI.() -> T): T = NodeJS.runJS { body(NodeJSAPI()) }

/** Eval a js string, return result. */
def eval(js: String)(using c: ScalaContext) = c.eval(js)

/** Obtain the polyglot bindings--topmost scope of nodejs. For JS, use `Value` methods
 * such as `getMember` to obtain values from the bindings. `Value.hasMembers` should
 * be true since the topmost scope is an object. You have to explicitly access
 * bindings to see them in the foreign language although you could access them
 * through type class flavored interop e.g. `js: Java.type("SomeJVMClass")`.
*/
def bindings(using c: ScalaContext) = c.bindings

/** Extract ExceutionContext from the scala context. */
def nodeJSEC(using c: ScalaContext) = c.nodeJSEC

/** Run on nodejs thread and return a scala Future. */
def runJS[T](thunk: () => T)(using c: ScalaContext) = c.runJS[T](thunk)

/** Access the context. */
def context(using c: ScalaContext) = c.context

/** Expose a variable at the top level nodejs by its name. This means a script
 * in nodejs could access the value by name without accessing the polyglot bindings. 
 * You need to make sure that any JS access takes into account the values specific type, 
 * if that is relevant.
 * 
 * @todo Make this a macro so the name is pulled from the declaration.
 */
def expose(name: String, value: Any)(using c: ScalaContext) = {
  val key = "__nodejvm_transfer"
  // single thread nodejs means we can use the same name Ok
    c.bindings.putMember(key, value)
    // don't use var = because that's a local binding! we want global JS
    c.eval(s"$name = Polyglot.import('__nodejvm_transfer');")
    c.bindings.removeMember(key)
}

/** Run on nodejs thread. Don't block it for too long! Returning values from 
 * method is find but they should always be accessed on the nodejs thread inside
 * this block or by switching to the nodejs thread explicitly.
*/
def nodeJSThread[T](body: ScalaContext ?=> T): T = NodeJS.runJS{ () => 
  given c as ScalaContext
  body
}

/** Tag for forcing some methods to be thoughtfully used in `nodejs`. */
class ScalaContext:
  def eval(js: String): Value = NodeJS.eval(js)
  def runJS[T](thunk: () => T) = NodeJS.runJSAsync(thunk.asJava).asScala
  val bindings = NodeJS.polyglotContext().getPolyglotBindings()
  val nodeJSEC = ExecutionContext.fromExecutor(NodeJS.executor)  
  val context  = NodeJS.polyglotContext()

/**
  * The API for working with the NodeJS world. Accessed by using a [nodejs] block: the methods on this class are in
  * scope when inside the code block.
  */
// class NodeJSAPI() {
//     /**
//      * Converts the [Value] to a JVM type [T] in the following way:
//      *
//      * 1. If the type is an interface not annotated with `@FunctionalInterface` then a special proxy is returned that
//      *    knows how to map JavaBean style property methods on that interface to JavaScript properties.
//      * 2. Otherwise, the [Value. as] method is used with a [TypeLiteral] so generics are preserved and the best possible
//      *    translation occurs.
//      */
//     inline fun <reified T> Value.cast(): T = castValue(this, object : TypeLiteral<T>() {})

//     companion object {
//         /** @suppress */
//         @JvmStatic
//         fun <T> castValue(value: Value, typeLiteral: TypeLiteral<T>): T {
//             val clazz = typeLiteral.rawType
//             @Suppress("UNCHECKED_CAST")
//             return if (JSTranslationProxyHandler.isTranslateableInterface(clazz))
//                 Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz, *clazz.interfaces), JSTranslationProxyHandler(value)) as T
//             else
//                 value.`as`(typeLiteral)
//         }
//     }

//     /** Casts any object to being a JavaScript object. */
//     fun Any.asValue(): Value = NodeJS.polyglotContext().asValue(this)

//     /**
//      * Evaluates the given JavaScript string and casts the result to the desired JVM type. You can request a cast
//      * to interfaces that map to JS objects, collections, the Graal/Polyglot [Value] type, boxed primitives and more.
//      */
//     inline fun <reified T> eval(@Language("JavaScript") javascript: String): T = NodeJS.eval(javascript).cast()

//     /**
//      * Evaluates the given JavaScript but throws away any result.
//      */
//     fun run(/*Language("JavaScript") */javascript: String) {
//         NodeJS.eval(javascript)
//     }

//     /** Allows you to read JS properties of the given [Value] using Kotlin indexing syntax. */
//     inline operator fun <reified T> Value.get(key: String): T = getMember(key).cast()

//     /** Allows you to read JS properties of the given [Value] using Kotlin indexing syntax. */
//     operator fun Value.get(key: String): Value = getMember(key)

//     /** Allows you to set JS properties of the given [Value] using Kotlin indexing syntax. */
//     operator fun Value.set(key: String, value: Any?) = putMember(key, value)

//     private val bindings = NodeJS.polyglotContext().polyglotBindings

//     /**
//      * Implementation for [bind]. The necessary operator functions are defined as extensions to allow for reified generics.
//      * @suppress
//      */
//     class Binding<T>

//     /** @suppress */
//     operator fun <T> Binding<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
//         // This rather ugly hack is required as we can't just insert the name directly,
//         // we have to go via an intermediate 'bindings' map.
//         bindings["__nodejvm_transfer"] = value
//         NodeJS.eval("${property.name} = Polyglot.import('__nodejvm_transfer');")
//         bindings.removeMember("__nodejvm_transfer")
//     }

//     /** @suppress */
//     inline operator fun <reified T> Binding<T>.getValue(thisRef: Any?, property: KProperty<*>): T = eval(property.name)

//     /** @suppress */
//     inner class Binder<T>(private val default: T? = null) {
//         operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): Binding<T> {
//             val b = Binding<T>()
//             if (default != null)
//                 b.setValue(null, prop, default)
//             return b
//         }
//     }

//     /**
//      * Use this in property delegate syntax to access top level global variables in the NodeJS context. By declaring
//      * a variable as `var x: String by bind()` you can read and write the 'x' global variable in JavaScript world.
//      */
//     fun <T> bind(default: T? = null) = Binder(default)
// }

// /** Wraps JS objects with some Bean property convenience glue. */
// private class JSTranslationProxyHandler(private val value: Value) : InvocationHandler {
//     companion object {
//         fun isTranslateableInterface(c: Class<*>) =
//             c.isInterface && !c.isAnnotationPresent(FunctionalInterface::class.java)
//     }

//     // This code does a lot of redundant work on every method call and could be optimised with caches.
//     init {
//         check(nodejs { value.hasMembers() }) { "Cannot translate this value to an interface because it has no members." }
//     }

//     override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
//         // Apply Bean-style naming pattern matching.
//         val name = method.name
//         fun hasPropName(p: Int) = name.length > p && name[p].isUpperCase()
//         val getter = name.startsWith("get") && hasPropName(3)
//         val setter = name.startsWith("set") && hasPropName(3)
//         val izzer = name.startsWith("is") && hasPropName(2)
//         val isPropAccess = getter || setter || izzer
//         val propName = if (isPropAccess) {
//             if (getter || setter) {
//                 name.drop(3).decapitalize()
//             } else {
//                 check(izzer)
//                 name.drop(2).decapitalize()
//             }
//         } else null

//         val returnType = method.returnType
//         val parameterCount = method.parameterCount

//         when {
//             izzer -> check(returnType == Boolean::class.java && parameterCount == 0) {
//                 "Methods starting with 'is' should return boolean and have no parameters."
//             }
//             getter -> check(parameterCount == 0) { "Methods starting with 'get' should not have any parameters." }
//             setter -> check(parameterCount == 1) { "Methods starting with 'set' should have a single parameter." }
//         }

//         NodeJS.checkOnMainNodeThread()

//         return if (propName != null) {
//             if (getter || izzer) {
//                 val member = value.getMember(propName)
//                     ?: throw IllegalStateException("No property with name $propName found: [${value.memberKeys}] ")
//                 member.`as`(returnType)
//             } else {
//                 check(setter)
//                 value.putMember(propName, args!!.single())
//                 null
//             }
//         } else {
//             // Otherwise treat it as a method call.
//             check(value.canInvokeMember(name)) { "Method $name does not appear to map to an executable member: [${value.memberKeys}]" }
//             val result = value.invokeMember(name, *(args ?: emptyArray<Any>()))
//             // The result should be thrown out if expecting void, or translated again if the return type is a
//             // non-functional interface (functional interfaces are auto-translated by Polyglot already), or
//             // otherwise we just rely on the default Polyglot handling which is pretty good most of the time.
//             when {
//                 returnType == Void.TYPE -> null
//                 isTranslateableInterface(returnType) -> Proxy.newProxyInstance(
//                     this.javaClass.classLoader,
//                     returnType.interfaces,
//                     JSTranslationProxyHandler(result)
//                 )
//                 else -> result.`as`(returnType)
//             }
//         }
//     }
// }

/** Value extension methods. Uses *Opt suffix since Value already has as* methods.
* scala types are immutable.
* 
* We have to run `isNull` checks to determine Option value so its a bit verbose.
* A null value concept could be different in different languages.
*/
extension ValueOps on (o: Value):
    def asStringOpt = if o.isNull then None else Option(o.asString())
    def asIntOpt = if o.isNull then None else Option(o.asInt())
    def asFloatOpt = if o.isNull then None else Option(o.asFloat())
    def asDoubleOpt = if o.isNull then None else Option(o.asDouble())
    def asBooleanOpt = if o.isNull then None else Option(o.asBoolean())
    /** Throw it, catch it then return it as a value. */
    def asExceptionOpt: Option[Throwable] =
        try { if o.isException() then o.throwException(); None } catch{ case x@_ => Option(x) }
    def get(m: String) = if o.hasMember(m) then Option(o.getMember(m)) else None
    def asJavaListOpt[T] = if o.hasArrayElements then Option(o.as(classOf[ju.List[T]])) else None
    def asSeqOpt[T] = o.asJavaListOpt[T].map(_.asScala.toVector)
    /** K will typically be a string or number. */
    def asMapOpt[K,V] = if o.isHostObject then Option(o.as(classOf[ju.Map[K,V]]).asScala.toMap) else None

/** Structural type. Not sure this is right yet. */
case class JSValue(o: Value) extends Selectable:
    def selectDynamic(name: String): Value = o.getMember(name)


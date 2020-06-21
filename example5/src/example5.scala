package example5

import zio._
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import nodejs.helpers._

type Book = JSObject:
  val id: String
  val title: String
  val isbn: String
  val pages: Int

val BookMetadata = Map(
    "pages" -> ((value: Value) => value.asInt),
)

def (v: Value).asBook = JSObject(v, BookMetadata).asInstanceOf[Book]


object BookResolvers:
  /** Resolver is called from nodejs, processes something, then returns a value.
    * It does not execute js code inside of node.
    */
  def description(id: String, context: Value) =
    //println(s"description resolver: $id, $context")
    val ctx = Context.getCurrent()
    val bindingsValue = ctx.getPolyglotBindings()
    val booksArray = bindingsValue.getMember("books")
    println(s"booksArray: $booksArray")
    val book1 = JSObject(booksArray.getArrayElement(0), BookMetadata).asInstanceOf[Book]
    val book2 = booksArray.getArrayElement(1).asBook
    println(s"book1: ${book1.id}, ${book1.title}, ${book1.pages}")
    println(s"book2: ${book2.id}, ${book2.title}, ${book2.pages}")
    "A book!"

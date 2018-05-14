package bench

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import State._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
abstract class Bench
    extends ArgonautBench
    with CirceAutoBench
    with CirceManualBench
    with JacksonScalaBench
    with Json4sBench
    // with PlayJsonBench
    with SprayJsonBench
    with UPickleBench { self: Params =>
}

trait Params {

  var foos: Seq[Foo[Option]] = _
  def length: Int
  def depth: Int

  @Setup
  def setup(data: Data): Unit = {
    foos = data.get(length, depth)
  }

}

class Case1 extends Bench with Params {
  @Param(Array("10"))
  var length: Int = _
  @Param(Array("10"))
  var depth: Int = _
}
class Case2 extends Bench with Params {
  @Param(Array("10"))
  var length: Int = _
  @Param(Array("100"))
  var depth: Int = _
}
class Case3 extends Bench with Params {
  @Param(Array("100"))
  var length: Int = _
  @Param(Array("10"))
  var depth: Int = _
}
class Case4 extends Bench with Params {
  @Param(Array("100"))
  var length: Int = _
  @Param(Array("100"))
  var depth: Int = _
}
class Case5 extends Bench with Params {
  @Param(Array("1"))
  var length: Int = _
  @Param(Array("1000"))
  var depth: Int = _
}

trait ArgonautBench { self: Params =>
  import argonaut._, Argonaut._

  implicit val encodeFooForArgonaut: EncodeJson[Foo[Option]] = EncodeJson[Foo[Option]] {
    case Foo(i, foo) => Json("i" -> Json.jNumber(i), "foo" -> foo.asJson)
  }

  @Benchmark
  def encodeArgonaut: String = foos.toList.asJson.nospaces
}

trait CirceAutoBench { self: Params =>
  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.jackson._

  @Benchmark
  def encodeCirceAuto: String = foos.asJson.noSpaces

  @Benchmark
  def encodeCirceAutoJackson: String = jacksonPrint(foos.asJson)

  @Benchmark
  def encodeCirceAutoJacksonB: ByteBuffer = jacksonPrintByteBuffer(foos.asJson)
}

trait CirceManualBench { self: Params =>
  import io.circe.{Encoder, Json}
  import io.circe.syntax._
  import io.circe.jackson._

  implicit val encodeFooCirce: Encoder[Foo[Option]] = Encoder.instance {
    case Foo(i, foo) => Json.obj("i" := Json.fromInt(i), "foo" := foo.asJson)
  }

  @Benchmark
  def encodeCirceManual: String = foos.asJson.noSpaces

  @Benchmark
  def encodeCirceManualJackson: String = jacksonPrint(foos.asJson)

  @Benchmark
  def encodeCirceManualJacksonB: ByteBuffer = jacksonPrintByteBuffer(foos.asJson)
}

trait JacksonScalaBench { self: Params =>
  import com.fasterxml.jackson.databind.ObjectMapper
  import com.fasterxml.jackson.module.scala.DefaultScalaModule
  import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  @Benchmark
  def encodeJackson: String = mapper.writeValueAsString(foos)

  @Benchmark
  def encodeJacksonB: ByteBuffer = ByteBuffer.wrap(mapper.writeValueAsBytes(foos))
}

trait Json4sBench { self: Params =>
  import org.json4s._
  import native.Serialization.{write => nwrite, formats}
  import jackson.Serialization.{write => swrite}

  implicit val noTypeHintsFormats: Formats = formats(NoTypeHints)

  @Benchmark
  def encodeJson4sNative: String = nwrite(foos)

  @Benchmark
  def encodeJson4sJackson: String = swrite(foos)
}

// FIXME: NPE occurred...
trait PlayJsonBench { self: Params =>
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val encodeFooPlayJson: Writes[Foo[Option]] = (
    (JsPath \ "i").write[Int] and
      (JsPath \ "foo").writeNullable[Foo[Option]]
  )(unlift(Foo.unapply[Option]))

  // @Benchmark
  def encodePlayJson: String = Json.stringify(Json.toJson(foos))

  // @Benchmark
  def encodePlayJsonB: ByteBuffer =
    ByteBuffer.wrap(Json.toBytes(Json.toJson(foos)))
}

trait SprayJsonBench { self: Params =>
  import spray.json._
  import DefaultJsonProtocol._

  implicit object FooFormat extends JsonFormat[Foo[Option]] {
    def read(json: JsValue): Foo[Option] = ???
    def write(obj: Foo[Option]): JsValue = JsObject("i" -> JsNumber(obj.i), "foo" -> obj.foo.toJson)
  }

  @Benchmark
  final def encodeSprayJson: String = foos.toJson.compactPrint
}

trait UPickleBench { self: Params =>
  import upickle.default._

  implicit val fooWriter: Writer[Foo[Option]] = macroW

  @Benchmark
  def encodeUPickle: String = write(foos)

  @Benchmark
  def encodeUPickleAst: String = writeJs(foos).render()
}

object State {

  case class Foo[F[_]](i: Int, foo: F[Foo[F]])

  private def genFoo(i: Int, foo: Foo[Option]): Foo[Option] = {
    if (i == 0) foo
    else genFoo(i - 1, Foo(i, Some(foo)))
  }

  @State(Scope.Benchmark)
  class Data {

    def get(length: Int, depth: Int): Seq[Foo[Option]] =
      Iterator
        .continually(genFoo(depth - 1, Foo(depth, Option.empty[Foo[Option]])))
        .take(length)
        .toList
  }
}

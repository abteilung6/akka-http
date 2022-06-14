package part1_recap

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object AkkaStreamsRecap extends App {

  implicit val system: ActorSystem = ActorSystem("AkkaStreamsRecap")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  import system.dispatcher

  val source = Source(1 to 100) // publish elements
  val sink = Sink.foreach[Int](println) // receives elements
  val flow = Flow[Int].map(x => x + 1)

  val runnableGraph = source.via(flow).to(sink)
  // val simpleMaterializedValue = runnableGraph.run() // materialization

  // materialized value
  val sumSink = Sink.fold[Int, Int](0)((currentSum, element) => currentSum + element)
  val sumFuture = source.runWith(sumSink)

  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all the numbers for the simple source is $value")
    case Failure(_) => println("Summing all the numbers frm the simple source failed")
  }

  val anotherMaterializedValue = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.left).run()

  /**
   * materializing a graph means materializing all the components
   * a materialized value can be anything at all
   *
   * backpressure actions
   * - buffer elements
   * - apply a strategy n case the buffer overflows
   * - fail the entire stream
   */
  val bufferedFlow = Flow[Int].buffer(10, OverflowStrategy.dropHead)
  source.async.via(bufferedFlow).async.runForeach {
    e =>
      Thread.sleep(100)
      println(e)
  }
}

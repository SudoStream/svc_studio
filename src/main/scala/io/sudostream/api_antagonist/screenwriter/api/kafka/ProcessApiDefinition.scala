package io.sudostream.api_antagonist.screenwriter.api.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions}
import akka.stream.Materializer
import io.sudostream.api_antagonist.messages.SpeculativeScreenplay
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ProcessApiDefinition {

  implicit def executor: ExecutionContextExecutor

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  def kafkaConsumerBootServers: String
  def kafkaProducerBootServers: String

  def consumerSettings: ConsumerSettings[Array[Byte], SpeculativeScreenplay]

  def producerSettings: ProducerSettings[Array[Byte], SpeculativeScreenplay]

  def logger: LoggingAdapter

  def publishStuffToKafka(): Future[Done] = {
    Consumer.committableSource(consumerSettings, Subscriptions.topics("speculative-screenplay"))
      .map {
        msg =>
          val speculativeScreenplay = msg.record.value()

          println("Got the spec screenplay:\n" + speculativeScreenplay.toString)

          val msgToCommit = ProducerMessage.Message(
            new ProducerRecord[Array[Byte], SpeculativeScreenplay]("greenlit-film", speculativeScreenplay),
            msg.committableOffset)
          println("Screenplay : " + speculativeScreenplay)
          msgToCommit

      }
      .runWith(Producer.commitableSink(producerSettings))
  }

}

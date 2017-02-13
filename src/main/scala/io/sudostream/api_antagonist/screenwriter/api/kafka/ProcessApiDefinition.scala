package io.sudostream.api_antagonist.screenwriter.api.kafka

import java.io.{PrintWriter, StringWriter}

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.sudostream.api_antagonist.messages.SpeculativeScreenplay
import io.sudostream.api_antagonist.screenwriter.api.SwaggerJsonApiToScreenplayWriterConverter
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{ExecutionContextExecutor, Future}

trait ProcessApiDefinition {

  implicit def executor: ExecutionContextExecutor

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  def kafkaConsumerBootServers: String
  def kafkaProducerBootServers: String

  def consumerSettings: ConsumerSettings[Array[Byte], String]

  def producerSettings: ProducerSettings[Array[Byte], SpeculativeScreenplay]

  def logger: LoggingAdapter

  def publishStuffToKafka(): Future[Done] = {
    Consumer.committableSource(consumerSettings, Subscriptions.topics("api-definition"))
      .map {
        msg =>
          val speculativeScreenplay = new SwaggerJsonApiToScreenplayWriterConverter().
            passApiDefinitionToScreenplayWriterAmateur(msg.record.value()).get.generateHappyPathTests

          val msgToCommit = ProducerMessage.Message(
            new ProducerRecord[Array[Byte], SpeculativeScreenplay]("speculative-screenplay", speculativeScreenplay),
            msg.committableOffset)
          println("Screenplay : " + speculativeScreenplay)
          msgToCommit
      }
      .runWith(Producer.commitableSink(producerSettings))
  }

  def publishSingleEventToKafka(speculativeScreenplay: SpeculativeScreenplay): Future[Done] = {

    val done = Source.single(speculativeScreenplay)
      .map { msg =>
        new ProducerRecord[Array[Byte], SpeculativeScreenplay]("speculative-screenplay", msg)
      }
      .runWith(Producer.plainSink(producerSettings))
    logger.info("Message on its way to Kafka")
    logger.debug("Message sent:- " + speculativeScreenplay)

    done.onSuccess {
      case whoKnows => logger.info("All goood :" + whoKnows)
    }

    done.onFailure {
      case e: Throwable =>
        logger.error("Ooooh dear :- " + e.getMessage + "\n")
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        logger.error(sw.toString)
      case somethingElse => logger.error("Not sure what happened! " + somethingElse)
    }

    done
  }

}

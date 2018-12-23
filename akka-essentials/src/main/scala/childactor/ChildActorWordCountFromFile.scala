package childactor

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern.ask
import scala.io.Source._

import scala.concurrent.Await

case class ProcessStringMsg(string: String)

case class StartProcessFileMsg()

case class StringProcessedMsg(words: Integer)

class StringProcessingActor extends Actor { //worker
  override def receive: Receive = {
    case ProcessStringMsg(string) => {
      val wordsInLine = string.split(" ").length //count words per line
      sender ! StringProcessedMsg(wordsInLine)
    }
    case _ => println("ERROR: message not recognised")
  }
}
class ChildActorWordCountFromFile(filename: String) extends Actor { //master
  private var running = false
  private var totalLines = 0
  private var linesProcessed = 0
  private var result = 0
  private var fileSender: Option[ActorRef] = None // original sender

  //////////////////////////////////////
  override def receive: Receive = {
    case StartProcessFileMsg() =>
      if (running) {
        println("Warning: duplicate start message received")
      } else {
        running = true
        fileSender = Some(sender)
        fromFile(filename).getLines.foreach {
          line =>
            println(line)
            context.actorOf(Props[StringProcessingActor]) ! ProcessStringMsg(line)
            totalLines += 1
        }
      }

    case StringProcessedMsg(words) =>
      result += words
      linesProcessed += 1
      if (linesProcessed == totalLines) {
        fileSender.get ! result
      }
    case _ => println("message not recognised")
  }
}

object ChildActorWordCountFromFile extends App {
  val system = ActorSystem("StringProcessing")
  val actor = system.actorOf(Props(new ChildActorWordCountFromFile("src/main/resources/testfiles/declaration.txt")))
  implicit val timeout = Timeout(25 seconds)
  val future = actor ? StartProcessFileMsg()
  val result = Await.result(future, timeout.duration).asInstanceOf[Int]
  println(result)
  system terminate
}

/*
1320
 */
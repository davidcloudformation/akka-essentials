package childactor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}

object ChildActorWordCount extends App {

  ////Master
  object Master {

    case class Initialize(size: Int)
    case class MasterReply(id: Int, wordcount: Map[String, Int])
    case class WorkerTask(id: Int, text: String)

  }

  class Master extends Actor with ActorLogging {

    import Master._

    override def receive: Receive = {
      case Initialize(n) =>
        ////
        val childRefs = for (i <- 0 to n) yield context.actorOf(Props[Worker], s"cm_$i")
        log.info(s"Initialising ....")
        context.become(withChildren(childRefs.toSet, 0, 0, Map(), Map()))


        def withChildren(actorRefs: Set[ActorRef], id: Int, task_id: Int,
                         mapRequest: Map[Int, ActorRef], result: Map[String, Int]): Receive = {
          case text: String =>
            val originalSender = sender()
            val task = WorkerTask(id, text)
            val childRef = childRefs(id)
            childRef ! task // to worker

            val nextIndex = (id + 1) % actorRefs.size
            val mr = mapRequest + (id -> originalSender)

            context.become(withChildren(actorRefs, nextIndex, task_id + 1, mr, result))

          case MasterReply(_id, wordcount) =>
            val originalSender = mapRequest(_id)
            val removeOneId = mapRequest - _id
            val my_result = (wordcount.toList ++ result.toList).groupBy(x => x._1).map(x => (x._1, x._2.map(_._2).sum))
            originalSender ! my_result // to home

            context.become(withChildren(actorRefs, id, task_id, removeOneId, my_result))


        }
    }
  }


  /// Worker
  class Worker extends Actor with ActorLogging {

    import Master._

    override def receive: Receive = {

      case WorkerTask(id, text) =>

        val wordCount = text.split(" ").toList.map(_.toLowerCase).groupBy(identity).mapValues(_.size)
        sender() ! MasterReply(id, wordCount) // to master

    }
  }


  /// Test

  class Test extends Actor with ActorLogging {

    import Master._

    override def receive: Receive = {
      case "go to starting point" =>
        val master = context.actorOf(Props[Master], "master")

        val words = List("Hello world", "My akka o my world", "one More world count Hello","more text here hello","again another series")
        val len = words.length
        master ! Initialize(len)

        words.foreach(x => master ! x)

      case word_count_result =>
        Thread.sleep(2000)

        log.info(s"Current result is : $word_count_result")
    }
  }


  val system = ActorSystem("Test")
  val actor = system.actorOf(Props[Test], "my_mast")

  actor ! "go to starting point" // home


}

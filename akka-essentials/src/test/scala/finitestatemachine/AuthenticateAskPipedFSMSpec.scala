package finitestatemachine

import akka.actor.{Actor, ActorLogging,  ActorSystem, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.pipe

class AuthenticateAskPipedFSMSpec extends TestKit(ActorSystem("AuthenticateAskPipedFSMSpec"))
  with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AuthenticateAskPipedFSMSpec._

  "A piped authenticator" should {
    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props) = {

    "fail to authenticate a non-registered user" in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("david", "tan")
      expectMsg(AuthFailure("username not found"))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("david", "tan")
      authManager ! Authenticate("david", "ten")
      expectMsg(AuthFailure("password incorrect"))
    }

    "successfully authenticate a registered user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("david", "tan")
      authManager ! Authenticate("david", "tan")
      expectMsg(AuthSuccess)
    }
  }

}

object AuthenticateAskPipedFSMSpec {

  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess

  // EVENTS
  case class Read(key: String)
  case class Write(key: String, value: String)
  trait AuthState
  case object Online extends AuthState

  //Data
  trait AuthData
  case class RegisteredUser(kv: Map[String, String]) extends AuthData

  ////
  class AuthMachineFSM extends FSM[AuthState, AuthData] {
    /// starting point
    startWith(Online, RegisteredUser(Map()))

    when(Online) {
      case Event(Write(k, v), RegisteredUser(kv)) => stay using RegisteredUser(kv + (k -> v))

      case Event(Read(key), RegisteredUser(kv)) =>
        Thread.sleep(2500) // slow db
        sender() ! kv.get(key) // Option[String]
        stay()
    }

    whenUnhandled {
      case Event(_, _) =>
        sender() ! AuthFailure("CommandNotFound")
        stay()
    }

    onTransition {
      case stateA -> stateB => log.info(s"Transitioning from $stateA to $stateB")
    }
    initialize()

  }

  class PipedAuthManager extends Actor with ActorLogging {

    // step 2 - logistics
    implicit val timeout: Timeout = Timeout(8 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    protected val authDb = context.actorOf(Props[AuthMachineFSM])

    override def receive: Receive = {
      case RegisterUser(username, password) => authDb ! Write(username, password)
      case Authenticate(username, password) => handleAuthentication(username, password)
    }

    def handleAuthentication(username: String, password: String): Unit = {
      // step 3 - ask the actor
      val future = authDb ? Read(username) // Future[Any]

      // step 4 - process the future until you get the responses you will send back
      val passwordFuture = future.mapTo[Option[String]] // Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure("username not found")
        case Some(dbPassword) =>
          if (dbPassword == password) AuthSuccess else AuthFailure("password incorrect")
      } // Future[Any] - will be completed with the response I will send back

      responseFuture.pipeTo(sender())
    }
  }

}

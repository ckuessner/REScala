package reader.network

import scala.events._
import scala.events.behaviour._
import java.net._

class UrlChecker {
  type CheckArg = String
  type CheckResult = Either[String,URL]
  type AfterCheck = (CheckArg,CheckResult)
  
  
  val urlA = new Var("") 
  
  

  /**
   * Try to increase confidence that the String is a valid feed url
   * by performing some simple checks.
   *
   * @param url The string to check
   * @return Nothing is returned but events are fired, see below
   */
  val check = Observable( (url: String) => help(url) )
    
  private def help(url: String) = {
    urlA() = url
    checkURL(url)
  }
  


  // Tries to create a url from the string and returns it in Right
  // if not successful, a Left with an error message is returned
  private def checkURL(url: String): CheckResult = {
    try {
      val u = new URL(url)
      u.getContent
      Right(u)
    } catch {
      case e: UnknownHostException => Left(errorMessage(url,e))
      case e: MalformedURLException => Left(errorMessage(url,e))
    }
  }
  
  var UrlValid: Signal[Boolean] = Signal{checkURLSignal(urlA.toString)}
  var ErrorMessage: Signal[String] = Signal{EM} 
  
  var EM: String = ""
  
    private def checkURLSignal(url: String): Boolean = {
    System.out.println("here")
      var valid = true
    try {
      val u = new URL(url)
      u.getContent
      Right(u)
      System.out.println("here")
    } catch {
      case e: UnknownHostException => valid = false //EM
      case e: MalformedURLException => valid = false //EM
    }
    return valid
  }

  private lazy val checkSuccessful: Event[CheckResult] = 
    check.after && { t: AfterCheck => t._2.isRight } map { t: AfterCheck => t._2 }

  private lazy val checkFailed: Event[CheckResult] = 
    check.after && { t: AfterCheck => t._2.isLeft } map { t: AfterCheck => t._2 }

  private lazy val checkedOption: Event[Option[URL]] = 
    (checkSuccessful || checkFailed) map { e: CheckResult => e match { case Right(u) => Some(u)
                                                                      case Left(_)  => None } }

  /** Fired for every valid url checked */
  lazy val checkedURL = new ImperativeEvent[URL]
  checkedOption += { opt => opt foreach { checkedURL(_) } }

  /** Only fires if the checked url is valid */
  lazy val urlIsValid: Event[Unit]   = checkSuccessful.dropParam

  /** Only fires if the checked url is invalid */
  lazy val urlIsInvalid: Event[Unit] = checkFailed.dropParam

  private def errorMessage(url: String, e: Exception): String =
    "Error while checking '" + url + "' - " + e.getMessage

}

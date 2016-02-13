package filters

import play.api.Logger
import play.api.mvc._
import sun.misc.BASE64Decoder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object BasicAuthFilter extends Filter {
  private lazy val unauthResult = Results.Unauthorized.withHeaders(("WWW-Authenticate",
    "Basic realm=\"myRealm\""))
  private lazy val passwordRequired = true
  private lazy val username = "chrischivers"
  private lazy val password = "Audacity2005"
  private lazy val protectedList = Seq("control.html","control","admin",
    "stream-started","start-stream","stop-stream","lines-read","lines-read-since-restart","current-rainfall",
    "memory-state","email-alerts-started","start-email-alerting","stop-email-alerting","historical-data-collection-started",
    "start-historical-data-collection","stop-historical-data-collection","size-holding-buffer","number-non-matches",
    "insert-transactions-requested", "insert-transactions-executed","insert-transactions-outstanding","insert-transactions-dropped",
    "pulls-requested","pulls-executed","live-streaming-started","start-live-streaming","stop-live-streaming","number-live-actors",
    "number-live-children","update-route-definitions","update-routes-percentage-complete","update-routes-number-inserted",
    "update-routes-number-updated","update-stop-definitions","update-stops-percentage-complete","update-stops-number-inserted",
    "update-stops-number-updated","add-polylines","add-polylines-number-read","add-polylines-number-from-web","add-polylines-number-from-cache," +
      "clean-up-point-to-point", "point-to-point-clean-checked", "point-to-point-clean-deleted","admin-log", "number-connections")
  //need the space at the end
  private lazy val basicSt = "basic "

  //This is needed if you are behind a load balancer or a proxy
  private def getUserIPAddress(request: RequestHeader): String = {
    return request.headers.get("x-forwarded-for").getOrElse(request.remoteAddress.toString)
  }

  private def logFailedAttempt(requestHeader: RequestHeader) = {
    Logger.warn(s"IP address ${getUserIPAddress(requestHeader)} failed to log in, " +
      s"requested uri: ${requestHeader.uri}")
  }

  private def decodeBasicAuth(auth: String): Option[(String, String)] = {
    if (auth.length() < basicSt.length()) {
      return None
    }
    val basicReqSt = auth.substring(0, basicSt.length())
    if (basicReqSt.toLowerCase() != basicSt) {
      return None
    }
    val basicAuthSt = auth.replaceFirst(basicReqSt, "")
    //BESE64Decoder is not thread safe, don't make it a field of this object
    val decoder = new BASE64Decoder()
    val decodedAuthSt = new String(decoder.decodeBuffer(basicAuthSt), "UTF-8")
    val usernamePassword = decodedAuthSt.split(":")
    if (usernamePassword.length >= 2) {
      //account for ":" in passwords
      return Some(usernamePassword(0), usernamePassword.splitAt(1)._2.mkString)
    }
    None
  }

  private def isProtectedPage(requestHeader: RequestHeader): Boolean = {
    val reqURI = requestHeader.uri
    //remove the first "/" in the uri
    if (protectedList.contains(reqURI.substring(1))) return true
    else false

  }

  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader):
  Future[Result] = {
    if (!passwordRequired || !isProtectedPage(requestHeader)) {
      return nextFilter(requestHeader)
    }

    requestHeader.headers.get("authorization").map { basicAuth =>
      decodeBasicAuth(basicAuth) match {
        case Some((user, pass)) => {
          if (username == user && password == pass) {
            return nextFilter(requestHeader)
          }
        }
        case _ => ;
      }
      logFailedAttempt(requestHeader)
      return Future.successful(unauthResult)
    }.getOrElse({
      logFailedAttempt(requestHeader)
      Future.successful(unauthResult)
    })

  }
}




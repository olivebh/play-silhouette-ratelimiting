package utils.ratelimiting

import java.time.{ Duration, LocalDateTime }

import javax.inject.Inject

import scala.concurrent.Future

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{ Request, Result, Results }

import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.actions.{ SecuredActionBuilder, SecuredRequest, SecuredRequestHandler }

import utils.authentication.DefaultEnv
import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.api.Silhouette
import daos.UserLimitDAO

class SecuredRateLimitingAction @Inject() (
    securedRequestHandler: SecuredRequestHandler,
    environment: Environment[DefaultEnv],
    userLimitDAO: UserLimitDAO) extends SecuredActionBuilder(securedRequestHandler(environment)) {

  private type SecReq[T] = SecuredRequest[DefaultEnv, T]

  private val logger: Logger = Logger(this.getClass())

  override def invokeBlock[A](request: Request[A], block: SecReq[A] => Future[Result]) = {
    val blockWithHeaders = withLimitHeaders(block)
    super.invokeBlock(request, blockWithHeaders)
  }

  private def withLimitHeaders[A](block: SecReq[A] => Future[Result]): SecReq[A] => Future[Result] = { request =>

    val user = request.identity
    val userUUID = request.identity.userId

    userLimitDAO.update(user) flatMap {
      case (userLimit, permitted) =>
        
        val limit = userLimit.limit.toString
        val remaining = (if(userLimit.remaining < 0) 0L else userLimit.remaining).toString 
        val secsToReset = Duration.between(LocalDateTime.now, RateLimit.refreshAt).getSeconds.toString
        val headers = List(
          "X-Rate-Limit-Limit" -> limit, // # of allowed requests in the current period
          "X-Rate-Limit-Remaining" -> remaining, // # of remaining requests in the current period
          "X-Rate-Limit-Reset" -> secsToReset) // # of seconds left in the current period

        if (permitted) {
          logger.info("Permitted user: " + user.fullName)
          block(request).map(_.withHeaders(headers: _*))
        } else {
          logger.warn("Refused user: " + user.fullName)
          val remainingSecs = Duration.between(LocalDateTime.now, RateLimit.refreshAt).getSeconds
          val refuseResponse = Results.TooManyRequests.withHeaders(headers: _*)
          Future.successful(refuseResponse)
        }
    }
  }

}

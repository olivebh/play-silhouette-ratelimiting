package controllers

import java.util.UUID

import javax.inject.Inject

import scala.concurrent.Future

import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{ JsError, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ Action, Controller }

import com.mohiva.play.silhouette.api.{ LoginEvent, LoginInfo, SignUpEvent, Silhouette }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider

import daos.UserLimitDAO
import models.User
import services.UserService
import utils.authentication.{ DefaultEnv, SignUpData }

class SignUpController @Inject() (
    val messagesApi: MessagesApi,
    silhouette: Silhouette[DefaultEnv],
    userService: UserService,
    authInfoRepository: AuthInfoRepository,
    passwordHasher: PasswordHasher,
    userLimitDAO: UserLimitDAO
    ) extends Controller with I18nSupport {

  def submit = Action.async(parse.json) { implicit request =>
    request.body.validate[SignUpData].fold(
      errors => {
        Future.successful(Unauthorized(Json.obj("message" -> Messages("invalid.data"), "errors" -> JsError.toJson(errors))))
      },
      data => {
        val loginInfo = LoginInfo(CredentialsProvider.ID, data.email)
        userService.retrieve(loginInfo).flatMap {
          case Some(user) => Future.successful(BadRequest(Json.obj("message" -> Messages("user.exists"))))
          case None =>
            val user = User(
              userId = UUID.randomUUID(),
              loginInfo = loginInfo,
              firstName = Some(data.firstName),
              lastName = Some(data.lastName),
              fullName = Some(data.firstName + " " + data.lastName),
              email = Some(data.email),
              passwordInfo = None
            )
            
            // Add new User to rate-limit map !!!            
            userLimitDAO add List(user)

            val authInfo = passwordHasher.hash(data.password)
            for {
              user <- userService.save(user)
              authInfo <- authInfoRepository.add(loginInfo, authInfo)
              authenticator <- silhouette.env.authenticatorService.create(loginInfo)
              token <- silhouette.env.authenticatorService.init(authenticator)
            } yield {
              silhouette.env.eventBus.publish(SignUpEvent(user, request))
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              Ok(Json.obj("token" -> token))
            }
        }
      }
    )
  }

}

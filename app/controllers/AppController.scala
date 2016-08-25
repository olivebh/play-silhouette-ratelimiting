package controllers

import javax.inject.{ Inject, Named, Singleton }

import akka.actor.ActorRef

import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.Controller

import com.mohiva.play.silhouette.api.{ LogoutEvent, Silhouette }
import com.mohiva.play.silhouette.api.Authorization.RichAuthorization

import utils.authentication.DefaultEnv
import utils.authorization.Roles.{ AdminRole, UserRole }
import utils.authorization.WithRole
import utils.ratelimiting.SecuredRateLimitingAction
import utils.ratelimiting.RateLimitActor

class AppController @Inject() (
    val silhouette: Silhouette[DefaultEnv],
    @Named(RateLimitActor.Name) val userLimitActor: ActorRef,
    SecuredRateLimitingAction: SecuredRateLimitingAction) extends Controller {// with RateLimiting {

  def user = silhouette.SecuredAction { implicit request =>
    Ok(Json.toJson(request.identity))
  }

  def signOut = silhouette.SecuredAction.async { implicit request =>
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, Ok)
  }

  /* testing rate-limiting */
  def limit = SecuredRateLimitingAction { implicit request =>    
      Ok("Permitted to Limiting action")    
  }

  /* can't do SecuredRateLimitingAction(WithRole(UserRole)) and I don't know why */
  def limitUser = (silhouette.SecuredAction(WithRole(UserRole)) andThen SecuredRateLimitingAction) { implicit request =>
    Ok("Permitted to Limiting action")
  }

  /* testing role-based Authorization */
  def userOnly = silhouette.SecuredAction(WithRole(UserRole)) { implicit request =>
    Ok("SUCCESS! (only  USER)")
  }

  def adminOnly = silhouette.SecuredAction(WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (only ADMIN)")
  }

  def userOrAdmin = silhouette.SecuredAction(WithRole(UserRole) || WithRole(AdminRole)) { implicit request =>
    Ok("SUCCESS! (USER or ADMIN)")
  }
  
}
package utils.authorization

import scala.concurrent.Future
import play.api.mvc.Request
import com.mohiva.play.silhouette.api.{ Authenticator, Authorization }

import models.User
import utils.authorization.Roles.Role
import utils.authentication.DefaultEnv

/**
 * Grants access to a User with the given Role.
 *
 *  {{{
 *     silhouette.SecuredAction(WithRole(UserRole))
 *
 *     // You can also import an implicit ExecutionContext and then use operands: !, ||, &&
 *     silhouette.SecuredAction(WithRole(UserRole) || WithRole(AdminRole))
 *  }}}
 */
case class WithRole(role: Role) extends Authorization[User, DefaultEnv#A] {

  override def isAuthorized[B](user: User, authenticator: DefaultEnv#A)(implicit request: Request[B]): Future[Boolean] =
    Future.successful(user.role == role)

}

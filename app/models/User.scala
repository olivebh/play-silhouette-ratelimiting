package models

import java.util.UUID

import play.api.libs.json.{ Format, Json, OFormat }

import com.mohiva.play.silhouette.api.{ Identity, LoginInfo }
import com.mohiva.play.silhouette.api.util.PasswordInfo

import utils.authorization.Roles.{ Role, UserRole }
import utils.ratelimiting.RateLimit

/** Represents a user.
  */
case class User(
  userId: UUID,
  loginInfo: LoginInfo,
  firstName: Option[String],
  lastName: Option[String],
  fullName: Option[String],
  email: Option[String],
  passwordInfo: Option[PasswordInfo],
  role: Role = UserRole,
  rateLimit: Long = RateLimit.DefaultLimit) extends Identity

object User {

  implicit val passwordInfoJsonFormat = Json.format[PasswordInfo]

  implicit val userJsonFormat: Format[User] = Json.format[User]

  implicit val userJsonOFormat: OFormat[User] = Json.format[User] // used by reactivemongo...
}

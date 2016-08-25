package utils.authentication

import play.api.libs.json.Json

/** Data used for User sign-up
 */
case class SignUpData(
	firstName: String,
	lastName: String,
	email: String,
	password: String)

object SignUpData {

	implicit val jsonFormat = Json.format[SignUpData]
}

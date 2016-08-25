package utils.authorization

import play.api.libs.json._
import play.api.libs.functional.syntax._

object Roles {

	sealed abstract class Role(val name: String)

	case object AdminRole extends Role("admin")
	case object EditorRole extends Role("editor")
	case object UserRole extends Role("user")

	val roles = Seq[Role](AdminRole, UserRole, EditorRole)

	/* JSON implicits */
	val roleReads: Reads[Role] = (__ \ 'name).read[String].map { s =>
		roles.find { r => r.name.equals(s) }.getOrElse(UserRole)
	}
	val roleWrites: Writes[Role] = (__ \ 'name).write[String].contramap { (role: Role) =>
		role.name
	}
	implicit val jsonFormat = Format(roleReads, roleWrites)

}


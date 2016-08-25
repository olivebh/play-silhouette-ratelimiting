package daos.mongo

import java.util.UUID

import javax.inject.Inject

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection

import com.mohiva.play.silhouette.api.LoginInfo

import daos.UserDAO
import models.User
import reactivemongo.api.ReadPreference

class UserDAOMongo @Inject() (reactiveMongoApi: ReactiveMongoApi) extends UserDAO {

	val jsonCollection = reactiveMongoApi.database.map(db => db[JSONCollection]("users"))

	def find(loginInfo: LoginInfo): Future[Option[User]] = jsonCollection.flatMap { users =>
		users.find(Json.obj("loginInfo" -> loginInfo)).one[User]
	}

	def find(userId: UUID): Future[Option[User]] = jsonCollection.flatMap { users =>
		users.find(Json.obj("userId" -> userId)).one[User]
	}

	def find: Future[List[User]] = jsonCollection.flatMap { users =>
		users.find(Json.obj()).cursor[User](ReadPreference.Primary).collect[List]()
	}

	def save(user: User): Future[User] = jsonCollection.flatMap { users =>
		users.insert(user).map(_ => user)
	}

}
package daos.mongo

import javax.inject.Inject

import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.JsObjectDocumentWriter

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO

import models.User
import models.User._
import reactivemongo.play.json.collection.JSONCollection

class PasswordInfoDaoMongo @Inject() (reactiveMongoApi: ReactiveMongoApi) extends DelegableAuthInfoDAO[PasswordInfo] {

	val jsonCollection = reactiveMongoApi.database.map(db => db[JSONCollection]("users"))

	def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = jsonCollection.flatMap { users =>
		for {
			maybeUser <- users.find(Json.obj("loginInfo" -> loginInfo)).one[User]
		} yield maybeUser.flatMap(_.passwordInfo)
	}

	def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = jsonCollection.flatMap { users =>
		users.update(
			Json.obj("loginInfo" -> loginInfo),
			Json.obj("$set" -> Json.obj("passwordInfo" -> authInfo))
		).map(_ => authInfo)
	}

	def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = jsonCollection.flatMap { users =>
		add(loginInfo, authInfo)
	}

	def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = jsonCollection.flatMap { users =>
		add(loginInfo, authInfo)
	}

	def remove(loginInfo: LoginInfo): Future[Unit] = jsonCollection.flatMap { users =>
		users.update(
			Json.obj("loginInfo" -> loginInfo),
			Json.obj("$pull" -> Json.obj("loginInfo" -> loginInfo)) // remove loginInfo from User
		).map(_ => ())
	}

}
















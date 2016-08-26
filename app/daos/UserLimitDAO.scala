package daos

import java.util.UUID
import scala.concurrent.Future
import models.User
import akka.util.ByteString
import redis.ByteStringFormatter
import scala.util.Try
import utils.ratelimiting.UserLimit

trait UserLimitDAO {

  def find(userId: UUID): Future[UserLimit]

  def update(user: User): Future[(UserLimit, Boolean)]

  def add(users: List[User]): Future[Boolean]

  def refresh: Future[Unit]
  
  def cleanup: Future[Unit]

}

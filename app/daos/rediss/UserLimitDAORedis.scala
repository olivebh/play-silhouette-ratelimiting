package daos.rediss

import java.util.UUID

import javax.inject.Inject

import scala.concurrent.Future

import akka.actor.ActorSystem

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import daos.UserLimitDAO
import models.User
import redis.RedisClient
import utils.ratelimiting.UserLimit

class UserLimitDAORedis @Inject() (implicit system: ActorSystem) extends UserLimitDAO {

  private val redis = RedisClient()

  private def limitKey(id: UUID): String = s"user:$id:limit"

  private def remainingKey(id: UUID): String = s"user:$id:remaining"
  
  private def key(id: UUID): String = s"user:$id"

  def add(users: List[User]): Future[Boolean] = {

    val (limits, remainings) = users.map { user =>
      val lim = limitKey(user.userId) -> user.rateLimit
      val rem = remainingKey(user.userId) -> user.rateLimit
      lim -> rem
    }.unzip
    redis.mset(limits.toMap ++ remainings.toMap) // bulk insert
    
    
  }

  def find(userId: UUID): Future[UserLimit] = {

    val (limKey, remKey) = (limitKey(userId), remainingKey(userId))

    val tr = redis.transaction()
    tr.watch(limKey, remKey)
    val futureLimit = tr.incrby(limKey, 0) // get Long...
    val futureRemaining = tr.incrby(remKey, 0)
    tr.exec()

    for {
      limit <- futureLimit
      remaining <- futureRemaining
    } yield UserLimit(userId, limit, remaining)
  }

  def update(user: User): Future[(UserLimit, Boolean)] = {

    val (limKey, remKey) = (limitKey(user.userId), remainingKey(user.userId))

    val tr = redis.transaction()
    tr.watch(limKey, remKey)
    val futureLimit = tr.incrby(limKey, 0)
    val futureRemaining = tr.decr(remKey)
    tr.exec()

    for {
      limit <- futureLimit
      remaining <- futureRemaining
    } yield UserLimit(user.userId, limit, remaining) -> (remaining >= 0)
  }

  def refresh: Future[Unit] = {
    getAll flatMap { userLimits =>
      val (lims, rems) = userLimits.map { u =>
        val lim = limitKey(u.userId) -> u.limit
        val rem = remainingKey(u.userId) -> u.limit
        lim -> rem
      }.unzip
      redis.mset((lims ++ rems).toMap).map(_ => ())
    }
  }

  def cleanup: Future[Unit] = {
    getAll map { userLimits =>
      val (limKeys, remKeys) = userLimits.map(u => limitKey(u.userId) -> remainingKey(u.userId)).unzip
      redis.del((limKeys ++ remKeys): _*)
    }
  }

  /** Gets all userLimits from Redis
    */
  private def getAll: Future[Seq[UserLimit]] = {
    val futureUUIDs = redis.keys("user:*:limit") map { keys =>
      keys map { k =>
        UUID.fromString(k.split(":")(1))
      }
    }
    futureUUIDs flatMap { uuids =>
      Future.traverse(uuids) { find(_) }
    }
  }

}

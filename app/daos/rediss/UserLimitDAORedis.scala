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
import utils.ratelimiting.RateLimit

class UserLimitDAORedis @Inject() (implicit system: ActorSystem) extends UserLimitDAO {

  private val redis = RedisClient()

  private val LimitKey = "limit"
  private val RemainingKey = "remaining"
  private val UserKeyPrefix = "user:limit:"

  private def userKey(id: UUID): String = UserKeyPrefix + id

  def add(users: List[User]): Future[Boolean] = {

    val users2Keys = users.map { user =>
      val lim = LimitKey -> user.rateLimit
      val rem = RemainingKey -> user.rateLimit
      userKey(user.userId) -> Map(lim, rem)
    }
    Future.traverse(users2Keys) { case (key, map) => redis.hmset(key, map) } map {
      _.foldLeft(true) { case (acc, b) => acc && b }
    }
  }

  def find(userId: UUID): Future[UserLimit] = {

    val tr = redis.transaction()
    val key = userKey(userId)
    tr.watch(key)
    val futureLimit = tr.hincrby(key, LimitKey, 0)
    val futureRemaining = tr.hincrby(key, RemainingKey, 0)
    tr.exec()

    for {
      limit <- futureLimit
      remaining <- futureRemaining
    } yield UserLimit(userId, limit, remaining)
  }

  def update(user: User): Future[(UserLimit, Boolean)] = {

    val tr = redis.transaction()
    val key = userKey(user.userId)
    tr.watch(key)
    val futureLimit = tr.hincrby(key, LimitKey, 0)
    val futureRemaining = tr.hincrby(key, RemainingKey, -1)
    tr.exec()

    for {
      limit <- futureLimit
      remaining <- futureRemaining
    } yield UserLimit(user.userId, limit, remaining) -> (remaining >= 0)
  }

  def refresh: Future[Unit] = getAll flatMap { userLimits =>
    println("REFRESH!")

    val users2Keys = userLimits.map { u =>
      val lim = LimitKey -> u.limit
      val rem = RemainingKey -> u.limit
      userKey(u.userId) -> Map(lim, rem)
    }

    Future.traverse(users2Keys) {
      case (key, map) => redis.hmset(key, map)
    }.map(_ =>
      RateLimit.refreshAt = RateLimit.getFreshTime // reset time
    )
  }

  def cleanup: Future[Unit] = {
    for {
      keys <- redis.keys(UserKeyPrefix + "*")
      _ <- redis.del(keys: _*)
    } yield ()
  }

  /** Gets all userLimits from Redis
    */
  private def getAll: Future[Seq[UserLimit]] = {

    redis.keys(UserKeyPrefix + "*") flatMap { keys =>
      Future.traverse(keys) { key =>
        val futureLimit = redis.hincrby(key, LimitKey, 0)
        val futureRemaining = redis.hincrby(key, RemainingKey, 0)

        val uuidString = key.split(":")(2)
        
        for {
          limit <- futureLimit
          remaining <- futureRemaining
        } yield UserLimit(UUID.fromString(uuidString), limit, remaining)

      }
    }
  }

}

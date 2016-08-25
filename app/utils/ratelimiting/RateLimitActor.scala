package utils.ratelimiting

import java.time.LocalDateTime
import java.util.UUID

import javax.inject.{ Inject, Singleton }

import scala.collection.mutable
import scala.concurrent.duration.DurationLong

import akka.actor.{ Actor, ActorSystem, Props, actorRef2Scala }

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import RateLimitActor.{ Refresh, UserLimit }
import daos.UserDAO
import models.User

@Singleton
class RateLimitActor @Inject() (
    system: ActorSystem,
    userDAO: UserDAO) extends Actor {
  import RateLimitActor._

  private val logger = Logger(this.getClass())

  private val userLimits = mutable.Map[UUID, UserLimit]()

  /* Actors are bound eagerly!
   * @see https://github.com/playframework/playframework/blob/3e629df347e847db0476eb798d55dc5254efc4fa/framework/src/play-guice/src/main/scala/play/api/libs/concurrent/AkkaGuiceSupport.scala#L57
   */
  userDAO.find map (_ foreach addUserToMap) // add all users from db

  /*
   * refresh automatically
   */
  system.scheduler.scheduleOnce(WindowSize.minutes) {
    self ! Refresh()
  }

  private def addUserToMap(user: User) = {
    val usersLimit = UserLimit(limit = user.rateLimit, remaining = user.rateLimit, expirationTime = getFreshTime)
    userLimits += (user.userId -> usersLimit)
  }

  def receive = {

    /** when new user signs up, add it to map
      */
    case Add(users: List[User]) => {
      users foreach addUserToMap
      logger.info(s"Succesfully loaded ${users.size} to rate limit map.")
    }

    /** Returns Option[(UserLimit, Boolean)] - (updated user in map, permitted)
      */
    case Update(user: User) => {
      val response = userLimits.get(user.userId) flatMap { oldUserLimit =>

        val newRemaining = {
          if (oldUserLimit.remaining > 0) oldUserLimit.remaining - 1
          else 0 // don't got negative..
        }
        val newUserLimit = oldUserLimit.copy(remaining = newRemaining) // cut down one request
        
        userLimits.update(user.userId, newUserLimit)
        userLimits.get(user.userId) map { _ ->  (oldUserLimit.remaining > 0) }
      }
      sender() ! response
    }

    /** Returns Option[(UserLimit, Boolean)] - (updated user in map, permitted)
      */
    case Get(user: User) => {
      sender() ! userLimits.get(user.userId)
    }

    /** Refresh all user limits.
      */
    case Refresh() => userLimits foreach {
      case (uuid, userLimit) =>
        val updated = userLimit.copy(remaining = DefaultLimit, expirationTime = getFreshTime)
        userLimits.update(uuid, updated)
    }

  }

}

object RateLimitActor {

  final val Name = "rate-limit-actor"

  case class UserLimit(limit: Long, remaining: Long, expirationTime: LocalDateTime) { // one entry per User, reset every 15 minutes
    def isExpired: Boolean = expirationTime isBefore LocalDateTime.now
  }

  val DefaultLimit = 10L // total requests limit per Rate

  private val WindowSize = 1L // minutes
  private def getFreshTime = LocalDateTime.now.plusMinutes(WindowSize)

  /* actor */
  def props = Props[RateLimitActor]

  /* messages */
  case class Add(users: List[User])
  case class Update(user: User)
  case class Get(user: User)
  case class Refresh()

}
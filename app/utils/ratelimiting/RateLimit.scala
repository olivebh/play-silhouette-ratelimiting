package utils.ratelimiting

import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import daos.{ UserDAO, UserLimitDAO }
import play.api.inject.ApplicationLifecycle

/** Initializes Redis db with UserLimits.
  *
  * Schedules a fixed refresh every WindowSize.minutes for all user limits.
  *
  * @author Sake
  */
@Singleton
class RateLimit @Inject() (
    system: ActorSystem,
    lifecycle: ApplicationLifecycle,
    userLimitDAO: UserLimitDAO,
    userDAO: UserDAO) {

  import scala.concurrent.duration.DurationLong
  import RateLimit._

  /* on app start, fill Redis */
  userDAO.find.flatMap { users =>
    userLimitDAO.add(users)
  }

  /* every WindowSize.minutes do a refresh */
  system.scheduler.scheduleOnce(WindowSize.minutes) {
    refreshAt = getFreshTime // reset time
    userLimitDAO.refresh
  }
  
  /* on app stop, cleanup Redis */
  lifecycle.addStopHook { () => 
    userLimitDAO.cleanup
  }

}

object RateLimit {

  import java.time.LocalDateTime

  val DefaultLimit: Long = 10L // total requests limit per Rate

  val WindowSize: Long = 1L // minutes

  var refreshAt: LocalDateTime = getFreshTime // global clock, for resetting user limits

  def getFreshTime: LocalDateTime = LocalDateTime.now.plusMinutes(WindowSize)

}
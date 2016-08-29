package utils.ratelimiting

import java.time.LocalDateTime

import javax.inject.{ Inject, Singleton }

import scala.concurrent.duration.DurationLong

import akka.actor.ActorSystem

import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import daos.{ UserDAO, UserLimitDAO }

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
  val bla = system.scheduler.schedule(0.seconds, WindowSize.minutes) {
    userLimitDAO.refresh
  }

  /* on app stop, cleanup Redis */
  lifecycle.addStopHook { () =>
    bla.cancel()
    userLimitDAO.cleanup
  }

}

object RateLimit {

  import java.time.LocalDateTime

  val DefaultLimit: Long = 10L // total requests limit per Rate

  val WindowSize: Long = 1L // minutes

  var refreshAt: LocalDateTime = LocalDateTime.now.plusMinutes(WindowSize) // global clock, for resetting user limits

  def getFreshTime: LocalDateTime = LocalDateTime.now.plusMinutes(WindowSize)

}
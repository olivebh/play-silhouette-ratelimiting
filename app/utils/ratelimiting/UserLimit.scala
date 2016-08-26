package utils.ratelimiting

import java.util.UUID

case class UserLimit(userId: UUID, limit: Long, remaining: Long)
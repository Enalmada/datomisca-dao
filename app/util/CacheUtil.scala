package util

import play.api.http.HeaderNames

import scala.concurrent.duration._

object CacheUtil extends HeaderNames {

  val cache = Seq(CACHE_CONTROL -> s"public, max-age=3600")

  def cache(duration: Duration = 1.hour) = Seq(CACHE_CONTROL -> s"public, max-age=${duration.toSeconds}")

  val noCache = Seq(CACHE_CONTROL -> "no-cache, no-store, must-revalidate",
    PRAGMA -> "no-cache",
    EXPIRES -> "0")

}
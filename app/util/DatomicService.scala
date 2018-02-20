package util

import javax.inject.{Inject, Singleton}

import datomisca._
import datomiscadao.DB
import models.User.Role
import models._
import play.api.i18n.MessagesApi
import play.api.inject.ApplicationLifecycle
import play.api.{Logger, Mode}
import services.DatomiscaPlayPlugin

import scala.concurrent.{ExecutionContext, Future}

object DatomicService {
  implicit var connOpt: Option[Connection] = None

  implicit def conn(): Connection = connOpt.get
}

@Singleton
class DatomicService @Inject()(implicit env: play.api.Environment, ec: ExecutionContext, config: play.api.Configuration,
                               lifecycle: ApplicationLifecycle, wsClient: play.api.libs.ws.WSClient, messagesApi: MessagesApi) {

  // Test
  Logger.debug("My Datomisca initialized.")

  val datomiscaPlayPlugin = new DatomiscaPlayPlugin(config)

  def connectionUrl(appKey: String): String = datomiscaPlayPlugin.uri(appKey)

  implicit val conn: Connection = if (env.mode == Mode.Test) {
    Datomic.createDatabase(connectionUrl("test"))
    Datomic.connect(connectionUrl("test"))
  } else {
    Datomic.createDatabase(connectionUrl("prod"))
    Datomic.connect(connectionUrl("prod"))
  }


  DatomicService.connOpt = Some(conn)

  if (env.mode == Mode.Test) {
    loadSchema(check = false)
  } else {

    if (env.mode == Mode.Dev) {
      //Datomic.deleteDatabase(connectionUrl("prod"))
    }

    loadSchema()
    defaultData()

  }

  def testShutdown(): Connection = {
    Logger.debug("My Datomisca shutdown.")
    Datomic.deleteDatabase(connectionUrl("test"))
    Datomic.createDatabase(connectionUrl("test"))
    Datomic.connect(connectionUrl("test"))
  }

  if (env.mode == Mode.Test) {
    lifecycle.addStopHook { () =>
      Future.successful(testShutdown())
    }
  } else {

    if (env.mode == Mode.Dev) {

      lifecycle.addStopHook { () =>
        conn.release()
        Logger.debug("peer -conn release")
        //Peer.shutdown(false)
        //Logger.debug("peer - shutdown")
        Future.successful(true)
      }
    }
  }

  def loadSchema(check: Boolean = true)(implicit conn: Connection): Unit = {
    implicit val db = Datomic.database

    val combinedSchema = User.Schema.schema ++
      Configuration.Schema.schema ++
      DBVersion.Schema.schema

    DB.loadSchema(combinedSchema, check)

  }

  def defaultData() = {
    // Default data
    User.findByEmail("adam@factya.com") match {
      case None =>
        Logger.debug("Adding default users")
        val adamPre = User(email = "adam@factya.com".toLowerCase, password = "someBcryptSaltedThing", role = Role.Tech, active = true, validated = true)
        val adamIdFut = User.create(adamPre)

        val followingPre = User(email = "following@factya.com".toLowerCase, password = "someBcryptSaltedThing", role = Role.Tech, active = true, validated = true)
        val followingIdFut = User.create(followingPre)

        for {
          adamId <- adamIdFut
          followingId <- followingIdFut
        } yield {
          User.addFollowing(adamId, followingId)
        }

      case _ =>
    }


  }


}
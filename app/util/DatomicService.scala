package util

import datomisca.plugin.DatomiscaPlayPlugin
import datomisca.{Connection, Datomic}
import datomiscadao.DB
import models.User.Role
import models._
import org.mindrot.jbcrypt.BCrypt
import play.api.Play.current
import play.api.{Application, Logger}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object DatomicService {

  // Hack to control which connection to use dev/test
  var connString = ""

  val uri = DatomiscaPlayPlugin.uri("prod")
  val test = DatomiscaPlayPlugin.uri("test")

  // Imported into models to provide the implicit connection
  lazy implicit val conn: Connection = {
    Datomic.connect(connString)
  }

  def testStart() = {
    play.Logger.info("created DB:" + Datomic.createDatabase(test))

    conn
    loadSchema()

  }

  def testEnd() = {

    // drop the test schema
    Datomic.deleteDatabase(test)

  }

  def normalStart(app: Application) = {

    play.Logger.info("created DB:" + Datomic.createDatabase(uri))
    conn

    loadSchema()
    defaultData()
    postMigrations()

  }

  def loadSchema() = {
    implicit val db = Datomic.database

    val combinedSchema = User.Schema.schema ++
      Configuration.Schema.schema ++
      DBVersion.Schema.schema

    val filteredSchema = combinedSchema.filterNot(s => DB.hasAttribute(s.ident))

    if (filteredSchema.nonEmpty) {
      val fut = Datomic.transact(filteredSchema) map { tx =>
        println(s"Loaded Schema: $filteredSchema")
      }

      Await.result(fut, Duration("3 seconds"))
    }

  }

  def defaultData() = {
    // Default data
    if (User.findAll.isEmpty) {
      Logger.debug("Adding default users")
      val user = User(email = "user@example.com".toLowerCase, password = BCrypt.hashpw("password", BCrypt.gensalt()), role = Role.Tech, active = true, validated = true)
      User.create(user)

    }


  }


  def postMigrations() = {
    Logger.info("Doing postMigrations")
    val dbVersion = DBVersion.getDbVersion
    Logger.info(s"DBVersion: ${dbVersion.version}")

  }


}
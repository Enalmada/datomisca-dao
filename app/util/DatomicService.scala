package util

import datomisca.{Connection, Datomic}
import datomiscadao.DB
import models.User.Role
import models._
import org.mindrot.jbcrypt.BCrypt
import play.api.{Application, Logger}
import services.DatomiscaPlayPlugin

object DatomicService {

  // Hack to control which connection to use dev/test
  var connString = ""

  def uri(app: Application) = new DatomiscaPlayPlugin(app).uri("prod")

  def test(app: Application) = new DatomiscaPlayPlugin(app).uri("test")

  // Imported into models to provide the implicit connection
  lazy implicit val conn: Connection = {
    Datomic.connect(connString)
  }

  def testStart(app: Application) = {
    play.Logger.info("created DB:" + Datomic.createDatabase(test(app)))

    conn
    loadSchema()

  }

  def testEnd(app: Application) = {

    // drop the test schema
    Datomic.deleteDatabase(test(app))

  }

  def normalStart(app: Application) = {

    play.Logger.info("created DB:" + Datomic.createDatabase(uri(app)))
    conn

    loadSchema()
    defaultData()
    postMigrations()

  }

  def loadSchema() = {

    val combinedSchema = User.Schema.schema ++
      Configuration.Schema.schema ++
      DBVersion.Schema.schema

      DB.loadSchema(combinedSchema)

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
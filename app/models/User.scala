package models

import java.util.TimeZone

import datomisca.DatomicMapping._
import datomisca._
import datomiscadao.Sort.SortBy
import datomiscadao.{DB, IdEntity, Page, PageFilter}
import models.User.{Role, Schema}
import models.User.Role.Role

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls

case class User(id: Long = -1L,
                email: String = "",
                validated: Boolean = false,
                imageUrl: Option[String] = None,
                password: String = "",
                role: Role = Role.Member,
                active: Boolean = true,
                notes: Option[String] = None) extends IdEntity {

  def isAdmin = role == Role.Administrator || role == Role.Tech

  def following()(implicit conn: Connection): Vector[User] = User.refListByParentId(Schema.following, id)

  def usersFollowing()(implicit conn: Connection): Vector[User] = User.refListByChildId(Schema.following, id)

  /**
    * Returns default TimeZone for the User. May be implemented as a persisted property in the future.
    */
  def timeZone = User.DefaultTimeZone

  /**
    * Formats a date using the User's preferences. Pattern may be implemented as a persisted property in the future.
    */

}

object User extends DB[User] {

  object Role extends {

    sealed trait Role

    case object Tech extends Role

    case object Administrator extends Role

    case object Member extends Role

    def all: List[Role] = Tech :: Administrator :: Member :: Nil

    def fromString(str: String): Option[Role] = all.find(_.toString == str)

    def displayValues: List[(String, String)] = {
      all.map(p => (p.toString, p.toString))
    }
  }

  object Schema {

    object ns {
      val user = new Namespace("user") {
        val role = Namespace("role")
      }
    }

    // Attributes
    val email = Attribute(ns.user / "email", SchemaType.string, Cardinality.one).withUnique(Unique.identity).withDoc("User's email address")
    val validated = Attribute(ns.user / "validated", SchemaType.boolean, Cardinality.one).withDoc("Is email address validated")
    val imageUrl = Attribute(ns.user / "imageUrl", SchemaType.string, Cardinality.one).withDoc("Gravatar image")
    val password = Attribute(ns.user / "password", SchemaType.string, Cardinality.one).withDoc("The encrypted password")
    val role = Attribute(ns.user.role / "role", SchemaType.ref, Cardinality.one).withDoc("The level of permission")
    val active = Attribute(ns.user / "active", SchemaType.boolean, Cardinality.one).withDoc("Is the account active")
    val notes = Attribute(ns.user / "notes", SchemaType.string, Cardinality.one).withDoc("Notes about the user")

    val following: Attribute[DatomicRef.type, Cardinality.many.type] = Attribute(ns.user / "following", SchemaType.ref, Cardinality.many).withDoc("user following these people")

    // Role enumerated values
    val tech = AddIdent(ns.user.role / Role.Tech.toString)
    val administrator = AddIdent(ns.user.role / Role.Administrator.toString)
    val member = AddIdent(ns.user.role / Role.Member.toString)

    val schema = Seq(
      email, validated, imageUrl, password, role, active, notes,
      tech, administrator, member,
      following
    )

  }

  implicit val kwToRole: datomisca.Keyword => Role.Role = (kw: datomisca.Keyword) => Role.fromString(kw.getName).get
  implicit val roletoKw: Role.Role => datomisca.Keyword = (role: Role.Role) => Schema.ns.user.role / role.toString

  implicit val reader: EntityReader[User] = (
    ID.read[Long] and
      Schema.email.read[String] and
      Schema.validated.readOrElse[Boolean](false) and
      Schema.imageUrl.readOpt[String] and
      Schema.password.read[String] and
      Schema.role.read[Role] and
      Schema.active.read[Boolean] and
      Schema.notes.readOpt[String]
    ) (User.apply _)

  implicit val writer: PartialAddEntityWriter[User] = (
    ID.write[Long] and
      Schema.email.write[String] and
      Schema.validated.write[Boolean] and
      Schema.imageUrl.writeOpt[String] and
      Schema.password.write[String] and
      Schema.role.write[Role] and
      Schema.active.write[Boolean] and
      Schema.notes.writeOpt[String]
    ) (unlift(User.unapply))


  val DefaultTimeZone = TimeZone.getTimeZone("America/Los_Angeles")

  def findById(id: Long)(implicit conn: Connection) = {
    User.find(id, Datomic.database)
  }

  val queryAll = Query(
    """
    [
      :find ?a
      :where
        [?a :user/email]
    ]
    """)

  def findAll()(implicit conn: Connection): Seq[User] = {
    DB.list(Datomic.q(queryAll, Datomic.database), Datomic.database()).toSeq
  }

  val findByEmailQuery = Query(
    """
    [
      :find ?a
      :in $ ?email
      :where
        [?a :user/email ?email]
    ]
    """)


  def findByEmail(email: String)(implicit conn: Connection): Option[User] = {
    headOption(Datomic.q(findByEmailQuery, Datomic.database, email.toLowerCase), Datomic.database())
  }

  def delete(id: Long)(implicit conn: Connection) = User.retractEntity(id)

  def create(user: User)(implicit conn: datomisca.Connection): Future[Long] = {

    val userFact = DatomicMapping.toEntity(DId(Partition.USER))(user)

    val allFacts = Seq(userFact)
    DB.transact(allFacts, userFact.id)


  }

  def update()(implicit id: Long, user: User, conn: Connection): Unit = {
    val o = User.get(id, Datomic.database)

    val userFacts: Seq[TxData] = Seq(
      DB.factOrNone(o.email, user.email, Schema.email -> user.email),
      DB.factOrNone(o.validated, user.validated, Schema.validated -> user.validated),
      DB.factOrNone(o.imageUrl, user.imageUrl, Schema.imageUrl -> user.imageUrl.getOrElse("")),
      DB.factOrNone(o.password, user.password, Schema.password -> user.password),
      DB.factOrNone(o.role, user.role, Schema.role -> user.role),
      DB.factOrNone(o.active, user.active, Schema.active -> user.active),
      DB.factOrNone(o.notes, user.notes, Schema.notes -> user.notes.getOrElse(""))
    ).flatten

    DB.transactAndWait(userFacts)

  }

  val lowerFindByEmailQuery = Query(
    """
    [
      :find ?a
      :in $ ?email
      :where
        [?a :user/email ?originalEmail]
      [(.toLowerCase ^String ?originalEmail) ?lowercaseEmail]
      [(= ?lowercaseEmail ?email)]
    ]
    """)

  def list(queryOpt: Option[String], sortBy: SortBy, pageFilter: PageFilter)(implicit conn: Connection): Page[User] = {
    implicit val db = Datomic.database

    queryOpt match {
      case Some(email) => User.page(Datomic.q(lowerFindByEmailQuery, Datomic.database, email.toLowerCase), pageFilter)
      case None => User.page(Datomic.q(queryAll, Datomic.database), pageFilter)
    }

  }

  def addFollowing(followerId: Long, userId: Long)(implicit conn: datomisca.Connection): Future[TxReport] = {
    val fact = SchemaFact.add(followerId)(Schema.following -> userId)
    Datomic.transact(fact)
  }

  def removeFollowing(followerId: Long, userId: Long)(implicit conn: datomisca.Connection): Future[TxReport] = {
    val fact = SchemaFact.retract(followerId)(Schema.following -> userId)
    Datomic.transact(fact)
  }

}


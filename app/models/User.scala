package models

import java.util.TimeZone

import datomisca.DatomicMapping._
import datomisca._
import datomiscadao.Sort.{Asc, SortBy}
import datomiscadao.{DB, IdEntity, Page, PageFilter}
import models.User.Role
import models.User.Role.Role
import org.mindrot.jbcrypt.BCrypt
import util.DatomicService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.reflectiveCalls

case class User(id: Long = -1L,
                email: String = "",
                password: String = "",
                validated: Boolean = false,
                role: Role = Role.Member,
                active: Boolean = true,
                notes: Option[String] = None) extends IdEntity {

  def isAdmin = role == Role.Administrator || role == Role.Tech

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
    val password = Attribute(ns.user / "password", SchemaType.string, Cardinality.one).withDoc("The encrypted password")
    val validated = Attribute(ns.user / "validated", SchemaType.boolean, Cardinality.one).withDoc("Is email address validated")
    val role = Attribute(ns.user.role / "role", SchemaType.ref, Cardinality.one).withDoc("The level of permission")
    val active = Attribute(ns.user / "active", SchemaType.boolean, Cardinality.one).withDoc("Is the account active")
    val notes = Attribute(ns.user / "notes", SchemaType.string, Cardinality.one).withDoc("Notes about the user")


    // Role enumerated values
    val tech = AddIdent(ns.user.role / Role.Tech.toString)
    val administrator = AddIdent(ns.user.role / Role.Administrator.toString)
    val member = AddIdent(ns.user.role / Role.Member.toString)

    val schema = Seq(
      email, validated, password, role, active, notes,
      tech, administrator, member
    )

  }

  implicit val kwToRole: datomisca.Keyword => Role.Role = (kw: datomisca.Keyword) => Role.fromString(kw.getName).get
  implicit val roletoKw: Role.Role => datomisca.Keyword = (role: Role.Role) => Schema.ns.user.role / role.toString

  implicit val reader: EntityReader[User] = (
    ID.read[Long] and
      Schema.email.read[String] and
      Schema.password.read[String] and
      Schema.validated.readOrElse[Boolean](false) and
      Schema.role.read[Role] and
      Schema.active.read[Boolean] and
      Schema.notes.readOpt[String]
    ) (User.apply _)

  implicit val writer: PartialAddEntityWriter[User] = (
    ID.write[Long] and
      Schema.email.write[String] and
      Schema.password.write[String] and
      Schema.validated.write[Boolean] and
      Schema.role.write[Role] and
      Schema.active.write[Boolean] and
      Schema.notes.writeOpt[String]
    ) (unlift(User.unapply))


  val DefaultTimeZone = TimeZone.getTimeZone("America/Los_Angeles")

  def findById(id: Long) = User.find(id)

  val queryAll = Query(
    """
    [
      :find ?a
      :where
        [?a :user/email]
    ]
    """)

  def findAll: Seq[User] = {
    list(execute0(queryAll))
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


  def findByEmail(email: String): Option[User] = {
    headOption(execute1(findByEmailQuery, email.toLowerCase))
  }

  def delete(id: Long) = User.retractEntity(id)

  def create(user: User): Future[User] = {

    val userFact = DatomicMapping.toEntity(DId(Partition.USER))(user)

    val allFacts = Seq(userFact)
    User.createEntity(allFacts, userFact.id)

  }

  def update(implicit id: Long, user: User): Future[User] = {
    val o = User.get(id)

    val userFacts: Seq[TxData] = Seq(
      DB.factOrNone(o.email, user.email, Schema.email -> user.email),
      DB.factOrNone(o.validated, user.validated, Schema.validated -> user.validated),
      DB.factOrNone(o.password, user.password, Schema.password -> user.password),
      DB.factOrNone(o.role, user.role, Schema.role -> user.role),
      DB.factOrNone(o.active, user.active, Schema.active -> user.active),
      DB.factOrNone(o.notes, user.notes, Schema.notes -> user.notes.getOrElse(""))
    ).flatten

    User.updateEntity(userFacts, id)

  }

  val queryAllWithEmail = Query(
    """
    [
      :find ?a ?email
      :where
        [?a :user/email ?email]
    ]
    """)

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

  def list(queryOpt: Option[String], sortBy: SortBy, pageFilter: PageFilter): Page[User] = {
    implicit val db = Datomic.database

    queryOpt match {
      case Some(email) => User.page(Datomic.q(lowerFindByEmailQuery, Datomic.database, email.toLowerCase), pageFilter)
      case None => User.pageWithSort(Datomic.q(queryAllWithEmail, Datomic.database), pageFilter, Asc)
    }

  }

  def authenticate(email: String, password: String): Option[User] = {
    for {
      user <- User.findByEmail(email)
      if BCrypt.checkpw(password, user.password)
    } yield user
  }


}


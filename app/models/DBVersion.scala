package models

import java.lang

import datomisca.DatomicMapping._
import datomisca._
import datomisca.gen.TypedQuery0
import datomiscadao.DB

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DBVersion(id: Long = -1,
                     version: Int = 0)

object DBVersion extends DB[DBVersion] {

  object Schema {

    object ns {
      val dbversion = new Namespace("dbversion")
    }

    val version: Attribute[lang.Long, Cardinality.one.type] = Attribute(ns.dbversion / "version", SchemaType.long, Cardinality.one).withDoc("Version number")

    val schema = Vector(version)

  }

  /*_*/
  implicit val reader: EntityReader[DBVersion] = (
    ID.read[Long] and
      Schema.version.read[Int]
    ) (DBVersion.apply _)
  /*_*/

  implicit val writer: PartialAddEntityWriter[DBVersion] = (
    ID.write[Long] and
      Schema.version.write[Int]
    ) (unlift(DBVersion.unapply))


  def create(dbVersion: DBVersion)(implicit conn: Connection): Future[Long] = {

    val newVersion = DatomicMapping.toEntity(DId(Partition.USER))(dbVersion)

    DB.transact(Vector(newVersion), newVersion.id)

  }

  def update(id: Long, dbVersion: DBVersion)(implicit conn: Connection): Unit = {
    implicit val primaryId: Long = id
    val o = DBVersion.get(id)

    val facts: TraversableOnce[TxData] = Vector(
      DB.factOrNone(o.version, dbVersion.version, Schema.version -> dbVersion.version)
    ).flatten

    DB.transact(facts)

  }

  val queryAll: TypedQuery0[Any] = /*_*/ Query(
    """
    [
      :find ?e
      :where
        [?e :dbversion/version]
    ]
    """) /*_*/


  def getDbVersion()(implicit conn: Connection): Future[DBVersion] = {

    DBVersion.headOption(Datomic.q(queryAll, Datomic.database)) match {
      case Some(dbVersion) => Future.successful(dbVersion)
      case None =>
        DBVersion.create(DBVersion()).map(x => DBVersion.get(x))
    }

  }

  def updateVersion(dbVersion: DBVersion)(implicit conn: Connection): Unit = {
    val copy = dbVersion.copy(version = dbVersion.version + 1)
    DBVersion.update(dbVersion.id, copy)
  }

  def delete(id: Long)(implicit conn: Connection): Future[TxReport] = DBVersion.retractEntity(id)


}
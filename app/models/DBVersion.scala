package models

import datomisca.DatomicMapping._
import datomisca._
import datomiscadao.DB
import util.DatomicService._

import scala.concurrent.ExecutionContext.Implicits.global

case class DBVersion(id: Long = -1,
                     version: Int = 0)

object DBVersion extends DB[DBVersion] {

  object Schema {

    object ns {
      val dbversion = new Namespace("dbversion")
    }

    val version = Attribute(ns.dbversion / "version", SchemaType.long, Cardinality.one).withDoc("Version number")

    val schema = Seq(version)

  }

  implicit val reader: EntityReader[DBVersion] = (
    ID.read[Long] and
      Schema.version.read[Int]
    ) (DBVersion.apply _)

  implicit val writer: PartialAddEntityWriter[DBVersion] = (
    ID.write[Long] and
      Schema.version.write[Int]
    ) (unlift(DBVersion.unapply))


  def create(dbVersion: DBVersion): Long = {

    val newVersion = DatomicMapping.toEntity(DId(Partition.USER))(dbVersion)

    DB.transactAndWait(Seq(newVersion), newVersion.id)

  }

  def update(implicit id: Long, dbVersion: DBVersion): Unit = {

    val o = DBVersion.get(id)

    val facts: TraversableOnce[TxData] = Seq(
      DB.factOrNone(o.version, dbVersion.version, Schema.version -> dbVersion.version)
    ).flatten

    DB.transactAndWait(facts)

  }

  val queryAll = Query(
    """
    [
      :find ?e
      :where
        [?e :dbversion/version]
    ]
    """)


  def getDbVersion: DBVersion = {

    DBVersion.headOption(Datomic.q(queryAll, Datomic.database), Datomic.database()) match {
      case Some(dbVersion) => dbVersion._2
      case None => {
        val id = DBVersion.create(DBVersion())
        DBVersion.get(id)
      }
    }

  }

  def updateVersion(dbVersion: DBVersion) = {
    val copy = dbVersion.copy(version = dbVersion.version + 1)
    DBVersion.update(dbVersion.id, copy)
  }

  def delete(id: Long) = DBVersion.retractEntity(id)


}
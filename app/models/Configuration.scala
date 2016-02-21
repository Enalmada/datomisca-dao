package models

import datomisca.DatomicMapping._
import datomisca._
import datomiscadao.{DB, IdEntity, Page, PageFilter}
import util.DatomicService._

import scala.concurrent.ExecutionContext.Implicits.global

case class Configuration(id: Long = -1L,
                         configKey: String,
                         configValue: Option[String] = None,
                         notes: Option[String] = None) extends IdEntity


object Configuration extends DB[Configuration] {

  object Schema {

    object ns {
      val configuration = new Namespace("configuration")
    }

    // Attributes
    val configKey = Attribute(ns.configuration / "configkey", SchemaType.string, Cardinality.one).withUnique(Unique.identity).withDoc("Config primary key")
    val configValue = Attribute(ns.configuration / "configvalue", SchemaType.string, Cardinality.one).withDoc("Configuration value")
    val notes = Attribute(ns.configuration / "notes", SchemaType.string, Cardinality.one).withDoc("notes about the config")

    val schema = Seq(
      configKey, configValue, notes
    )

  }

  implicit val reader: EntityReader[Configuration] = (
    ID.read[Long] and
      Schema.configKey.read[String] and
      Schema.configValue.readOpt[String] and
      Schema.notes.readOpt[String]
    ) (Configuration.apply _)

  implicit val writer: PartialAddEntityWriter[Configuration] = (
    ID.write[Long] and
      Schema.configKey.write[String] and
      Schema.configValue.writeOpt[String] and
      Schema.notes.writeOpt[String]
    ) (unlift(Configuration.unapply))


  def delete(id: Long) = Configuration.retractEntity(id)

  def save(configuration: Configuration): Configuration = {

    if (configuration.id == -1L) {
      create(configuration)
    } else {
      update(configuration.id, configuration)
    }

  }

  def create(configuration: Configuration): Configuration = {

    val newEntity = (
      SchemaEntity.newBuilder
        += (Schema.configKey -> configuration.configKey)
        +?= (Schema.configValue -> configuration.configValue)
        +?= (Schema.notes -> configuration.notes)
      ) withId DId(Partition.USER)


    val id: Long = DB.transactAndWait(Seq(newEntity), newEntity.id)
    Configuration.get(id)

  }

  def update(implicit id: Long, configuration: Configuration): Configuration = {

    val o = Configuration.get(id)

    val facts: TraversableOnce[TxData] = Seq(
      DB.factOrNone(o.configKey, configuration.configKey, Schema.configKey -> configuration.configKey),
      DB.factOrNone(o.configValue, configuration.configValue, Schema.configValue -> configuration.configValue.getOrElse("")),
      DB.factOrNone(o.notes, configuration.notes, Schema.notes -> configuration.notes.getOrElse(""))
    ).flatten

    DB.transactAndWait(facts)
    Configuration.get(configuration.id)

  }


  def exists(configKey: String, idOpt: Option[Long]): Boolean = {
    Configuration.find(LookupRef(Schema.configKey, configKey), Datomic.database()) match {
      case Some(configuration) =>
        idOpt.map { id =>
          configuration.id != id
        }.getOrElse(true)
      case None => false
    }

  }

  val queryAll = Query(
    """
    [
      :find ?a
      :where
        [?a :configuration/configkey]
    ]
    """)


  def findByKey(configKey: String): Option[Configuration] = {
    Configuration.find(LookupRef(Schema.configKey, configKey), Datomic.database())
  }

  def findValueByKey(configKey: String): Option[String] = {
    findByKey(configKey).map {
      _.configValue
    }.getOrElse(None)
  }


  def list(pageFilter: PageFilter): Page[Configuration] = {
    implicit val db = Datomic.database
    Configuration.page(Datomic.q(queryAll, Datomic.database), pageFilter)
  }

}
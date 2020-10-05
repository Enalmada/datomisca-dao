package models

import datomisca.DatomicMapping._
import datomisca._
import datomisca.gen.{TypedQuery0, TypedQuery4}
import datomiscadao.Sort.SortBy
import datomiscadao.{DB, IdEntity, PageFilter}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Queries._


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
    val configKey: Attribute[String, Cardinality.one.type] = Attribute(ns.configuration / "configkey", SchemaType.string, Cardinality.one).withUnique(Unique.identity).withDoc("Config primary key")
    val configValue: Attribute[String, Cardinality.one.type] = Attribute(ns.configuration / "configvalue", SchemaType.string, Cardinality.one).withDoc("Configuration value")
    val notes: Attribute[String, Cardinality.one.type] = Attribute(ns.configuration / "notes", SchemaType.string, Cardinality.one).withDoc("notes about the config")

    val schema = Vector(
      configKey, configValue, notes
    )

  }

  /*_*/
  implicit val reader: EntityReader[Configuration] = (
    ID.read[Long] and
      Schema.configKey.read[String] and
      Schema.configValue.readOpt[String] and
      Schema.notes.readOpt[String]
    ) (Configuration.apply _)
  /*_*/

  implicit val writer: PartialAddEntityWriter[Configuration] = (
    ID.write[Long] and
      Schema.configKey.write[String] and
      Schema.configValue.writeOpt[String] and
      Schema.notes.writeOpt[String]
    ) (unlift(Configuration.unapply))

  def delete(id: Long)(implicit conn: Connection): Future[TxReport] = Configuration.retractEntity(id)

  def save(configuration: Configuration)(implicit conn: Connection): Future[Configuration] = {

    if (configuration.id == -1L) {
      create(configuration)
    } else {
      update(configuration.id, configuration)
    }

  }

  def create(configuration: Configuration)(implicit conn: Connection): Future[Configuration] = {

    val newEntity = (
      SchemaEntity.newBuilder
        += (Schema.configKey -> configuration.configKey)
        +?= (Schema.configValue -> configuration.configValue)
        +?= (Schema.notes -> configuration.notes)
      ) withId DId(Partition.USER)


    DB.transact(Vector(newEntity), newEntity.id).map(Configuration.get(_))

  }

  def update(id: Long, configuration: Configuration)(implicit conn: Connection): Future[Configuration] = {
    implicit val primaryId: Long = id
    val o = Configuration.get(id)

    val facts: IterableOnce[TxData] = Vector(
      DB.factOrNone(o.configKey, configuration.configKey, Schema.configKey -> configuration.configKey),
      DB.factOrNone(o.configValue, configuration.configValue, Schema.configValue -> configuration.configValue.getOrElse("")),
      DB.factOrNone(o.notes, configuration.notes, Schema.notes -> configuration.notes.getOrElse(""))
    ).flatten

    if (facts.iterator.nonEmpty) {
      Datomic.transact(facts).map(_ => Configuration.get(id))
    } else {
      Future.successful(Configuration.get(id))
    }

  }


  def exists(configKey: String, idOpt: Option[Long])(implicit conn: Connection): Boolean = {
    Configuration.find(LookupRef(Schema.configKey, configKey)) match {
      case Some(configuration) =>
        !idOpt.contains(configuration.id)
      case None => false
    }

  }

  val queryAll: TypedQuery0[Any] = /*_*/ query"""
    [
      :find ?a
      :where
        [?a :configuration/configkey]
    ]
    """ /*_*/


  def findByKey(configKey: String)(implicit conn: Connection): Option[Configuration] = {
    Configuration.find(LookupRef(Schema.configKey, configKey))
  }

  def findValueByKey(configKey: String)(implicit conn: Connection): Option[String] = findByKey(configKey).flatMap(_.configValue)

  val listQuery: TypedQuery4[_, _, _, _, (Any, Any)] = /*_*/ query"""
    [
      :find ?e ?sortValue
      :in $$ ?sortBy ?key %
      :where
        (ruleKey ?e ?key)
        [(get-else $$ ?e ?sortBy "") ?sortValue]
    ]
    """ /*_*/

  val ruleKey = "[(ruleKey ?e ?key) [?e :configuration/configkey ?originalKey] [(.toLowerCase ^String ?originalKey) ?lowercaseKey] [(= ?lowercaseKey ?key)] ]"

  def dummyRule(ruleName: String) = s"[($ruleName ?e ?x) [?e :configuration/configkey _] ]"

  def list(keyOpt: Option[String], sortBy: SortBy, pageFilter: PageFilter)(implicit conn: Connection): datomiscadao.Page[Configuration] = {
    implicit val db: Database = Datomic.database()

    val ruleKeyFinal: String = keyOpt match {
      case Some(_) => ruleKey
      case None => dummyRule("ruleKey")
    }
    val rules = "[" + ruleKeyFinal + "]"

    Configuration.pageWithSort(Datomic.q(listQuery, Datomic.database(), ":configuration/" + sortBy.field, keyOpt.getOrElse("").toLowerCase, rules), pageFilter, sortBy.order)
  }

}

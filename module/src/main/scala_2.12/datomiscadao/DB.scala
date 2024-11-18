package datomiscadao

import java.util.{Date, UUID}
import datomisca._
import datomisca.gen.{TypedQuery0, TypedQuery2, TypedQuery3}
import datomiscadao.Sort.{Asc, Desc, SortOrder}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.collection.parallel.ParIterable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
trait IdEntity {

  def id: Long

}

/**
  * This trait is intended to be mixed into companion objects to make it easy to do basic CRUD functionality.
  */
trait DB[T] {

  implicit val reader: EntityReader[T]
  implicit val writer: PartialAddEntityWriter[T]

  def createEntity(facts: TraversableOnce[TxData], resolveId: DId)(implicit conn: Connection, ec: ExecutionContext): Future[T] = {
    for {
      tx <- Datomic.transact(facts)
    } yield get(tx.resolve(resolveId))
  }

  def updateEntity(facts: TraversableOnce[TxData], id: Long)(implicit conn: Connection, ec: ExecutionContext): Future[T] = {
    for {
      tx <- Datomic.transact(facts)
    } yield get(id)
  }

  /* Some experimenting on how to get errors out of models */
  // Datomic.q(queryAll, Datomic.database) => execute1
  def execute0(myQuery: AbstractQuery)(implicit conn: Connection) = {
    Datomic.q(myQuery.asInstanceOf[TypedQuery0[Any]], Datomic.database)
  }

  def execute1(myQuery: AbstractQuery, param: String)(implicit conn: Connection) = {
    Datomic.q(myQuery.asInstanceOf[TypedQuery2[AnyRef, AnyRef, Any]], Datomic.database, param)
  }

  def execute1(myQuery: AbstractQuery, param: Long)(implicit conn: Connection) = {
    Datomic.q(myQuery.asInstanceOf[TypedQuery2[AnyRef, AnyRef, Any]], Datomic.database, param)
  }


  private val findByGenericQuery: TypedQuery3[_, _, _, Any] = /*_*/ Query("[:find ?e :in $ ?attr ?childId :where [?e ?attr ?childId] ]") /*_*/

  def refOptionByChildId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], childId: Long)(implicit conn: Connection): Option[T] = {
    DB.headOption(Datomic.q(findByGenericQuery, Datomic.database, attribute, childId), Datomic.database())
  }

  def refListByChildId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], childId: Long)(implicit conn: Connection): Vector[T] = {
    DB.vector(Datomic.q(findByGenericQuery, Datomic.database, attribute, childId), Datomic.database())
  }

  def refRawByChildId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], childId: Long)(implicit conn: Connection): Iterable[Any] = {
    Datomic.q(findByGenericQuery, Datomic.database, attribute, childId)
  }

  private val findByParentIdQuery: TypedQuery3[_, _, _, Any] = /*_*/ Query("[:find ?e :in $ ?attr ?parentId :where [?parentId ?attr ?e] ]") /*_*/

  def refListByParentId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], parentId: Long)(implicit conn: Connection): Vector[T] = {
    DB.vector(Datomic.q(findByParentIdQuery, Datomic.database, attribute, parentId), Datomic.database())
  }

  def refOptionByParentId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], parentId: Long)(implicit conn: Connection): Option[T] = {
    DB.headOption(Datomic.q(findByParentIdQuery, Datomic.database, attribute, parentId), Datomic.database())
  }

  def refRawByParentId(attribute: Attribute[DatomicRef.type, _ <: Cardinality], parentId: Long)(implicit conn: Connection): Iterable[Any] = {
    Datomic.q(findByParentIdQuery, Datomic.database, attribute, parentId)
  }

  /**
    * Gets an entity by ID, where the ID can either be a [[Long]] or a [[LookupRef]]. This method will throw an
    * [[UnresolvedLookupRefException]] if the ID does not map to an existing entity. this should never be the case for a
    * [[Long]] ID, but can potentially be possible when using a [[LookupRef]]. If you are not certain that a  [[LookupRef]]
    * points to an existing entity, call `find` instead
    *
    * @param id     the ID of the entity you with to select
    * @param db     the database to look in
    * @param reader the reader that converts the entity into the proper case class
    * @param idConv the converter that converts the OD from type `I` to the [[Long]] that it eventually needs to be
    * @tparam I the type of the ID, can either be a [[Long]] or a [[LookupRef]]
    * @return the entity, or an [[UnresolvedLookupRefException]] if no entity can be found
    */
  def get[I](id: I, db: Database)(implicit reader: EntityReader[T], idConv: AsPermanentEntityId[I]): T = {
    val entity = db.entity(id)
    DatomicMapping.fromEntity[T](entity)
  }

  def get[I](id: I)(implicit conn: Connection, reader: EntityReader[T], idConv: AsPermanentEntityId[I]): T = {
    val entity = Datomic.database.entity(id)
    DatomicMapping.fromEntity[T](entity)
  }


  def find[I](id: I, db: Database)(implicit reader: EntityReader[T], idConv: AsPermanentEntityId[I]): Option[T] = {
    val entity = db.entity(id)
    try {
      Some(DatomicMapping.fromEntity[T](entity))
    } catch {
      case e: Exception => None
    }
  }

  def find[I](id: I)(implicit conn: Connection, reader: EntityReader[T], idConv: AsPermanentEntityId[I]): Option[T] = {
    val entity = Datomic.database().entity(id)
    try {
      Some(DatomicMapping.fromEntity[T](entity))
    } catch {
      case e: Exception => None
    }
  }


  /**
    * Similar to `get(id: Long)` except this method will not throw an exception of the entity with the specified ID
    * exists, instead, it simply returns a `None`. This is useful for when you are not sure whether or not an entity has
    * been retracted.
    *
    * @param ref    the [[LookupRef]] for the entity you wish to find
    * @param db     the database to search in
    * @param reader the reader which converts the [[Entity]] to our native entity type `T`
    * @return `Some[T]` if the entity exists, `None` otherwise
    */
  def find(ref: LookupRef, db: Database)(implicit reader: EntityReader[T]): Option[T] = {
    ref.entity(db).map(ent => DatomicMapping.fromEntity[T](ent))
  }

  def find(ref: LookupRef)(implicit conn: Connection, reader: EntityReader[T]): Option[T] = {
    ref.entity(Datomic.database()).map(ent => DatomicMapping.fromEntity[T](ent))
  }


  /**
    * Similar to `get(id: Long)` all excpetions are caught and wrapped in a [[Try]]. This is useful in circumstances
    * where perhaps the entity in question was not fully entered into the DB, so while something is there with that ID,
    * there isn't enough to fully inflate the case class.
    *
    * @param id     the ID of the entity that you want to select
    * @param db     the database to search in
    * @param reader the reader which converts the [[Entity]] to our native entity type `T`
    * @param idConv the converter that converts the OD from type `I` to the [[Long]] that it eventually needs to be
    * @return `Some[T]` if the entity exists, `None` otherwise
    */
  def getAsTry[I](id: I, db: Database)(implicit reader: EntityReader[T], idConv: AsPermanentEntityId[I]): Try[T] = Try {
    get(id, db)
  }

  def getAsTry[I](id: I)(implicit conn: Connection, reader: EntityReader[T], idConv: AsPermanentEntityId[I]): Try[T] = Try {
    get(id, Datomic.database())
  }


  /**
    * Gets an entity in JSON format. This is done directly on the Datomic map, so calling this method is an optimization
    * that saves on having to serialize and deserialize from a case class.
    *
    * @param id    the ID of the entity
    * @param depth the depth to recursively expand. After you reach this depth, you will only get entity IDs instead of
    *              the fully expanded entities.
    * @param db    the database to use
    * @tparam I the type of the ID, can either be a [[Long]] or a [[LookupRef]]
    * @return the JSON
    */
  def getAsJson[I](id: I, db: Database, depth: Int = 10)(implicit idConv: AsPermanentEntityId[I]): JsValue = {

    def writesDatomicDataToDepth(depth: Int): Writes[Any] = {
      require(depth >= 0)
      new Writes[Any] {
        override def writes(a: Any): JsValue = a match {
          case s: String => JsString(s)
          case b: Boolean => JsBoolean(b)
          case l: Long => JsNumber(l)
          //case f: Float          => JsNumber(f)
          case d: Double => JsNumber(d)
          case bi: BigInt => JsNumber(BigDecimal(bi))
          case bd: BigDecimal => JsNumber(bd)
          case d: java.util.Date => Writes.defaultDateWrites.writes(d)
          case u: java.util.UUID => JsString(u.toString)
          case u: java.net.URI => JsString(u.toString)
          case k: Keyword => JsString(k.toString.substring(k.toString.lastIndexOf('/') + 1))
          case e: Entity =>
            if (depth == 0)
              JsNumber(e.id)
            else
              writesEntityToDepth(depth).writes(e)
          case i: Iterable[_] =>
            val builder = Seq.newBuilder[JsValue]
            for (a <- i) {
              builder += writesDatomicDataToDepth(depth).writes(a)
            }
            JsArray(builder.result())
          case _ => throw new RuntimeException(s"Unexpected Datomic data of ${a.getClass}")
        }
      }
    }

    def writesEntityToDepth(depth: Int): Writes[Entity] = {

      new Writes[Entity] {
        override def writes(entity: Entity): JsValue = {

          def transformKeys(map: Map[String, Any]): Map[String, Any] = {

            for {

              (key, value) <- map
            } yield {

              val trimmedKey: String = key.toString.substring(key.toString.lastIndexOf('/') + 1)
              val trimmedValue = value match {

                // Need to add the @unchecked annotation to get rid of the following type erasure warning that we don't
                // care about: non-variable type argument String in type pattern Map[String,Any] is unchecked since it is eliminated by erasure
                case valueMap: Map[String, Any]@unchecked => transformKeys(valueMap)
                case _ => value
              }
              (trimmedKey, trimmedValue)
            }
          }

          Writes.genericMapWrites(writesDatomicDataToDepth(depth - 1)).writes(transformKeys(entity.toMap))
        }
      }
    }
    val entity = db.entity(id)

    Json.toJson(entity)(writesEntityToDepth(depth))
  }


  /**
    * This is the Datomic equivalent of a delete, except in Datomic, nothing ever gets deleted. You can only insert a
    * retraction, that flags the specified entity as ignored.
    *
    * @param id the ID of the entity to retract
    * @param ex the [[ExecutionContext]]
    * @tparam I the type of the ID, can either be a [[Long]] or a [[LookupRef]]
    * @return the transaction record specifying whether or not the transaction succeeded or failed
    */
  def retractEntity[I](id: I)(implicit conn: Connection, ex: ExecutionContext, idConv: AsPermanentEntityId[I]): Future[TxReport] = DB.retractEntity(id)


  /**
    * Returns the list of entities chosen by the query tupled with its ID.
    *
    * @param query  the Datomic Query
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the reader for the entity
    * @return
    */
  protected def list(query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): List[T] = DB.list[T](query, db)

  protected def list(query: Iterable[Any])(implicit conn: Connection, reader: EntityReader[T]): List[T] = DB.list[T](query, Datomic.database())

  protected def listWithId(query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): List[(Long, T)] = DB.listWithId[T](query, db)

  protected def listWithId(query: Iterable[Any])(implicit conn: Connection, reader: EntityReader[T]): List[(Long, T)] = DB.listWithId[T](query, Datomic.database())

  /**
    * Returns a single entity (the first entity if there are many that match the query) as an option or `None` if there
    * are no results matching the query
    *
    * @param query  the executed query
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the reader to convert to the specified entity type
    * @return `Some(entity)` of there is at lease one result where `entity` is the first entity found, or `None` if there
    *         are no results
    */
  protected def headOption(query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Option[T] = DB.headOption[T](query, db)

  protected def headOption(query: Iterable[Any])(implicit conn: Connection, reader: EntityReader[T]): Option[T] = DB.headOption[T](query, Datomic.database())

  protected def headOptionWithId(query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Option[(Long, T)] = DB.headOptionWithId[T](query, db)

  protected def headOptionWithId(query: Iterable[Any])(implicit conn: Connection, reader: EntityReader[T]): Option[(Long, T)] = DB.headOptionWithId[T](query, Datomic.database())

  /**
    * Runs a query and pulls a single page worth of values out.
    *
    * @param query  the query
    * @param filter the filter
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the entity reader
    * @return the [[Page]]
    */
  protected def page(query: Iterable[Any], filter: PageFilter)(implicit db: Database, reader: EntityReader[T]): Page[T] = {
    DB.page[T](query, filter)
  }

  protected def pageWithSort(query: Iterable[(Any, Any)], filter: PageFilter, sort: SortOrder = Asc)(implicit db: Database, reader: EntityReader[T]): Page[T] = {
    DB.pageWithSort[T](query, filter, sort)
  }

}


/**
  * Object abstraction with for interacting with Datomic. Note that this object, is not intended to try and abstract/hide
  * away the fact that we are using Datomic, but rather intends to make it easier to use Datomic the way that we want to
  * use it in this app.
  */
object DB {

  val dbLogger: Logger = LoggerFactory.getLogger("DB")

  /**
    * Generate a unique UUID to be used as a lookup-ref. Abstracting away the fact that we are using Datomic to generate
    * UUIDs. We are using Datomic for reasons specified here: http://docs.datomic.com/identity.html
    *
    * @return the [[UUID]]
    */
  def uuid: UUID = Datomic.squuid()


  /**
    * Little bit of boilerplate to get around the fact that Scala type parameter inference is either all or nothing. See
    * [[http://stackoverflow.com/questions/10726222/simulate-partial-type-parameter-inference-with-implicits/10734268#10734268]]
    * for an explaination of why this is necessary and the sample from which this code is derived from.
    *
    * @tparam T the return type
    */
  class EntityGetter[T] {

    /**
      * Gets an entity by ID, where the ID can either be a [[Long]] or a [[LookupRef]]. This method will throw an
      * [[UnresolvedLookupRefException]] if the ID does not map to an existing entity. this should never be the case for a
      * [[Long]] ID, but can potentially be possible when using a [[LookupRef]]. If you are not certain that a  [[LookupRef]]
      * points to an existing entity, call `find` instead
      *
      * @param id     the ID of the entity you with to select
      * @param db     the database to look in
      * @param reader the reader that converts the entity into the proper case class
      * @param idConv the converter that converts the OD from type `I` to the [[Long]] that it eventually needs to be
      * @tparam I the type of the ID, can either be a [[Long]] or a [[LookupRef]]
      * @return the entity, or an [[UnresolvedLookupRefException]] if no entity can be found
      */
    def apply[I](id: I, db: Database)(implicit reader: EntityReader[T], idConv: AsPermanentEntityId[I]): T = {

      val entity = db.entity(id)
      DatomicMapping.fromEntity[T](entity)
    }
  }

  /**
    * See [[EntityGetter.apply()]].
    *
    * @tparam T the type that you want returned
    * @return the [[EntityGetter]]
    */
  def get[T] = new EntityGetter[T]


  /**
    * Converts an ID to a tuple of the ID and the Entity that the ID represents.
    *
    * @param e      the entity
    * @param db     the database, used to find the ID
    * @param reader the reader, used to convert the entity map to the appropriate case class
    * @tparam T the type to convert to
    * @return a tuple of ID and entity
    */
  private def toIdEntityTuple[T](e: Any, db: Database)(implicit reader: EntityReader[T]): (Long, T) = e match {
    case id: Long =>
      val entity = db.entity(id)
      (id, DatomicMapping.fromEntity(entity)(reader))
  }

  private def toEntity[T](e: Any, db: Database)(implicit reader: EntityReader[T]): T = e match {
    case id: Long =>
      val entity = db.entity(id)
      DatomicMapping.fromEntity(entity)(reader)
  }


  /**
    * Returns the list of entities chosen by the query tupled with its ID.
    *
    * @param query  the Datomic Query
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the reader for the entity
    * @return
    */
  def list[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): List[T] = {
    query.toList.map(toEntity(_, db)(reader))
  }

  def vector[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Vector[T] = {
    query.map(toEntity(_, db)(reader)).toVector
  }

  def vectorPar[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Vector[T] = {
    query.par.map(toEntity(_, db)(reader)).toVector
  }

  def raw[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Iterable[T] = {
    query.map(toEntity(_, db)(reader))
  }

  def rawPar[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): ParIterable[T] = {
    query.par.map(toEntity(_, db)(reader))
  }

  def listWithId[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): List[(Long, T)] = {
    query.toList.map(toIdEntityTuple(_, db)(reader))
  }


  /**
    * Returns a single entity (the first entity if there are many that match the query) as an option or `None` if there
    * are no results matching the query
    *
    * @param query  the executed query
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the reader to convert to the specified entity type
    * @return `Some(entity)` of there is at lease one result where `entity` is the first entity found, or `None` if there
    *         are no results
    */
  def headOption[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Option[T] = {
    query.headOption.map(toEntity(_, db)(reader))
  }

  def headOptionWithId[T](query: Iterable[Any], db: Database)(implicit reader: EntityReader[T]): Option[(Long, T)] = {
    query.headOption.map(toIdEntityTuple(_, db)(reader))
  }


  /**
    * Runs a query and pulls a single page worth of values out.
    *
    * @param query  the query
    * @param filter the filter
    * @param db     the database - Required to convert the entity ID to the full entity. For consistency, This should be the
    *               same database that you used to execute the query.
    * @param reader the entity reader
    * @return the [[Page]]
    */
  protected def page[T](query: Iterable[Any], filter: PageFilter)(implicit db: Database, reader: EntityReader[T]): Page[T] = {

    val from = filter.offset
    val until = filter.offset + filter.pageSize + 1
    val hasPrev = from > 0
    val hasNext = query.size >= until
    val items: List[T] = query.toList.slice(from, until - 1).map(toEntity(_, db)(reader))

    Page(items, filter, hasPrev, hasNext, query.size)
  }

  protected def pageWithId[T](query: Iterable[Any], filter: PageFilter)(implicit db: Database, reader: EntityReader[T]): Page[(Long, T)] = {

    val from = filter.offset
    val until = filter.offset + filter.pageSize + 1
    val hasPrev = from > 0
    val hasNext = query.size >= until
    val items: List[(Long, T)] = query.toList.slice(from, until).map(toIdEntityTuple(_, db)(reader))

    Page(items, filter, hasPrev, hasNext, query.size)
  }

  def listToPage[T](rawList: List[Any], filter: PageFilter)(implicit db: Database, reader: EntityReader[T]): Page[T] = {
    val from = filter.offset
    val until = filter.offset + filter.pageSize + 1
    val hasPrev = from > 0
    val hasNext = rawList.size >= until
    val items = rawList.slice(from, until - 1).map(toEntity(_, db)(reader))

    Page(items, filter, hasPrev, hasNext, rawList.size)
  }

  def compareFunction(leftE: (Any, Any), rightE: (Any, Any)): Boolean = {
    (leftE._2, rightE._2) match {
      case (a: Int, b: Int) => a < b
      case (a: Double, b: Double) => a < b
      case (a: String, b: String) => a < b
      case (a: Date, b: Date) => a.before(b)
      case (a: Long, b: Long) => a < b
      case (a: Any, b: Any) => a.toString < b.toString
    }
  }


  protected def pageWithSort[T](query: Iterable[(Any, Any)], filter: PageFilter, sort: SortOrder = Asc)(implicit db: Database, reader: EntityReader[T]): Page[T] = {
    val from = filter.offset
    val until = filter.offset + filter.pageSize + 1
    val hasPrev = from > 0
    val hasNext = query.size >= until

    val result: List[(Any, Any)] = query.toList.sortWith(compareFunction)

    val sorted = sort match {
      case Asc => result
      case Desc => result.reverse
    }

    val sliced = sorted.slice(from, until - 1).map(x => toEntity(x._1, db)(reader))
    Page(sliced, filter, hasPrev, hasNext, query.size)
  }


  /**
    * Retrieves the value of a particular field for a particular entity. In other words, in the EAVT structure of
    * Datomic this method allows you to specify the id (E), attribute (A), and what you will get in return is the most
    * recent (T) value (V).
    *
    * @param id        the ID of the entity that you want to query
    * @param attribute the schema attribute of the field that you want to get
    * @param db        the database
    * @param attrC     the converter
    * @tparam DD   the Datomic Data type
    * @tparam Card the cardinality
    * @tparam T    the return type (must correspond to the Datomic data type)
    * @return the field value
    */
  def value[I, DD <: AnyRef, Card <: Cardinality, T](id: I,
                                                     attribute: Attribute[DD, Card],
                                                     db: Database)
                                                    (implicit attrC: Attribute2EntityReaderInj[DD, Card, T],
                                                     idConv: AsPermanentEntityId[I]): T = {

    valueOpt(id, attribute, db).get
  }


  /**
    * Retrieves the optional value of a particular attribute for a particular entity, from a particular database value.
    * In other words, in the EAVT structure of Datomic this method allows you to specify the id (E), attribute (A), and
    * what you will get in return is the most recent (T) value (V), for the supplied database.
    *
    * @param id        the ID of the entity that you want to query
    * @param attribute the schema attribute of the field that you want to get
    * @param database  the database
    * @param attrC     the converter
    * @tparam DD   the Datomic Data type
    * @tparam Card the cardinality
    * @tparam T    the return type (must correspond to the Datomic data type)
    * @return the field value
    */
  def valueOpt[I, DD <: AnyRef, Card <: Cardinality, T](id: I,
                                                        attribute: Attribute[DD, Card],
                                                        database: Database)
                                                       (implicit attrC: Attribute2EntityReaderInj[DD, Card, T],
                                                        idConv: AsPermanentEntityId[I]): Option[T] = {

    val entity: Entity = database.entity(id)
    val value: Option[T] = entity.get(attribute)

    value
  }


  /**
    * Retract an individual attribute for a given entity. Before issuing the retraction, it first checks that there is a
    * value there to retract. If there is not, this method is simply a NOOP.
    *
    * @param id     the identified for the entity. Can either be an ID, or a lookup ref
    * @param attr   the attribute to retract
    * @param conn   the database connection
    * @param ec     the [[ExecutionContext]]
    * @param attrC  the [[Attribute2EntityReaderInj]]
    * @param ev2    the [[Attribute2FactWriter]]
    * @param idConv the ID converter
    * @tparam I    the type of the ID
    * @tparam DD   the Datomic Data type
    * @tparam Card the cardinality of the attribute
    * @tparam T    the type of the value that you are retracting
    * @return a successful future if all goes well
    */
  def retract[I, DD <: AnyRef, Card <: Cardinality, T](id: I, attr: Attribute[DD, Card])
                                                      (implicit conn: Connection,
                                                       ec: ExecutionContext,
                                                       attrC: Attribute2EntityReaderInj[DD, Card, T],
                                                       ev2: Attribute2FactWriter[DD, Card, T],
                                                       idConv: AsPermanentEntityId[I]): Future[Unit] = {

    valueOpt(id, attr, conn.database()) match {

      case Some(value) => Datomic.transact(SchemaFact.retract(id)(attr -> value)).map(r => Unit)
      case None => Future.successful(Unit)
    }
  }


  /**
    * This is the Datomic equivalent of a delete, except in Datomic, nothing ever gets deleted. You can only insert a
    * retraction, that flags the specified entity as ignored.
    *
    * @param id the ID of the entity to retract
    * @param ex the [[ExecutionContext]]
    * @tparam I the type of the ID, can either be a [[Long]] or a [[LookupRef]]
    * @return the transaction record specifying whether or not the transaction succeeded or failed
    */
  def retractEntity[I](id: I)(implicit conn: Connection, ex: ExecutionContext, idConv: AsPermanentEntityId[I]): Future[TxReport] = {

    Datomic.transact(Entity.retract(id))
  }

  /**
    * Asserts a new fact in the database
    *
    * @param id      the entity ID
    * @param attrVal the assertion of the form `Schema.thing -> value`
    * @param conn    the database connection
    * @param ec      the execution context
    * @param ev2     the [[Attribute2FactWriter]]
    * @tparam DD   the Datomic Data type
    * @tparam Card the cardinality of the attribute
    * @tparam A    the type of the value that you are adding
    * @return the database result
    */
  def add[DD <: AnyRef, Card <: Cardinality, A, I](id: I)(attrVal: (Attribute[DD, Card], A))
                                                  (implicit conn: Connection,
                                                   ec: ExecutionContext,
                                                   ev1: AsEntityId[I],
                                                   ev2: Attribute2FactWriter[DD, Card, A]): Future[TxReport] = {

    Datomic.transact(SchemaFact.add(id)(attrVal))
  }

  def factOrNone[T, DD <: AnyRef, Card <: Cardinality, A](original: Any, changed: Any, attrVal: (Attribute[DD, Card], A))
                                                         (implicit ev2: Attribute2FactWriter[DD, Card, A], id: Long) = {

    // Need to compare content equality and None = None
    def optionCompare(a: Option[Any], b: Option[Any]) = (a, b) match {
      case (Some(x: Any), None) => false
      case (None, Some(x: Any)) => false
      case (None, None) => true
      case (Some(x: Long), Some(y: Long)) => x == y
      case (Some(x: Int), Some(y: Int)) => x == y
      case (Some(x: String), Some(y: String)) => x == y
      case (Some(x: Date), Some(y: Date)) => x.compareTo(y) == 0
      case (Some(x: Float), Some(y: Float)) => x.compareTo(y) == 0
      case _ => false
    }

    (original, changed) match {
      case (b: Option[Any], a: Option[Any]) => {
        if (b.nonEmpty && a.isEmpty) Some(SchemaFact.retract(id)(attrVal._1 -> b.get.asInstanceOf[A]))
        else if (optionCompare(b, a)) None else Some(SchemaFact.add(DId(id))(attrVal))
      }
      case (b: Any, a: Any) => if (b == a) None else Some(SchemaFact.add(DId(id))(attrVal))
      case _ => None
    }

  }

  // Used in schema upgrades
  def hasAttribute(attributeIdent: Keyword)(implicit db: Database, conn: Connection): Boolean =
    Datomic.q(Query("[:find ?e :in $ ?attribute :where [?e :db/ident ?attribute]]"), db, attributeIdent).toSeq.nonEmpty


  def transactAndWait(facts: TxData*)(implicit conn: Connection, ec: ExecutionContext): Unit = {
    if (facts.nonEmpty) {
      Await.result(
        for {
          _ <- Datomic.transact(facts)
        } yield (),
        Duration("3 seconds")
      )
    }
  }

  def transactAndWait(facts: TraversableOnce[TxData])(implicit conn: Connection, ec: ExecutionContext): Unit = {
    if (facts.nonEmpty) {
      Await.result(
        for {
          _ <- Datomic.transact(facts)
        } yield (),
        Duration("3 seconds")
      )
    }
  }

  def transact(facts: TraversableOnce[TxData])(implicit conn: Connection, ec: ExecutionContext): Unit = {
    if (facts.nonEmpty) {
      for {
        _ <- Datomic.transact(facts)
      } yield ()
    }
  }

  def transact(facts: TraversableOnce[TxData], id: Long)(implicit conn: Connection, ec: ExecutionContext): Future[Long] = {
    if (facts.nonEmpty) {
      Datomic.transact(facts).map(_ => id)
    } else {
      Future.successful(id)
    }
  }

  // TODO: this should be async
  def transactAndWait(facts: TraversableOnce[TxData], resolveId: DId)(implicit conn: Connection, ec: ExecutionContext): Long = {
    Await.result(
      for {
        tx <- Datomic.transact(facts)
      } yield tx.resolve(resolveId),
      Duration("3 seconds")
    )
  }

  def transact(facts: TraversableOnce[TxData], resolveId: DId)(implicit conn: Connection, ec: ExecutionContext): Future[Long] = {
    for {
      tx <- Datomic.transact(facts)
    } yield tx.resolve(resolveId)
  }


  def loadSchema(combinedSchema: Seq[TxData with KeywordIdentified], check: Boolean = true)(implicit conn: Connection, ec: ExecutionContext) = {
    implicit val db = Datomic.database

    val filteredSchema = if (check) combinedSchema.filterNot(s => DB.hasAttribute(s.ident)) else combinedSchema

    if (filteredSchema.nonEmpty) {
      val fut = Datomic.transact(filteredSchema) map { tx =>
        dbLogger.info(s"Loaded Schema: $filteredSchema")
      }

      Await.result(fut, Duration("10 seconds"))
    }
  }

}


object Sort {


  sealed trait SortOrder

  case object Asc extends SortOrder

  case object Desc extends SortOrder

  trait SortByField

  case class SortBy(field: String, order: SortOrder = Asc)

  def sortByOrder(currentSortBy: String, currentOrder: String, newSortByOpt: Option[String]): (String, String) = {

    val sortBy = newSortByOpt.getOrElse(currentSortBy)
    val order = newSortByOpt.map { newSortBy =>
      if (currentSortBy.equals(sortBy)) {
        if (currentOrder == "asc") {
          "desc"
        } else {
          "asc"
        }
      } else {
        "desc"
      }
    }.getOrElse(currentOrder)

    (sortBy, order)

  }


}


/**
  * Pagination support.
  */
case class PageFilter(page: Int = 0, pageSize: Int = 50) {
  def offset = page * pageSize
}

case class Page[+T](items: Seq[T], pageFilter: PageFilter, hasPrev: Boolean, hasNext: Boolean, total: Int = 0) {
  lazy val prev = Option(pageFilter.page - 1).filter(_ >= 0)
  lazy val current = pageFilter.page
  lazy val next = Option(pageFilter.page + 1).filter(_ => hasNext)
  lazy val from = pageFilter.offset + 1
  lazy val to = pageFilter.offset + items.size
}

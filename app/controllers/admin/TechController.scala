package controllers.admin

import java.io.{PrintWriter, StringWriter}
import javax.inject.Inject

import controllers.{BaseController, WebJarAssets}
import datomic.Peer
import datomisca.{Datomic, Query}
import models.User.Role.Administrator
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.MessagesApi
import util.DatomicService._

import scala.collection.JavaConverters._

class TechController @Inject()(implicit val messagesApi: MessagesApi, config: play.api.Configuration, env: play.api.Environment, webJarAssets: WebJarAssets) extends BaseController {

  def getStackTraceAsString(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  def query(query: String) = StackAction(AuthorityKey -> Administrator) { implicit request =>
    val form: Form[String] = Form("query" -> text).fill(query)
    val results = if (query.nonEmpty) {

      val queryAll = Query(
        """
        [
          :find ?name
          :where
            [?a :activity/name ?name]
        ]
        """)


      try {


        val queryFormed = if (query.trim.startsWith("[")) query else "[ " + query + " ]"

        val ids = Peer.q(queryFormed, Datomic.database.underlying).asScala.map(x => x.get(0).asInstanceOf[Long])
        val rows: Seq[(Long, Map[String, Any])] = ids.map { id => (id, Datomic.database.entity(id).toMap) }.toSeq

        val keys: Seq[String] = rows.flatMap { r =>
          r._2.keySet
        }.distinct


        //val headers: Seq[String] = rows.headOption.map { x =>  x._2.keys.toSeq }.getOrElse(Seq())
        val myRows: Seq[(Long, Seq[String])] = rows.map { x => (x._1, keys.map(k => x._2.getOrElse(k, "").toString)) }

        (keys, myRows, "")

      } catch {
        case e: Throwable => {
          getStackTraceAsString(e)
          (Seq[String](), Seq[(Long, Seq[String])](), getStackTraceAsString(e))
        }
      }


    } else (Seq[String](), Seq[(Long, Seq[String])](), "")

    Ok(views.html.admin.tech.datomic(form, results))

  }

}
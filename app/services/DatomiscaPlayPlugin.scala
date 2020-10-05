/*
 * Copyright 2012-2014 Pellucid and Zenexity
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import com.typesafe.config.ConfigObject
import datomisca.{Connection, Datomic}
import play.api.{Configuration, Logging}

import scala.util.Try
import scala.jdk.CollectionConverters._

class DatomiscaPlayPlugin(configuration: Configuration) extends Logging {

  val conf: Configuration = {
    val conf0 = configuration
    conf0.getOptional[Configuration]("datomisca.uri") match {
      case None => conf0
      // case Some(conf1) => conf0 ++ conf1 // conf1 withFallback conf0
      case Some(conf1) => conf1.withFallback(conf0)
    }
  }

  /** Retrieves URI using ID from configuration.
    * It crashes with runtime exception if not found
    */
  def uri(id: String): String =
    conf.getOptional[String](id) getOrElse {
      throw new IllegalArgumentException(s"$id not found")
    }

  /** Retrieves URI from configuration in safe mode.
    *
    * @return Some(uri) if found and None if not found
    */
  def safeUri(id: String): Option[String] = conf.getOptional[String](id)

  /** Creates a Datomic connection (or throws a RuntimeException):
    * - if ID is found in configuration, it retrieves corresponding URI
    * - if ID is not found, it considers ID is an URI
    *
    * @param id the id to search or an URI
    * @return created Connection (or throws RuntimeException)
    */
  def connect(id: String): Connection = Datomic.connect(
    if (id startsWith "datomic:")
      id
    else
      uri(id)
  )

  /** Safely creates a Datomic connection :
    * - if ID is found in configuration, it retrieves corresponding URI
    * - if ID is not found, it considers ID is an URI
    *
    * @param id the id to search or an URI
    * @return a Try[Connection] embedding potential detected exception
    */
  def safeConnect(id: String): Try[Connection] = Try(connect(id))


  def onStart(): Unit = {
    configuration.getOptional[ConfigObject]("datomisca.uri") foreach { obj =>
      obj.asScala.toMap foreach { case (k, v) =>
        if (v.valueType == com.typesafe.config.ConfigValueType.STRING) {
          val uriStr = v.unwrapped.toString
          assert {
            uriStr startsWith "datomic:"
          }
          val uri = new java.net.URI(uriStr drop 8)
          logger.info(
            s"""DatomiscaPlayPlugin found datomisca.uri config with,
               |{
               |  config key:      $k
               |  storage service: ${uri.getScheme}
               |  db URI path:     ${uri.getAuthority}${uri.getPath}
               |}""".stripMargin
          )
        }
      }
    }
  }
}


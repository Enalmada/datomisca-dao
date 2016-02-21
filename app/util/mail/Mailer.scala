package util.mail

import play.api.Play._
import play.api.i18n.Messages
import play.api.libs.mailer.MailerClient
import play.twirl.api.Html
import util.mail.MailService._
import views.html.mails

import scala.language.implicitConversions


object Mailer {

  implicit def html2String(html: Html): String = html.toString()

  implicit val from = current.configuration.getString("mail.from").get

  def welcome(email: String, link: String)(implicit mailerClient: MailerClient, messages: Messages) {

    sendEmailAsync(email)(
      subject = Messages("mail.welcome.subject"),
      bodyHtml = mails.welcome(email, link).toString,
      bodyText = mails.welcomeTxt(email, link).toString
    )

  }

  def forgotPassword(email: String, link: String)(implicit mailerClient: MailerClient, messages: Messages) = {

    sendEmailAsync(email)(
      subject = Messages("mail.forgotpwd.subject"),
      bodyHtml = mails.forgotPassword(email, link).toString,
      bodyText = mails.forgotPasswordTxt(email, link).toString
    )

  }

}
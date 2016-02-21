import play.api.{Application, GlobalSettings, Logger, Play}
import util.DatomicService

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Logger.info("Application started!")

    if (Play.isTest(app)) {
      DatomicService.connString = DatomicService.test
      DatomicService.testStart()
    } else {
      DatomicService.connString = DatomicService.uri
      DatomicService.normalStart(app)
    }

  }

  override def onStop(app: Application) {

    if (Play.isTest(app)) {
      DatomicService.testEnd()
    }

    Logger.info("Application stopped.")

  }


}
package models

import com.secretapp.backend.persist.events.LogEvent
import scala.concurrent.ExecutionContext

case class StatLogs(authCodes: Seq[(String, Int)],
                    sentSms: Seq[(String, Int)],
                    successSigns: Seq[(String, Int)],
                    auths: Seq[(String, Int, Int)])

object StatLogs {
  def stats()(implicit ec: ExecutionContext) = {
    for {
      authCodes <- LogEvent.authCodesStat()
      sentSms <- LogEvent.sentSmsStat()
      successSigns <- LogEvent.successSignsStat()
      auths <- LogEvent.authsStat()
    }
    yield StatLogs(authCodes = authCodes, sentSms = sentSms, successSigns = successSigns, auths = auths)
  }
}
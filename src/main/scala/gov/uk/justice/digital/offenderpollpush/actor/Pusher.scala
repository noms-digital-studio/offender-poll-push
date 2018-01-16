package gov.uk.justice.digital.offenderpollpush.actor

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.google.inject.Inject
import gov.uk.justice.digital.offenderpollpush.data.{PushResult, TargetOffender}
import gov.uk.justice.digital.offenderpollpush.traits.SingleTarget
import scala.concurrent.ExecutionContext.Implicits.global

class Pusher @Inject() (target: SingleTarget) extends Actor with ActorLogging {

  private def poller = context.actorSelection("/user/Poller")

  override def receive: Receive = {

    case targetOffender @ TargetOffender(id, _, cohort) =>

      log.info(s"Pushing Offender ID: $id of Delta Cohort: $cohort")
      target.push(targetOffender).pipeTo(self)


    case pushResult @ PushResult(offender, _, body, _) =>

      (pushResult.result, pushResult.error) match {

        // @TODO: what if connection pool full? test

        case (_, Some(error)) => log.warning(s"Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} PUSH ERROR: ${error.getMessage}")

        case (Some(result), None) => log.info(s"Push for Offender ID: ${offender.id} of Delta Cohort: ${offender.cohort} returned ${result.value} $body")

        case _ => log.warning("PUSH ERROR: No result or error")
      }

      poller ! pushResult
  }
}

//@TODO:
// If fails in memory, when re-load will re-process all outstanding and delete olders, get up to date

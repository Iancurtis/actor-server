package com.secretapp.backend.services.rpc

import com.secretapp.backend.util.HandleFutureOpt.HandleResult
import com.secretapp.backend.data.message.rpc.Error
import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import Scalaz._
import scala.language.implicitConversions

package object auth {
  // TODO: Move me
  case class Result[A](hr: HandleResult[Error, A])

  // TODO: Move me
  object Result {
    implicit def toResult[A](hr: HandleResult[Error, A]): Result[A] = Result(hr)

    implicit def fromResult[A](r: Result[A]): HandleResult[Error, A] = r.hr

    implicit def monad(implicit ec: ExecutionContext): Monad[Result] = new Monad[Result] {
      override def bind[A, B](fa: Result[A])(f: (A) => Result[B]): Result[B] =
        fa.hr flatMap { va =>
          va map { ra =>
            f(ra).hr
          } valueOr { la =>
            Future successful la.left
          }
        }

      override def point[A](a: => A): Result[A] = Future successful a.right
    }
  }
}

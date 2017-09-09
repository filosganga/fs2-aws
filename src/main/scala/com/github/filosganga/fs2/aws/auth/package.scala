package com.github.filosganga.fs2.aws


import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import javax.xml.bind.DatatypeConverter

import cats.effect.Sync
import fs2.hash._
import org.http4s.{Header, Request}
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import org.http4s.headers.Host

import scala.language.higherKinds

package object auth extends LazyLogging {

  private val dateFormatter = DateTimeFormatter.ofPattern("YYYYMMdd'T'HHmmss'Z'")

  def encodeHex(bytes: Array[Byte]): String = DatatypeConverter.printHexBinary(bytes).toLowerCase

  def signedRequest[F[_] : Sync](request: Request[F], key: SigningKey, date: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): F[Request[F]] = {

    request
      .body
      .through(sha256)
      .fold(Vector.empty[Byte])(_ :+ _)
      .map(xs => encodeHex(xs.toArray))
      .map { hashedBody =>

        val reqWithHeaders = request
          .putHeaders(
            Host(request.uri.host.get.value), // TODO Improve it
            Header("x-amz-date", date.format(dateFormatter)),
            Header("x-amz-content-sha256", hashedBody))
          .putHeaders(sessionHeader(key.credentials).toList: _*)

        val canonicalRequest = CanonicalRequest.from(reqWithHeaders)

        logger.debug("Canonical Request" + canonicalRequest.canonicalString)
        val authHeader: Header = authorizationHeader("AWS4-HMAC-SHA256", key, date, canonicalRequest)

        reqWithHeaders.putHeaders(authHeader)
      }
      .runLog
      .map(_.head)
  }

  private[this] def sessionHeader(creds: AwsCredentials): Option[Header] = creds match {
    case _: BasicCredentials => None
    case AwsSessionCredentials(_, _, sessionToken) => Some(Header("X-Amz-Security-Token", sessionToken))
  }

  private[this] def authorizationHeader(algorithm: String,
                                        key: SigningKey,
                                        requestDate: ZonedDateTime,
                                        canonicalRequest: CanonicalRequest
                                       ): Header = Header("Authorization", authorizationString(algorithm, key, requestDate, canonicalRequest))

  private[this] def authorizationString(algorithm: String,
                                        key: SigningKey,
                                        requestDate: ZonedDateTime,
                                        canonicalRequest: CanonicalRequest): String = {
    val sign = key.hexEncodedSignature(stringToSign(algorithm, key, requestDate, canonicalRequest).getBytes())

    s"$algorithm Credential=${key.credentialString}, SignedHeaders=${canonicalRequest.signedHeaders}, Signature=$sign"
  }

  def stringToSign(algorithm: String,
                   signingKey: SigningKey,
                   requestDate: ZonedDateTime,
                   canonicalRequest: CanonicalRequest): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashedRequest = encodeHex(digest.digest(canonicalRequest.canonicalString.getBytes))
    val date = requestDate.format(dateFormatter)
    val scope = signingKey.scope.scopeString
    s"$algorithm\n$date\n$scope\n$hashedRequest"
  }

}
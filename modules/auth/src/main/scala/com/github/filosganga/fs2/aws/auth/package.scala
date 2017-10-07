package com.github.filosganga.fs2.aws

import java.security.MessageDigest
import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import javax.xml.bind.DatatypeConverter

import cats.effect.Sync
import org.http4s.{Header, Request}
import org.http4s.headers.Host
import fs2.hash._
import org.log4s._
import cats.syntax.functor._

package object auth {

  private[this] val logger = getLogger

  private val dateFormatter =
    DateTimeFormatter.ofPattern("YYYYMMdd'T'HHmmss'Z'")

  def encodeHex(bytes: Array[Byte]): String =
    DatatypeConverter.printHexBinary(bytes).toLowerCase

  def signedRequest[F[_]: Sync](request: Request[F],
                                key: SigningKey,
                                date: ZonedDateTime = ZonedDateTime.now(
                                  ZoneOffset.UTC)): F[Request[F]] = {

    request.body
      .through(sha256)
      .fold(Vector.empty[Byte])(_ :+ _)
      .map(xs => encodeHex(xs.toArray))
      .map { hashedBody =>
        val reqWithHeaders = request
          .putHeaders(Host(request.uri.host.get.value), // TODO Improve it
                      Header("x-amz-date", date.format(dateFormatter)),
                      Header("x-amz-content-sha256", hashedBody))
          .putHeaders(sessionHeader(key.credentials).toList: _*)

        val canonicalRequest = CanonicalRequest.from(reqWithHeaders)

        logger.debug("Canonical Request" + canonicalRequest.canonicalString)
        val authHeader: Header =
          authorizationHeader("AWS4-HMAC-SHA256", key, date, canonicalRequest)

        reqWithHeaders.putHeaders(authHeader)
      }
      .runLog
      .map(_.head)
  }

  private[this] def sessionHeader(creds: AwsCredentials): Option[Header] =
    creds match {
      case _: BasicCredentials => None
      case AwsSessionCredentials(_, _, sessionToken) =>
        Some(Header("X-Amz-Security-Token", sessionToken))
    }

  private[this] def authorizationHeader(
      algorithm: String,
      key: SigningKey,
      requestDate: ZonedDateTime,
      canonicalRequest: CanonicalRequest): Header =
    Header("Authorization",
           authorizationString(algorithm, key, requestDate, canonicalRequest))

  private[this] def authorizationString(
      algorithm: String,
      key: SigningKey,
      requestDate: ZonedDateTime,
      canonicalRequest: CanonicalRequest): String = {
    val sign = key.hexEncodedSignature(
      stringToSign(algorithm, key, requestDate, canonicalRequest).getBytes())

    s"$algorithm Credential=${key.credentialString}, SignedHeaders=${canonicalRequest.signedHeaders}, Signature=$sign"
  }

  def stringToSign(algorithm: String,
                   signingKey: SigningKey,
                   requestDate: ZonedDateTime,
                   canonicalRequest: CanonicalRequest): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashedRequest = encodeHex(
      digest.digest(canonicalRequest.canonicalString.getBytes))
    val date = requestDate.format(dateFormatter)
    val scope = signingKey.scope.scopeString
    s"$algorithm\n$date\n$scope\n$hashedRequest"
  }

}

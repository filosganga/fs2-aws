package com.github.filosganga.fs2.aws.s3

import java.time.{LocalDate, ZonedDateTime}

import cats.data.Kleisli
import cats.effect.{Effect, Sync}
import cats.syntax.functor._
import com.github.filosganga.fs2.aws.auth._
import fs2._
import org.http4s.client.Client
import org.http4s.scalaxml._
import org.http4s._

import scala.language.higherKinds
import scala.xml.Elem
import org.http4s.client.blaze.PooledHttp1Client

class S3(credentials: AwsCredentials,
         region: String,
         endpoint: Option[Uri],
         usePathStyleUrl: Boolean,
         scheme: String = "http") {

  case class ObjectsResult(objects: Seq[Object],
                           nextContinuationToken: String,
                           isTruncated: Boolean)

  implicit def bucketsDecoder[F[_]: Effect]: EntityDecoder[F, Seq[Bucket]] =
    for {
      elem <- xml
    } yield {
      (elem \\ "Bucket").map { node =>
        Bucket(BucketName((node \ "Name").text),
               ZonedDateTime.parse((node \ "CreationDate").text))
      }
    }

  implicit def objectsResultDecoder[F[_]: Effect]
    : EntityDecoder[F, ObjectsResult] =
    for {
      elem <- xml
    } yield {
      val nextContinuationToken = (elem \\ "NextContinuationToken").text
      val isTruncated = (elem \\ "IsTruncated").text == "true"
      val objects = (elem \\ "Contents").map { objectElem =>
        Object(
          ObjectKey((objectElem \ "Key").text),
          ZonedDateTime.parse((objectElem \ "LastModified").text),
          (objectElem \ "Size").text.toLong,
          (objectElem \ "StorageClass").text,
          Etag((objectElem \ "ETag").text)
        )
      }

      ObjectsResult(objects, nextContinuationToken, isTruncated)
    }

  val baseEndpoint: Uri = endpoint
    .getOrElse {
      if (region == "us-east-1") {
        Uri.unsafeFromString(s"$scheme://s3.amazonaws.com")
      } else {
        Uri.unsafeFromString(s"$scheme://s3-$region.amazonaws.com")
      }
    }

  val signingKey =
    SigningKey(credentials, CredentialScope(LocalDate.now(), region, "s3"))

  def listBuckets[F[_]: Effect]: Stream[F, Bucket] = {
    val request = signedRequest(Request(Method.GET, baseEndpoint), signingKey)

    withHttpClient[F, Bucket] { client =>
      Stream.eval(client.expect[Seq[Bucket]](request)).flatMap(Stream(_: _*))
    }

  }

  def listObjects[F[_]: Effect](bucket: BucketName,
                                maxKeys: Int = 1000): Stream[F, Object] = {

    val uri = bucketUri(bucket)
      .withQueryParam("list-type", 2)
      .withQueryParam("max-keys", maxKeys)
      .withQueryParam("fetch-owner", "false")

    def makeRequest(
        client: Client[F],
        continuationToken: Option[String] = None): F[ObjectsResult] = {

      val thisUri = continuationToken
        .map { ct =>
          uri.withQueryParam("continuation-token", ct)
        }
        .getOrElse(uri)

      val request = signedRequest(Request(Method.GET, thisUri), signingKey)

      client.expect[ObjectsResult](request)
    }

    withHttpClient[F, ObjectsResult] { client =>
      Stream.eval(makeRequest(client)).flatMap { result =>
        Stream.unfoldEval[F, ObjectsResult, ObjectsResult](result) {
          prevResult =>
            if (prevResult.isTruncated) {
              makeRequest(client, Some(prevResult.nextContinuationToken))
                .map(r => Option(r -> r))
            } else {
              Sync[F].delay(None)
            }
        }
      }
    }.mapChunks { chunk =>
      chunk.mapConcat(result => Chunk.seq(result.objects))
    }

  }

  def fetchObjectInfo[F[_]: Effect](bucket: BucketName,
                                    key: ObjectKey): F[Option[Headers]] = {

    val request =
      signedRequest(Request(Method.HEAD, objectUri(bucket, key)), signingKey)

    withHttpClient[F, Headers] { client =>
      Stream.eval(client.fetch[Headers](request)(r => Sync[F].pure(r.headers)))
    }.runLast

  }

  def fetchObject[F[_]: Effect](bucket: BucketName,
                                key: ObjectKey): Stream[F, Byte] = {

    val request =
      signedRequest(Request(Method.GET, objectUri(bucket, key)), signingKey)

    withHttpClient[F, Byte] { client =>
      client.streaming[Byte](request)(_.body)
    }
  }

  def uploadObject[F[_]: Sync](
      bucket: BucketName,
      key: ObjectKey): Kleisli[F, Client[F], Pipe[F, Byte, Elem]] = Kleisli {
    client =>
      val pipe: Pipe[F, Byte, Elem] = { body: Stream[F, Byte] =>
        val request = signedRequest(
          Request(Method.PUT, objectUri(bucket, key), body = body),
          signingKey)
        Stream.eval(client.expect[Elem](request))
      }

      Sync[F].pure(pipe)
  }

  private def bucketUri(bucketName: BucketName): Uri = {

    if (usePathStyleUrl) {
      baseEndpoint.withPath(s"/${bucketName.value}")
    } else {
      baseEndpoint.copy(authority = baseEndpoint.authority.map(authority =>
        authority.copy(
          host = Uri.RegName(s"${bucketName.value}.${authority.host.value}"))))
    }
  }

  private def objectUri(bucketName: BucketName, key: ObjectKey): Uri = {
    bucketUri(bucketName) / key.value
  }

  private def withHttpClient[F[_], O](f: Client[F] => Stream[F, O])(
      implicit F: Effect[F]): Stream[F, O] = {
    Stream.bracket[F, Client[F], O](F.delay(PooledHttp1Client[F]()))(
      f,
      c => c.shutdown)
  }
}

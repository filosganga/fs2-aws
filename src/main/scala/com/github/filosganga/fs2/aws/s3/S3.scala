package com.github.filosganga.fs2.aws.s3

import java.time.{LocalDate, ZonedDateTime}

import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.functor._
import com.github.filosganga.fs2.aws.auth._
import fs2._
import org.http4s.client.Client
import org.http4s.scalaxml._
import org.http4s._

import scala.language.higherKinds
import scala.xml.Elem
import com.typesafe.scalalogging._
import org.http4s.headers.`Content-Length`

class S3(credentials: AwsCredentials, region: String, endpoint: Option[Uri], usePathStyleUrl: Boolean, scheme: String = "http") extends LazyLogging {

  val baseEndpoint: Uri = endpoint
    .getOrElse {
      if (region == "us-east-1") {
        Uri.unsafeFromString(s"$scheme://s3.amazonaws.com")
      } else {
        Uri.unsafeFromString(s"$scheme://s3-$region.amazonaws.com")
      }
    }

  val signingKey = SigningKey(credentials, CredentialScope(LocalDate.now(), region, "s3"))

  def listBuckets[F[_] : Sync](): Kleisli[F, Client[F], Stream[F, Bucket]] = Kleisli { client =>
    val request = signedRequest(Request(Method.GET, baseEndpoint), signingKey)

    client.expect[Elem](request).map { elem =>
      Stream((elem \\ "Bucket").map { node =>
        Bucket(
          BucketName((node \ "Name").text), ZonedDateTime.parse((node \ "CreationDate").text))
      }: _*)
    }

  }

  def listObjects[F[_] : Sync](bucket: BucketName, maxKeys: Int = 1000): Kleisli[F, Client[F], Stream[F, Object]] = Kleisli { client =>

    val uri = bucketUri(bucket)
      .withQueryParam("list-type", 2)
      .withQueryParam("max-keys", maxKeys)
      .withQueryParam("fetch-owner", "false")


    case class Result(objects: Seq[Object], nextContinuationToken: String, isTruncated: Boolean)

    def makeRequest(continuationToken: Option[String] = None): F[Result] = {

      val thisUri = continuationToken.map { ct =>
        uri.withQueryParam("continuation-token", ct)
      }.getOrElse(uri)

      val request = signedRequest(Request(Method.GET, thisUri), signingKey)

      client.expect[Elem](request).map { elem =>

        val nextContinuationToken = (elem \\ "NextContinuationToken").text
        val isTruncated = (elem \\ "IsTruncated").text == "true"
        val objects = (elem \\ "Contents").map { objectElem =>
          Object(
            ObjectKey((objectElem \ "Key").text),
            ZonedDateTime.parse((objectElem \ "LastModified").text),
            (objectElem \ "Size").text.toLong,
            (objectElem \ "StorageClass").text,
            Etag((objectElem \ "ETag").text),
          )
        }

        Result(objects, nextContinuationToken, isTruncated)
      }
    }

    val results: Stream[F, Result] = Stream.eval(makeRequest()).flatMap { result =>
      Stream.unfoldEval[F, Result, Result](result) { prevResult =>
        if (prevResult.isTruncated) {
          makeRequest(Some(prevResult.nextContinuationToken))
            .map(r => Option(r -> r))
        } else {
          Sync[F].delay(None)
        }
      }
    }

    val objects: Stream[F, Object] = results.mapChunks { chunk =>
      chunk.mapConcat(result => Chunk.seq(result.objects))
    }

    Sync[F].pure(objects)
  }

  def fetchObjectInfo[F[_] : Sync](bucket: BucketName, key: ObjectKey): Kleisli[F, Client[F], Headers] = Kleisli { client =>

    val request = signedRequest(Request(Method.HEAD, objectUri(bucket, key)), signingKey)

    client.fetch[Headers](request)(r => Sync[F].pure(r.headers))
  }

  def fetchObject[F[_] : Sync](bucket: BucketName, key: ObjectKey): Kleisli[F, Client[F], Stream[F, Byte]] = Kleisli { client =>

    val request = signedRequest(Request(Method.GET, objectUri(bucket, key)), signingKey)

    Sync[F].pure(client.streaming[Byte](request)(_.body))
  }

  def fetchMultipartObject[F[_] : Sync](bucket: BucketName, key: ObjectKey, chunkSize: Long = 1024 * 1024L): Kleisli[F, Client[F], Stream[F, Byte]] = for {
    info <- fetchObjectInfo(bucket, key)
    u <- Kleisli[F, Client[F], Stream[F, Byte]] { client =>

      val length = `Content-Length`.from(info).get.length

      val stream = Stream.unfold[Long, (Long, Long)](0L) { start =>
        if (start >= length) {
          None
        } else {
          val end = Math.min(start + chunkSize - 1, length - 1)
          Some((start, end), end + 1)
        }
        // TODO Of course it does not download in parallel. we need to use queues.
      }.flatMap { case (start, end) =>
        val request = signedRequest(Request(Method.GET, objectUri(bucket, key))
          .putHeaders(headers.Range(start, end)), signingKey)

        client.streaming[Byte](request)(_.body)
      }

      Sync[F].pure(stream)
    }
  } yield u


  private def bucketUri(bucketName: BucketName): Uri = {

    if (usePathStyleUrl) {
      baseEndpoint.withPath(s"/${bucketName.value}")
    } else {
      baseEndpoint.copy(authority = baseEndpoint.authority.map(authority =>
        authority.copy(host = Uri.RegName(s"${bucketName.value}.${authority.host.value}"))
      ))
    }
  }

  private def objectUri(bucketName: BucketName, key: ObjectKey): Uri = {
    bucketUri(bucketName) / key.value
  }
}

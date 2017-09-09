package com.github.filosganga.fs2.aws.auth

import java.net.URLEncoder

import org.http4s.Uri.Path
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, Query, Request}

private[aws] case class CanonicalRequest(method: String,
                                         uri: String,
                                         queryString: String,
                                         headerString: String,
                                         signedHeaders: String,
                                         hashedPayload: String) {
  def canonicalString: String = s"$method\n$uri\n$queryString\n$headerString\n$signedHeaders\n$hashedPayload"
}

private[aws] object CanonicalRequest {
  def from[F[_]](req: Request[F]): CanonicalRequest = {
    CanonicalRequest(
      req.method.name,
      preprocessPath(req.uri.path),
      canonicalQueryString(req.uri.query),
      canonicalHeaderString(req.headers),
      signedHeadersString(req.headers),
      req.headers.get(CaseInsensitiveString("x-amz-content-sha256")).get.value
    )
  }

  def canonicalQueryString(query: Query): String =
    query.sortBy(_._1).map { case (a, b) => s"${uriEncode(a)}=${uriEncode(b.getOrElse(""))}" }.mkString("&")

  private def uriEncode(str: String) = URLEncoder.encode(str, "utf-8")

  /**
    * URL encodes the given string.  This allows us to pass special characters
    * that would otherwise be rejected when building a URI instance.  Because we
    * need to retain the URI's path structure we subsequently need to replace
    * percent encoded path delimiters back to their decoded counterparts.
    */
  private def preprocessPath(path: Path): String =
    uriEncode(if(path.startsWith("/")) path else "/" + path)
      .replace(":", "%3A")
      .replace("%2F", "/")

  def canonicalHeaderString(headers: Headers): String = {

    val multipleSpaceRegex = "\\s+".r
    val grouped = headers.groupBy(_.name)
    val combined = grouped.mapValues(_.map(h => multipleSpaceRegex.replaceAllIn(h.value, " ").trim).mkString(","))

    combined.toSeq.sortBy(_._1).map { case (k, v) => s"${k.value.toLowerCase}:$v\n" }.mkString("")
  }

  def signedHeadersString(headers: Headers): String =
    headers
      .map(_.name.value.toLowerCase)
      .toSeq
      .distinct
      .sorted
      .mkString(";")

}
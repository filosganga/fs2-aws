package com.github.filosganga.fs2.aws.auth

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private[aws] final case class CredentialScope(date: LocalDate,
                                              awsRegion: String,
                                              awsService: String) {
  lazy val formattedDate: String =
    date.format(DateTimeFormatter.BASIC_ISO_DATE)

  def scopeString = s"$formattedDate/$awsRegion/$awsService/aws4_request"
}

private[aws] final case class SigningKey(credentials: AwsCredentials,
                                         scope: CredentialScope,
                                         algorithm: String = "HmacSHA256") {

  val rawKey = new SecretKeySpec(
    s"AWS4${credentials.secretAccessKey}".getBytes,
    algorithm)

  def signature(message: Array[Byte]): Array[Byte] = signWithKey(key, message)

  def hexEncodedSignature(message: Array[Byte]): String =
    encodeHex(signature(message))

  def credentialString: String =
    s"${credentials.accessKeyId}/${scope.scopeString}"

  lazy val key: SecretKeySpec =
    wrapSignature(dateRegionServiceKey, "aws4_request".getBytes)

  lazy val dateRegionServiceKey: SecretKeySpec =
    wrapSignature(dateRegionKey, scope.awsService.getBytes)

  lazy val dateRegionKey: SecretKeySpec =
    wrapSignature(dateKey, scope.awsRegion.getBytes)

  lazy val dateKey: SecretKeySpec =
    wrapSignature(rawKey, scope.formattedDate.getBytes)

  private def wrapSignature(signature: SecretKeySpec,
                            message: Array[Byte]): SecretKeySpec =
    new SecretKeySpec(signWithKey(signature, message), algorithm)

  private def signWithKey(key: SecretKeySpec,
                          message: Array[Byte]): Array[Byte] = {
    val mac = Mac.getInstance(algorithm)
    mac.init(key)
    mac.doFinal(message)
  }
}

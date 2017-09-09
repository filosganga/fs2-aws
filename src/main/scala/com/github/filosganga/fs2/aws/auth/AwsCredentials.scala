package com.github.filosganga.fs2.aws.auth

sealed trait AwsCredentials {
  def accessKeyId: String
  def secretAccessKey: String
}

final case class BasicCredentials(accessKeyId: String, secretAccessKey: String) extends AwsCredentials

final case class AwsSessionCredentials(accessKeyId: String, secretAccessKey: String, sessionToken: String)
  extends AwsCredentials

object AwsCredentials {
  def apply(accessKeyId: String, secretAccessKey: String): BasicCredentials =
    BasicCredentials(accessKeyId, secretAccessKey)
}

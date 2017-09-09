package com.github.filosganga.fs2.aws.s3

import java.time.ZonedDateTime

case class Bucket(name: BucketName, creationDate: ZonedDateTime)

case class Object(key: ObjectKey, lastModified: ZonedDateTime, size: Long, storageClass: String, etag: Etag)

case class ObjectKey(value: String) extends AnyVal

case class BucketName(value: String) extends AnyVal

case class Etag(value: String) extends AnyVal
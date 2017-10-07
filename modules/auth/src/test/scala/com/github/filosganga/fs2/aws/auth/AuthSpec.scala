package com.github.filosganga.fs2.aws.auth

import java.security.{MessageDigest, Security}

import fs2._
import fs2.hash._

class AuthSpec extends UnitSpec {

  "digest" should {
    "calculate the correct digest" in forAll() { data: Array[Byte] =>
      val d = MessageDigest.getInstance("SHA-256").digest(data)

      val testValue = Stream(data: _*).throughPure(sha256).toList

      testValue shouldBe d.toList
    }
  }

}

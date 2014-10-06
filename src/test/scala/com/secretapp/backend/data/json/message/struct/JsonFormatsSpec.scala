package com.secretapp.backend.data.json.message.struct

import com.secretapp.backend.data.json.JsonSpec
import com.secretapp.backend.data.json.message.rpc.file.JsonFormatsSpec._
import com.secretapp.backend.data.message.struct._
import com.secretapp.backend.data.types.Male
import play.api.libs.json._
import scala.util.Random
import scalaz._
import Scalaz._
import JsonFormatsSpec._

class JsonFormatsSpec extends JsonSpec {

  "(de)serializer" should {

    "(de)serialize UserId" in {
      val (v, j) = genUserId
      testToAndFromJson[UserId](j, v)
    }

    "(de)serialize AvatarImage" in {
      val (v, j) = genAvatarImage
      testToAndFromJson[AvatarImage](j, v)
    }

    "(de)serialize Avatar" in {
      val (v, j) = genAvatar
      testToAndFromJson[Avatar](j, v)
    }

    "(de)serialize User" in {
      val (avatar, avatarJson) = genAvatar
      val v = User(16, 17, "name", Male.some, Set(18), 19, avatar.some)
      val j = Json.obj(
        "uid"         -> 16,
        "accessHash"  -> "17",
        "name"        -> "name",
        "sex"         -> "male",
        "keyHashes"   -> Json.arr("18"),
        "phoneNumber" -> "19",
        "avatar"      -> avatarJson
      )
      testToAndFromJson[User](j, v)
    }

  }

}

object JsonFormatsSpec {

  def genUserId = {
    val uid = Random.nextInt()
    val accessHash = Random.nextLong()

    (
      UserId(1, 2),
      Json.obj(
        "uid"        -> 1,
        "accessHash" -> "2"
      )
    )
  }

  def genAvatarImage = {
    val (fileLocation, fileLocationJson) = genFileLocation
    val width = Random.nextInt()
    val height = Random.nextInt()
    val fileSize = Random.nextInt()

    (
      AvatarImage(fileLocation, width, height, fileSize),
      Json.obj(
        "fileLocation" -> fileLocationJson,
        "width"        -> width,
        "height"       -> height,
        "fileSize"     -> fileSize
      )
    )
  }

  def genAvatar = {
    val (smallImage, smallImageJson) = genAvatarImage
    val (largeImage, largeImageJson) = genAvatarImage
    val (fullImage, fullImageJson) = genAvatarImage

    (
      Avatar(
        smallImage.some,
        largeImage.some,
        fullImage.some
      ),
      Json.obj(
        "smallImage" -> smallImageJson,
        "largeImage" -> largeImageJson,
        "fullImage" -> fullImageJson
      )
    )
  }

}

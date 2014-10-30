package com.secretapp.backend.data.message.struct

@SerialVersionUID(1L)
case class Avatar(
  smallImage: Option[AvatarImage],
  largeImage: Option[AvatarImage],
  fullImage: Option[AvatarImage])

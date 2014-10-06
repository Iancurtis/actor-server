package com.secretapp.backend.data.json

package object message
  extends JsonFormats
  with rpc.JsonFormats
  with rpc.auth.JsonFormats
  with rpc.contact.JsonFormats
  with rpc.file.JsonFormats
  with rpc.messaging.JsonFormats
  with rpc.presence.JsonFormats
  with rpc.update.JsonFormats
  with struct.JsonFormats {

}

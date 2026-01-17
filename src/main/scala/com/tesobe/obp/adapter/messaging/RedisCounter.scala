package com.tesobe.obp.adapter.messaging

import cats.effect.{IO, Resource}
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.RedisCommands

object RedisCounter {

  def create(
      host: String,
      port: Int
  ): Resource[IO, RedisCommands[IO, String, String]] = {
    Redis[IO].utf8(s"redis://$host:$port")
  }

  def incrementOutbound(
      redis: RedisCommands[IO, String, String],
      messageType: String
  ): IO[Unit] = {
    redis.incr(s"obp-rabbit-cats-adapter-outbound:$messageType").void
  }

  def incrementInbound(
      redis: RedisCommands[IO, String, String],
      messageType: String
  ): IO[Unit] = {
    redis.incr(s"obp-rabbit-cats-adapter-inbound:$messageType").void
  }

  def getOutboundCount(
      redis: RedisCommands[IO, String, String],
      messageType: String
  ): IO[Long] = {
    redis
      .get(s"obp-rabbit-cats-adapter-outbound:$messageType")
      .map(_.map(_.toLong).getOrElse(0L))
  }

  def getInboundCount(
      redis: RedisCommands[IO, String, String],
      messageType: String
  ): IO[Long] = {
    redis
      .get(s"obp-rabbit-cats-adapter-inbound:$messageType")
      .map(_.map(_.toLong).getOrElse(0L))
  }

  def getAllCounts(
      redis: RedisCommands[IO, String, String]
  ): IO[Map[String, (Long, Long)]] = {
    for {
      outboundKeys <- redis.keys("obp-rabbit-cats-adapter-outbound:*")
      inboundKeys <- redis.keys("obp-rabbit-cats-adapter-inbound:*")

      allMessageTypes = (outboundKeys.map(
        _.stripPrefix("obp-rabbit-cats-adapter-outbound:")
      ) ++
        inboundKeys.map(
          _.stripPrefix("obp-rabbit-cats-adapter-inbound:")
        )).toSet

      counts <- allMessageTypes.toList.traverse { messageType =>
        for {
          outbound <- getOutboundCount(redis, messageType)
          inbound <- getInboundCount(redis, messageType)
        } yield (messageType, (outbound, inbound))
      }
    } yield counts.toMap
  }
}

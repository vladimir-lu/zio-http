package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.content.handlers.ServerResponseHandler
import zhttp.service.server.{ServerTime, WebSocketUpgrade}
import zio.{UIO, ZIO}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTimeGenerator: ServerTime,
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R]
    with ServerResponseHandler[R] { self =>

  override def channelRead0(ctx: Ctx, jReq: FullHttpRequest): Unit = {
    jReq.touch("server.Handler-channelRead0")
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(jReq, app)
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response],
  )(implicit ctx: Ctx, ev2: HttpConvertor[FullHttpRequest, A]): Unit = {
    http.execute(jReq, ctx) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) =>
                UIO {
                  writeResponse(
                    Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))),
                    jReq,
                  )
                }
              case None        =>
                UIO {
                  writeResponse(Response.status(Status.NOT_FOUND), jReq)
                }

            },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(ctx, jReq, res))
              else {
                for {
                  _ <- ZIO {
                    writeResponse(res, jReq)
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          writeResponse(res, jReq): Unit
        }

      case HExit.Failure(e) =>
        writeResponse(Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))), jReq): Unit

      case HExit.Empty =>
        writeResponse(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))), jReq): Unit

    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    rt.unsafeRun(ctx) {
      program
    }

  override def serverTime: ServerTime = serverTimeGenerator

  override val rt: HttpRuntime[R] = runtime

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }
}

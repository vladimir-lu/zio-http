package zhttp.service
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}
import zhttp.logging.Logger

final class RequestBodyHandler(val callback: UnsafeChannel => UnsafeContent => Unit)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private val log = Logger.getLogger("zhttp.service.RequestBodyHandler")

  private var onMessage: UnsafeContent => Unit = _

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    log.trace(s"Handling content: $msg")
    self.onMessage(new UnsafeContent(msg))
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    log.trace(s"RequestBodyHandler as been added to the channel pipeline.")
    self.onMessage = callback(new UnsafeChannel(ctx))
    ctx.read(): Unit
  }
}

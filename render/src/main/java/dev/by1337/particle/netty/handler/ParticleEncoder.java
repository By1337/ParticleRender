package dev.by1337.particle.netty.handler;

import dev.by1337.particle.netty.buffer.ByteBufUtil;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleSource;
import dev.by1337.particle.via.ParticleWriter;
import dev.by1337.particle.via.ViaHook;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class ParticleEncoder extends MessageToByteEncoder<ParticleSource> implements PacketBuilder {

    private static final Logger log = LoggerFactory.getLogger("ParticleEncoder");

    private final ViaHook.ViaMutator via;
    private final int protocolVersion;
    private ByteBuf out;
    private final int fallbackProtocol;
    private boolean doCompress;

    public ParticleEncoder(Channel channel, UUID player, int fallbackProtocol) {
        via = ViaHook.getViaMutator(player, channel, fallbackProtocol);
        protocolVersion = via.protocol();
        this.fallbackProtocol = fallbackProtocol;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ParticleSource writer, ByteBuf byteBuf) throws Exception {
        doCompress = ctx.channel().pipeline().get("compress") != null;
        this.out = byteBuf;
        try {
            writer.doWrite(this, 0, 0, 0);
        } catch (Exception e) {
            log.error("Failed to write packet!", e);
        } finally {
            out = null;
        }
    }

    // [prepender size][compress size][packet id][payload]
    // prepender size varInt(1-3)
    // compress size varInt
    // packet id varInt
    // packet payload
    @Override
    public void append(ParticleData particle, double x, double y, double z, float xDist, float yDist, float zDist) {
        final ByteBuf out = this.out;
        final int startPrt = out.writerIndex();
        // резервируем 2 байта под prepender size, максимум 2^14,
        out.writeZero(2);

        if (doCompress) {
            // пишем "compressed" size, вообще 0 значит что payload у нас не сжат.
            out.writeByte(0);
        }

        final int payloadStart = out.writerIndex();
        final int protocolVersion;
        final int writeLike = ParticleWriter.write(protocolVersion = this.protocolVersion, out, particle, x, y, z, xDist, yDist, zDist);
        if (writeLike == -1) {
            out.writerIndex(startPrt);
            return;
        }
        if (writeLike != protocolVersion) {
            if (ViaHook.HAS_VIA && writeLike == fallbackProtocol) {
                try {
                    // без slice via version не умеет
                    out.ensureWritable(256);
                    int widx = out.writerIndex() - payloadStart;
                    var slice = out.slice(payloadStart, widx + 256);
                    slice.writerIndex(widx);
                    via.mutator().accept(slice);
                    out.writerIndex(payloadStart + slice.writerIndex());
                } catch (Exception e) {
                    log.error("Failed to adapt packet via ViaVersion!", e);
                    out.writerIndex(startPrt);
                    return;
                }
            } else {
                log.error("Записал как {} хотя ожидалось {} или {}", writeLike, protocolVersion, fallbackProtocol);
                out.writerIndex(startPrt);
                return;
            }
        }

        final int prependerSize = out.writerIndex() - startPrt - 2;
        if (prependerSize >= 1 << 14) {
            // Под prepender size есть только 2 байта.
            // Такого никогда не должно быть так как в 16384 байт влазит любой партикл.
            log.error("Packet size exceeds 16384!");
            out.writerIndex(startPrt);
            return;
        }
        ByteBufUtil.setVarInt2(out, startPrt, prependerSize);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) {
        return msg instanceof ParticleSource;
    }

    @TestOnly
    public ByteBuf out() {
        return out;
    }

    @TestOnly
    public void setOut(ByteBuf out) {
        this.out = out;
    }
}

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
import java.util.concurrent.TimeUnit;

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

    //[prepender size][compress size][packet id][payload]
    // prepender size varInt(1-3)
    // compress size varInt
    // packet id varInt
    // packet payload
    @Override
    public void append(ParticleData particle, double x, double y, double z, float xDist, float yDist, float zDist) {
        final ByteBuf out = this.out;
        final int prependerStartIdx = out.writerIndex();
        // пишем prepender size в два байта, максимум 2^14,
        // этого достаточно так как если размер будет больше чем COMPRESSION_THRESHOLD это значение перезапишется после сжатия
        ByteBufUtil.writeVarInt2(out, 0);

        final int compressStartIdx;
        if (doCompress) {
            compressStartIdx = prependerStartIdx + 2;
            // compress size всегда 0 если размер меньше COMPRESSION_THRESHOLD, если больше то при сжатии это значение перезапишется
            ByteBufUtil.writeVarInt1(out, 0);
        } else {
            compressStartIdx = -1;
        }

        final int payloadStart = doCompress ? compressStartIdx + 1 : prependerStartIdx + 2;
        final int protocolVersion = this.protocolVersion;
        final int writeLike = ParticleWriter.write(protocolVersion, out, particle, x, y, z, xDist, yDist, zDist);
        if (writeLike == -1) {
            out.writerIndex(prependerStartIdx);
            return;
        }
        if (writeLike != protocolVersion) {
            if (ViaHook.HAS_VIA && writeLike == fallbackProtocol) {
                try {
                    // без slice via не умеет
                    out.ensureWritable(256);
                    int widx = out.writerIndex() - payloadStart;
                    var slice = out.slice(payloadStart, widx + 256);
                    slice.writerIndex(widx);
                    via.mutator().accept(slice);
                    out.writerIndex(payloadStart + slice.writerIndex());
                } catch (Exception e) {
                    log.error("Failed to adapt packet via ViaVersion!", e);
                    out.writerIndex(prependerStartIdx);
                    return;
                }
            } else {
                log.error("Записал как {} хотя ожидалось {} или {}", writeLike, protocolVersion, fallbackProtocol);
                out.writerIndex(prependerStartIdx);
                return;
            }
        }
        final int prependerSize;
        if (doCompress) {
            // Вообще надо бы сжать пакет, но пакет вряд ли будет размером больше чем 256 байт
            // Только партикл с ItemStack может превысить, но клиент всё равно примет пакет даже если он не был сжат.
            // Если решится на сжатие, то сюда надо прокинуть Deflater, который можно создать в ParticleEncoder.
            prependerSize = out.writerIndex() - compressStartIdx;
        } else {
            prependerSize = out.writerIndex() - prependerStartIdx - 2;
        }

        //Под prepender size выделили только 2 байта...
        //Если 1 пакет с партиклом занимает больше 16384 байт, то это не норма
        if (prependerSize > 16384) {
            // Здесь можно весь буфер с prependerStartIdx+2 сдвинуть на 1 байт и всё же записать prepender size,
            // но смысл поддерживать плохие решения когда в пакет попадает ItemStack с градиентами и вообще со всем...
            log.error("Packet size exceeds 16384!");
            out.writerIndex(prependerStartIdx);
            return;
        }
        ByteBufUtil.setVarInt2(out, prependerStartIdx, prependerSize);
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

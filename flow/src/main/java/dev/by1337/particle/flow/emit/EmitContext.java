package dev.by1337.particle.flow.emit;

public class EmitContext {
    private final int maxDepth;
    private int tick;
    private int depth;

    public EmitContext(int maxDepth, int tick) {
        this.maxDepth = maxDepth;
        this.tick = tick;
    }

    public int tick() {
        return tick;
    }
    public void pushDepth(){
        if (depth++ > maxDepth) throw new IllegalStateException("depth " + depth + " but max " + maxDepth);
    }
    public void popDepth(){
        depth--;
    }
}

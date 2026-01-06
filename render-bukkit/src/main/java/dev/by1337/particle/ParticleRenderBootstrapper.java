package dev.by1337.particle;

import org.bukkit.plugin.Plugin;

public class ParticleRenderBootstrapper {
    private final String handlerName;
    private final Plugin owner;
    private ParticlePipelineManager manager;

    public ParticleRenderBootstrapper(String handlerName, Plugin owner) {
        this.handlerName = handlerName;
        this.owner = owner;
    }
    public void enable(){
        manager = new  ParticlePipelineManager(owner, handlerName);
    }
    public void disable(){
        manager.close();
        manager = null;
    }
}

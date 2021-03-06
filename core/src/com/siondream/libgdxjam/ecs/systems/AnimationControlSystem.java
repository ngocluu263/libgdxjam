package com.siondream.libgdxjam.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.Logger;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState.TrackEntry;
import com.esotericsoftware.spine.AnimationStateData;
import com.siondream.libgdxjam.Env;
import com.siondream.libgdxjam.animation.Entry;
import com.siondream.libgdxjam.animation.Layer;
import com.siondream.libgdxjam.ecs.Mappers;
import com.siondream.libgdxjam.ecs.components.SpineComponent;
import com.siondream.libgdxjam.ecs.components.AnimationControlComponent;

public class AnimationControlSystem extends IteratingSystem implements EntityListener {

	private Bits tmp = new Bits();
	Logger logger = new Logger(
		AnimationControlSystem.class.getSimpleName(),
		Env.LOG_LEVEL
	);
	
	public AnimationControlSystem() {
		super(Family.all(
			SpineComponent.class,
			AnimationControlComponent.class).get()
		);
		
		logger.info("initialize");
	}
	
	@Override
	public void addedToEngine(Engine engine) {
		super.addedToEngine(engine);
		engine.addEntityListener(getFamily(), this);
	}
	
	@Override
	public void removedFromEngine(Engine engine) {
		super.removedFromEngine(engine);
		engine.removeEntityListener(this);
	}
	
	@Override
	public void entityAdded(Entity entity) {
		setupTransitions(entity);
	}

	@Override
	public void entityRemoved(Entity entity) {
		
	}

	@Override
	protected void processEntity(Entity entity, float deltaTime) {
		AnimationControlComponent control = Mappers.animControl.get(entity);
		SpineComponent spine = Mappers.spine.get(entity);
		
		for (Layer layer : control.data.layers) {
			updateLayer(spine, control, layer);
		}
	}
	
	private void updateLayer(SpineComponent spine, Bits state, Layer layer) {
		TrackEntry track = spine.state.getCurrent(layer.track);
		Animation current = track != null ? track.getAnimation() : null;
		Entry entry = getBestMatch(layer.entries, state);
		
		if (entry != null && entry.animation != current) {
			logger.info("new best match: " + entry.animation.getName());
			
			spine.state.setAnimation(
				layer.track,
				entry.animation,
				entry.loop
			);
		}
	}
	
	private Entry getBestMatch(ImmutableArray<Entry> entries, Bits state) {
		Entry best = null;
		int maxScore = Integer.MIN_VALUE;
		
		for (Entry entry : entries) {
			int score = getScore(state, entry.tags);
			if (score > maxScore) {
				maxScore = score;
				best = entry;
			}
		}
		
		return best;
	}
	
	private int getScore(Bits state, Bits tags) {
		if (!state.containsAll(tags)) {
			return Integer.MIN_VALUE;
		}
		
		tmp.clear();
		tmp.or(state);
		tmp.and(tags);
		
		int score = 0;
		int index = 0;
		
		while ((index = tmp.nextSetBit(index)) > -1) {
			score++;
			index++;
		}
		
		return score;
	}
	
	private void setupTransitions(Entity entity) {
		AnimationControlComponent animControl = Mappers.animControl.get(entity);
		SpineComponent spine = Mappers.spine.get(entity);
		AnimationStateData data = spine.state.getData();
		
		data.setDefaultMix(animControl.data.defaultTransition());
		
		Array<Animation> animations = data.getSkeletonData().getAnimations();
		for (int i = 0; i < animations.size; ++i) {
			for (int j = 0; j < animations.size; ++j) {
				if (i == j) { continue; }
				
				Animation from = animations.get(i);
				Animation to = animations.get(j);
				
				if (animControl.data.hasTransition(from, to)) {
					float duration = animControl.data.transition(from, to);
					data.setMix(from, to, duration);
				}
			}
		}
	}
}

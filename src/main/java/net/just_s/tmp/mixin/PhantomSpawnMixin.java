package net.just_s.tmp.mixin;

import net.just_s.tmp.TMPMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnMixin {
	@Unique
	private int tick;

	@Inject(at = @At("HEAD"), method = "spawn", cancellable = true)
	private void tick(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
		if (!spawnMonsters || !TMPMod.CONFIG.doInsomnia) {
			cir.setReturnValue(0);
			return;
		}
		--this.tick;
		if (this.tick <= 0) {
			this.tick += 20 * (TMPMod.CONFIG.insomniaMinCycleTime + world.random.nextInt(TMPMod.CONFIG.insomniaRandomizationTime));
			if (!world.isDay()) {
				cir.setReturnValue(customSpawn(world));
			}
		}
	}

	private int customSpawn(ServerWorld world) {
		Random random = world.random;
		int phantomsSpawned = 0;

		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}

			ServerStatHandler stats = player.getStatHandler();
			int noRest = Math.max(1, Math.min(2147483647, stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST))));

			if (random.nextInt(noRest) <= TMPMod.CONFIG.insomniaSpawnStartTimer * 20) {
				continue;
			}

			BlockPos bpos = player.getBlockPos();
			Vec3d worldSpawn = world.getSpawnPos().toCenterPos();
			double squaredRadius = TMPMod.CONFIG.phantomFreeArea * TMPMod.CONFIG.phantomFreeArea;
			boolean isInRadius = player.squaredDistanceTo(worldSpawn) <= squaredRadius;

			if (bpos.getY() < world.getSeaLevel()) continue;
			if (!world.isSkyVisible(bpos)) continue;
			if (TMPMod.CONFIG.insomniaLightStopsPhantoms < world.getLightLevel(bpos)) continue;
			if (isInRadius) continue;

			// all checks are fine, now finding place to spawn
			BlockPos spawnPos;
			BlockState blockState;
			FluidState fluidState;
			int k = 64; // safeguard
			do {
				spawnPos = bpos.up(20 + random.nextInt(15)).east(-10 + random.nextInt(21)).south(-10 + random.nextInt(21));
				blockState = world.getBlockState(spawnPos);
				fluidState = world.getFluidState(spawnPos);
				k--;
			} while (!SpawnHelper.isClearForSpawn(world, spawnPos, blockState, fluidState, EntityType.PHANTOM) && k > 0);
			if (k <= 0) continue;

			// place found, calculate phantoms amount for player
			EntityData entityData = null;
			LocalDifficulty localDifficulty = world.getLocalDifficulty(bpos);
			int amountOfPhantomsForPlayer = TMPMod.CONFIG.minAmountPerSpawn + random.nextInt(localDifficulty.getGlobalDifficulty().getId() + 1);
			amountOfPhantomsForPlayer = MathHelper.clamp(amountOfPhantomsForPlayer, TMPMod.CONFIG.minAmountPerSpawn, TMPMod.CONFIG.maxAmountPerSpawn);

			for (int m = 0; m < amountOfPhantomsForPlayer; ++m) {
				PhantomEntity phantomEntity = EntityType.PHANTOM.create(world);
				if (phantomEntity == null) continue;
				phantomEntity.refreshPositionAndAngles(spawnPos, 0.0F, 0.0F);
				entityData = phantomEntity.initialize(world, localDifficulty, SpawnReason.NATURAL, entityData);
				world.spawnEntityAndPassengers(phantomEntity);
				phantomsSpawned++;
			}
		}

		return phantomsSpawned;
	}
}

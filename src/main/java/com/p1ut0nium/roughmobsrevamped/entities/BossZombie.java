package com.p1ut0nium.roughmobsrevamped.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import com.p1ut0nium.roughmobsrevamped.misc.BossHelper;
import com.p1ut0nium.roughmobsrevamped.util.handlers.FogEventHandler;
import com.p1ut0nium.roughmobsrevamped.util.handlers.SoundHandler;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

public class BossZombie extends EntityZombie implements IBoss {
	
	private static int BATSWARM_ATTACK_RANGE = BossHelper.bossBatSwarmRange;
	private static int BATSWARM_MINIONS_MAX = BossHelper.bossBatSwarmCount;
	private static int BATSWARM_DELAY = BossHelper.bossBatSwarmDelay * 20;
	
	private static int FOG_MAX_DISTANCE = BossHelper.bossFogMaxDistance;
    private static int FOG_DOT_DELAY = BossHelper.bossFogDoTDelay * 20;
    private static int FOG_DOT_DAMAGE = BossHelper.bossFogDoTDamage;
    private static boolean FOG_DOT_ENABLED = BossHelper.bossFogDoTEnabled;
    private static boolean FOG_WARNING_ENABLED = BossHelper.bossFogDoTWarning;
    
	private static int fog_dot_tick;
	private static int batSwarmTick;
    
    private List<EntityPlayer> playersInFogRange;
    private HashMap<String, Boolean> playersWarned = new HashMap<>();
    private TextComponentString fogWarningMsg;
	private List<EntityHostileBat> batMinions = new ArrayList<EntityHostileBat>();
	
	// TODO private double[] bossColorTheme = {0.0, 1.0, 0.0};
	
	public BossZombie(World worldIn) {
		super(worldIn);
        this.experienceValue = 100;
        fog_dot_tick = 0;
        batSwarmTick = 0;
        
		fogWarningMsg = new TextComponentString("The thick fog reaches out for you... You begin to choke as you move through it.\nPerhaps you should find the source of the poisonous mist, or flee to safety.");
		fogWarningMsg.getStyle().setColor(TextFormatting.DARK_GREEN);
	}

	@Override
	public void onAddedToWorld() {
    	super.onAddedToWorld();
    	
        if (this.world.isRemote && this.posY >= world.getSeaLevel() && this.world.canSeeSky(this.getPosition())) {
			this.world.addWeatherEffect(new EntityLightningBolt(this.world, this.posX, this.posY, this.posZ, true));
			SoundEvent soundEvent = new SoundEvent(new ResourceLocation("entity.lightning.thunder"));
			this.world.playSound(this.posX, this.posY, this.posZ, soundEvent, SoundCategory.AMBIENT, 100.0F, 1.0F, true);
        }
    }

	@Override
    public void onLivingUpdate() {
	
        if (this.world.isRemote) {
            for (int i = 0; i < 2; ++i) {
                this.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, this.posX + (this.rand.nextDouble() - 0.5D) * (double)this.width, this.posY + this.rand.nextDouble() * (double)this.height, this.posZ + (this.rand.nextDouble() - 0.5D) * (double)this.width, 0.0D, 0.0D, 0.0D);
            }
        }
                
        if (!this.world.isRemote) {
        	
        	EntityPlayer closetPlayer = this.world.getNearestPlayerNotCreative(this, FOG_MAX_DISTANCE);
        	
        	// Bat Swarm can only be fired every BATSWARM_DELAY ticks
        	batSwarmTick = Math.max(batSwarmTick - 1, 0);
        	
            if (batSwarmTick == 0) {
            	batSwarmTick = BATSWARM_DELAY;

		        if (closetPlayer != null && this.canEntityBeSeen(closetPlayer) && closetPlayer.getDistance(this) <= BATSWARM_ATTACK_RANGE) {
		        	if (batMinions.isEmpty()) {
		        		for (int i = 0; i < BATSWARM_MINIONS_MAX; i++) {
		        			EntityHostileBat bat = new EntityHostileBat(this.world);
		        			bat.setPosition(this.posX + Math.random() - Math.random(), this.posY + Math.random(), this.posZ + Math.random() - Math.random());
		        			bat.onInitialSpawn(this.getEntityWorld().getDifficultyForLocation(this.getPosition()), null);
		        			
		        			this.world.spawnEntity(bat);
		        			
		        			bat.setIsBatHanging(false);
		        			bat.setBoss(this);
		        			
		        			batMinions.add(bat);
		        		}
		        		playSoundBatSwarm();
		        	}
		        }  
            }
            
	        // Remove any dead bats from the group of batMinions
	        if (!batMinions.isEmpty()) {
	        	for (EntityHostileBat bat : batMinions) {
	        		if (bat.isDead) {
	        			batMinions.remove(bat);
	        			break;
	        		}
	        	}
	        }
	        
	        if (FOG_DOT_ENABLED) {
	        	playersInFogRange = this.world.getEntitiesWithinAABB(EntityPlayer.class, this.getEntityBoundingBox().grow(FOG_MAX_DISTANCE));
				if (playersInFogRange != null) {

					// Warn player when entering poisonous fog.
					for (EntityPlayer player : playersInFogRange) {
						if (FOG_WARNING_ENABLED && !playersWarned.containsKey(player.getName())) {
							player.sendMessage(fogWarningMsg);
							playersWarned.put(player.getName(), true);
						}
					}

					// Reset fog DoT delay
					if (fog_dot_tick == FOG_DOT_DELAY) {
						fog_dot_tick = 0;
					}
					
					// Damage player while inside fog.
					if (fog_dot_tick == 0) {
						for (EntityPlayer player : playersInFogRange) {
							// player.addPotionEffect(new PotionEffect(MobEffects.POISON, FOG_DOT_DELAY, 1, false, true));
							player.attackEntityFrom(DamageSource.GENERIC, FOG_DOT_DAMAGE);
						}
					}

					fog_dot_tick += 1;
				}
				
				else {
					playersWarned.clear();
				}
	        }
        }

        super.onLivingUpdate();
    }
	
	// @SideOnly(Side.CLIENT)
	private void playSoundBatSwarm() {
		if (!world.isRemote)
			this.playSound(SoundHandler.ENTITY_BOSS_BATSWARM, 1.0F, 1.0F);
	}

	@Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        
        // Max out batSwarmTick so no more bats can spawn
        batSwarmTick = BATSWARM_DELAY;

        // Kill all bat minions when boss dies
        if (!batMinions.isEmpty() ) {
	        for (EntityHostileBat bat : batMinions) {
	        	bat.setDead();
	        }
        }
        
        //TODO Is there a better way of handling this?
        FogEventHandler.bossDied = true;
    }
    
    protected SoundEvent getAmbientSound() {
        return SoundHandler.ENTITY_BOSS_IDLE;
    }
    
    protected SoundEvent getDeathSound() {
        return SoundHandler.ENTITY_BOSS_DEATH;
    }
    
    //TODO Add custom loot table
    @Nullable
    protected ResourceLocation getLootTable() {
        return LootTableList.ENTITIES_ZOMBIE;
    }
    
    /* TODO
	public double[] getBossColorTheme() {
		return bossColorTheme;
	}

	public void setBossColorTheme(String[] bossColorTheme) {
		this.bossColorTheme = Arrays.stream(bossColorTheme).mapToDouble(Double::parseDouble).toArray();
	}
	*/
}

package crow.client.mixin.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.authlib.GameProfile;

import crow.client.event.EventTiming;
import crow.client.event.impl.TickEvent;
import crow.client.event.impl.UpdateEvent;
import crow.client.main.Crow;
import crow.client.module.Module;
import crow.client.module.modules.movement.NoSlow;
import crow.client.module.modules.movement.Sprint;
import crow.client.utils.SilentAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

@Mixin(priority = 995, value = EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer {

    @Shadow
    public int sprintingTicksLeft;

    public MixinEntityPlayerSP(World p_i45074_1_, GameProfile p_i45074_2_) {
        super(p_i45074_1_, p_i45074_2_);
    }

    @Override
	@Shadow
    public abstract void setSprinting(boolean p_setSprinting_1_);

    @Shadow
    protected int sprintToggleTimer;
    @Shadow
    public float prevTimeInPortal;
    @Shadow
    public float timeInPortal;
    @Shadow
    protected Minecraft mc;
    @Shadow
    public MovementInput movementInput;

    @Override
	@Shadow
    protected abstract boolean pushOutOfBlocks(double p_pushOutOfBlocks_1_, double p_pushOutOfBlocks_3_,
            double p_pushOutOfBlocks_5_);

    @Override
	@Shadow
    public abstract void sendPlayerAbilities();

    @Shadow
    protected abstract boolean isCurrentViewEntity();

    @Shadow
    public abstract boolean isRidingHorse();

    @Shadow
    private int horseJumpPowerCounter;
    @Shadow
    private float horseJumpPower;

    @Shadow
    protected abstract void sendHorseJump();

    @Shadow
    private boolean serverSprintState;
    @Shadow
    @Final
    public NetHandlerPlayClient sendQueue;

    @Override
	@Shadow
    public abstract boolean isSneaking();

    @Shadow
    private boolean serverSneakState;
    @Shadow
    private double lastReportedPosX;
    @Shadow
    private double lastReportedPosY;
    @Shadow
    private double lastReportedPosZ;
    @Shadow
    private float lastReportedYaw;
    @Shadow
    private float lastReportedPitch;
    @Shadow
    private int positionUpdateTicks;

    @Overwrite
    public void onUpdateWalkingPlayer() {

        Crow.eventBus.post(new TickEvent());

        boolean flag = this.isSprinting();
        if (flag != this.serverSprintState) {
            if (flag)
				this.sendQueue
                        .addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SPRINTING));
			else
				this.sendQueue
                        .addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SPRINTING));

            this.serverSprintState = flag;
        }

        boolean flag1 = this.isSneaking();
        if (flag1 != this.serverSneakState) {
            if (flag1)
				this.sendQueue
                        .addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SNEAKING));
			else
				this.sendQueue
                        .addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SNEAKING));

            this.serverSneakState = flag1;
        }

        if (this.isCurrentViewEntity()) {

            UpdateEvent e = new UpdateEvent(EventTiming.PRE, this.posX, this.getEntityBoundingBox().minY, this.posZ,
                    this.rotationYaw, this.rotationPitch, this.onGround);
            Crow.eventBus.post(e);
            // After every module has been polled, so the packet carries the
            // silent rotation on every tick moveFlying was steered by it —
            // including the glide back to the camera, which no module owns.
            SilentAim.applyToUpdate(e);

            double d0 = e.getX() - this.lastReportedPosX;
            double d1 = e.getY() - this.lastReportedPosY;
            double d2 = e.getZ() - this.lastReportedPosZ;
            double d3 = e.getYaw() - this.lastReportedYaw;
            double d4 = e.getPitch() - this.lastReportedPitch;
            boolean flag2 = (((d0 * d0) + (d1 * d1) + (d2 * d2)) > 9.0E-4D) || (this.positionUpdateTicks >= 20);
            boolean flag3 = (d3 != 0.0D) || (d4 != 0.0D);
            if (this.ridingEntity == null) {
                if (flag2 && flag3)
					this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(e.getX(), e.getY(),
                            e.getZ(), e.getYaw(), e.getPitch(), e.isOnGround()));
				else if (flag2)
					this.sendQueue.addToSendQueue(
                            new C03PacketPlayer.C04PacketPlayerPosition(e.getX(), e.getY(), e.getZ(), e.isOnGround()));
				else if (flag3)
					this.sendQueue.addToSendQueue(
                            new C03PacketPlayer.C05PacketPlayerLook(e.getYaw(), e.getPitch(), e.isOnGround()));
				else
					this.sendQueue.addToSendQueue(new C03PacketPlayer(e.isOnGround()));
            } else {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(this.motionX, -999.0D,
                        this.motionZ, e.getYaw(), e.getPitch(), e.isOnGround()));
                flag2 = false;
            }

            ++this.positionUpdateTicks;
            if (flag2) {
                this.lastReportedPosX = e.getX();
                this.lastReportedPosY = e.getY();
                this.lastReportedPosZ = e.getZ();
                this.positionUpdateTicks = 0;
            }

            if (flag3) {
                this.lastReportedYaw = e.getYaw();
                this.lastReportedPitch = e.getPitch();
            }

            e = UpdateEvent.convertPost(e);
            Crow.eventBus.post(e);

        }

    }

    @Overwrite
    public void sendChatMessage(String message) {
        if (message != null && message.startsWith(".")) {
            String[] args = message.substring(1).split(" ");
            String commandName = args[0];
            String[] commandArgs = new String[args.length - 1];
            System.arraycopy(args, 1, commandArgs, 0, commandArgs.length);
            Crow.commandManager.executeCommand(commandName, commandArgs);
        } else {
            this.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C01PacketChatMessage(message));
        }
    }

    @Override
	@Overwrite
    public void onLivingUpdate() {
        if (this.sprintingTicksLeft > 0) {
            --this.sprintingTicksLeft;
            if (this.sprintingTicksLeft == 0)
				this.setSprinting(false);
        }

        if (this.sprintToggleTimer > 0)
			--this.sprintToggleTimer;

        this.prevTimeInPortal = this.timeInPortal;
        if (this.inPortal) {
            if ((this.mc.currentScreen != null) && !this.mc.currentScreen.doesGuiPauseGame())
				this.mc.displayGuiScreen(null);

            if (this.timeInPortal == 0.0F)
				this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("portal.trigger"),
                        (this.rand.nextFloat() * 0.4F) + 0.8F));

            this.timeInPortal += 0.0125F;
            if (this.timeInPortal >= 1.0F)
				this.timeInPortal = 1.0F;

            this.inPortal = false;
        } else if (this.isPotionActive(Potion.confusion)
                && (this.getActivePotionEffect(Potion.confusion).getDuration() > 60)) {
            this.timeInPortal += 0.006666667F;
            if (this.timeInPortal > 1.0F)
				this.timeInPortal = 1.0F;
        } else {
            if (this.timeInPortal > 0.0F)
				this.timeInPortal -= 0.05F;

            if (this.timeInPortal < 0.0F)
				this.timeInPortal = 0.0F;
        }

        if (this.timeUntilPortal > 0)
			--this.timeUntilPortal;

        Module noSlow = Crow.moduleManager.getModuleByClazz(NoSlow.class);
        Module sprint = Crow.moduleManager.getModuleByClazz(Sprint.class);
        boolean noSlowEnabled = noSlow != null && noSlow.isEnabled() && NoSlow.slowPercent != null;
        boolean sprintEnabled = sprint != null && sprint.isEnabled();
        boolean sprintMultiDir = sprintEnabled && Sprint.multiDir != null && Sprint.multiDir.isToggled();

        boolean flag = this.movementInput.jump;
        boolean flag1 = this.movementInput.sneak;
        float f = 0.8F;
        boolean flag2 = this.movementInput.moveForward >= f;
        this.movementInput.updatePlayerMoveState();
        if (this.isUsingItem() && !this.isRiding()) {

            MovementInput var10000 = this.movementInput;

            if (noSlowEnabled) {
                float slowdown = (float) ((100 - NoSlow.slowPercent.getInput()) / 100F);
                var10000.moveStrafe *= slowdown;
                var10000.moveForward *= slowdown;
            } else {
                var10000.moveStrafe *= 0.2F;
                var10000.moveForward *= 0.2F;
                this.sprintToggleTimer = 0;
            }
        }

        this.pushOutOfBlocks(this.posX - ((double) this.width * 0.35D), this.getEntityBoundingBox().minY + 0.5D,
                this.posZ + ((double) this.width * 0.35D));
        this.pushOutOfBlocks(this.posX - ((double) this.width * 0.35D), this.getEntityBoundingBox().minY + 0.5D,
                this.posZ - ((double) this.width * 0.35D));
        this.pushOutOfBlocks(this.posX + ((double) this.width * 0.35D), this.getEntityBoundingBox().minY + 0.5D,
                this.posZ - ((double) this.width * 0.35D));
        this.pushOutOfBlocks(this.posX + ((double) this.width * 0.35D), this.getEntityBoundingBox().minY + 0.5D,
                this.posZ + ((double) this.width * 0.35D));
        boolean flag3 = ((float) this.getFoodStats().getFoodLevel() > 6.0F) || this.capabilities.allowFlying;
        if (this.onGround && !flag1 && !flag2
                && ((this.movementInput.moveForward >= f) || (sprintMultiDir
                        && ((movementInput.moveForward != 0) || (movementInput.moveStrafe != 0))))
                && !this.isSprinting() && flag3 && (!this.isUsingItem() || noSlowEnabled)
                && !this.isPotionActive(Potion.blindness))
			if ((this.sprintToggleTimer <= 0) && !this.mc.gameSettings.keyBindSprint.isKeyDown())
				this.sprintToggleTimer = 7;
			else
				this.setSprinting(true);

        if (!this.isSprinting()
                && ((this.movementInput.moveForward >= f) || (sprintMultiDir
                        && ((movementInput.moveForward != 0) || (movementInput.moveStrafe != 0))))
                && flag3 && (!this.isUsingItem() || noSlowEnabled)
                && !this.isPotionActive(Potion.blindness)
                && this.mc.gameSettings.keyBindSprint.isKeyDown())
			this.setSprinting(true);

        if (this.isSprinting() && ((sprintMultiDir
                ? !((movementInput.moveForward != 0) || (movementInput.moveStrafe != 0))
                : this.movementInput.moveForward < f) || this.isCollidedHorizontally || !flag3))
			this.setSprinting(false);

        if (this.capabilities.allowFlying)
			if (this.mc.playerController.isSpectatorMode()) {
                if (!this.capabilities.isFlying) {
                    this.capabilities.isFlying = true;
                    this.sendPlayerAbilities();
                }
            } else if (!flag && this.movementInput.jump)
				if (this.flyToggleTimer == 0)
					this.flyToggleTimer = 7;
				else {
                    this.capabilities.isFlying = !this.capabilities.isFlying;
                    this.sendPlayerAbilities();
                    this.flyToggleTimer = 0;
                }

        if (this.capabilities.isFlying && this.isCurrentViewEntity()) {
            if (this.movementInput.sneak)
				this.motionY -= this.capabilities.getFlySpeed() * 3.0F;

            if (this.movementInput.jump)
				this.motionY += this.capabilities.getFlySpeed() * 3.0F;
        }

        if (this.isRidingHorse()) {
            if (this.horseJumpPowerCounter < 0) {
                ++this.horseJumpPowerCounter;
                if (this.horseJumpPowerCounter == 0)
					this.horseJumpPower = 0.0F;
            }

            if (flag && !this.movementInput.jump) {
                this.horseJumpPowerCounter = -10;
                this.sendHorseJump();
            } else if (!flag && this.movementInput.jump) {
                this.horseJumpPowerCounter = 0;
                this.horseJumpPower = 0.0F;
            } else if (flag) {
                ++this.horseJumpPowerCounter;
                if (this.horseJumpPowerCounter < 10)
					this.horseJumpPower = (float) this.horseJumpPowerCounter * 0.1F;
				else
					this.horseJumpPower = 0.8F + ((2.0F / (float) (this.horseJumpPowerCounter - 9)) * 0.1F);
            }
        } else
			this.horseJumpPower = 0.0F;

        // Run the whole movement phase on the yaw we are about to report.
        //
        // moveFlying was already routed through MoveInputEvent, but that is not
        // the only consumer: EntityLivingBase.jump() applies the sprint-jump
        // boost straight off this.rotationYaw (EntityLivingBase:1573-1578), and
        // both jump() and moveEntityWithHeading live inside super.onLivingUpdate.
        // Sprint-jumping under silent aim therefore boosted along the camera yaw
        // while the server rebuilt it from the packet yaw — a ~0.4 motion error
        // on every sprint-jump, which is a guaranteed Simulation offset.
        //
        // Swapping here instead of patching jump() covers every consumer in the
        // phase at once. It also lands rotationYawHead (set from rotationYaw in
        // EntityPlayer.onLivingUpdate) and renderYawOffset on the reported yaw,
        // so the third-person model follows the silent rotation for free.
        SilentAim.beginMovementPhase();
        try {
            super.onLivingUpdate();
        } finally {
            SilentAim.endMovementPhase();
        }
        if (this.onGround && this.capabilities.isFlying && !this.mc.playerController.isSpectatorMode()) {
            this.capabilities.isFlying = false;
            this.sendPlayerAbilities();
        }

    }
}

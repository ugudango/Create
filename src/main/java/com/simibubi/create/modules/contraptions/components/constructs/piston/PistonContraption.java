package com.simibubi.create.modules.contraptions.components.constructs.piston;

import static com.simibubi.create.AllBlocks.MECHANICAL_PISTON_HEAD;
import static com.simibubi.create.AllBlocks.PISTON_POLE;
import static com.simibubi.create.AllBlocks.STICKY_MECHANICAL_PISTON;
import static com.simibubi.create.CreateConfig.parameters;
import static net.minecraft.state.properties.BlockStateProperties.FACING;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.modules.contraptions.components.constructs.Contraption;
import com.simibubi.create.modules.contraptions.components.constructs.piston.MechanicalPistonBlock.PistonState;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.PistonType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.Template.BlockInfo;

public class PistonContraption extends Contraption {

	protected AxisAlignedBB pistonCollisionBox;

	protected int extensionLength;
	protected int initialExtensionProgress;
	protected Direction orientation;

	public static PistonContraption movePistonAt(World world, BlockPos pos, Direction direction, boolean retract) {
		if (isFrozen())
			return null;
		PistonContraption construct = new PistonContraption();
		construct.orientation = direction;
		if (!construct.collectExtensions(world, pos, direction))
			return null;
		if (!construct.searchMovedStructure(world, pos.offset(direction, construct.initialExtensionProgress + 1),
				retract ? direction.getOpposite() : direction))
			return null;
		return construct;
	}

	private boolean collectExtensions(World world, BlockPos pos, Direction direction) {
		List<BlockInfo> poles = new ArrayList<>();
		BlockPos actualStart = pos;
		BlockState nextBlock = world.getBlockState(actualStart.offset(direction));
		int extensionsInFront = 0;
		boolean sticky = STICKY_MECHANICAL_PISTON.typeOf(world.getBlockState(pos));

		if (world.getBlockState(pos).get(MechanicalPistonBlock.STATE) == PistonState.EXTENDED) {
			while (PISTON_POLE.typeOf(nextBlock) && nextBlock.get(FACING).getAxis() == direction.getAxis()
					|| MECHANICAL_PISTON_HEAD.typeOf(nextBlock) && nextBlock.get(FACING) == direction) {

				actualStart = actualStart.offset(direction);
				poles.add(new BlockInfo(actualStart, nextBlock.with(FACING, direction), null));
				extensionsInFront++;
				nextBlock = world.getBlockState(actualStart.offset(direction));

				if (extensionsInFront > parameters.maxPistonPoles.get())
					return false;
			}
		}

		if (extensionsInFront == 0)
			poles.add(
					new BlockInfo(pos,
							MECHANICAL_PISTON_HEAD.get().getDefaultState().with(FACING, direction).with(
									BlockStateProperties.PISTON_TYPE, sticky ? PistonType.STICKY : PistonType.DEFAULT),
							null));
		else
			poles.add(new BlockInfo(pos, PISTON_POLE.get().getDefaultState().with(FACING, direction), null));

		BlockPos end = pos;
		nextBlock = world.getBlockState(end.offset(direction.getOpposite()));
		int extensionsInBack = 0;

		while (PISTON_POLE.typeOf(nextBlock)) {
			end = end.offset(direction.getOpposite());
			poles.add(new BlockInfo(end, nextBlock.with(FACING, direction), null));
			extensionsInBack++;
			nextBlock = world.getBlockState(end.offset(direction.getOpposite()));

			if (extensionsInFront + extensionsInBack > parameters.maxPistonPoles.get())
				return false;
		}

		extensionLength = extensionsInBack + extensionsInFront;
		initialExtensionProgress = extensionsInFront;
		pistonCollisionBox = new AxisAlignedBB(end.offset(direction, -extensionsInFront));

		for (BlockInfo pole : poles) {
			BlockPos polePos = pole.pos.offset(direction, -extensionsInFront);
			blocks.put(polePos, new BlockInfo(polePos, pole.state, null));
			pistonCollisionBox = pistonCollisionBox.union(new AxisAlignedBB(polePos));
		}

		constructCollisionBox = new AxisAlignedBB(pos.offset(direction, initialExtensionProgress));
		return true;
	}

	@Override
	protected boolean addToInitialFrontier(World world, BlockPos pos, Direction direction, List<BlockPos> frontier) {
		for (int offset = 1; offset <= parameters.maxChassisRange.get(); offset++) {
			BlockPos currentPos = pos.offset(direction, offset);
			if (!world.isAreaLoaded(currentPos, 1))
				return false;
			if (!world.isBlockPresent(currentPos))
				break;
			BlockState state = world.getBlockState(currentPos);
			if (state.getMaterial().isReplaceable())
				break;
			if (state.getCollisionShape(world, currentPos).isEmpty())
				break;
			if (AllBlocks.MECHANICAL_PISTON_HEAD.typeOf(state) && state.get(FACING) == direction.getOpposite())
				break;
			if (!canPush(world, currentPos, direction))
				return false;
			frontier.add(currentPos);
		}
		return true;
	}

	protected void add(BlockPos pos, BlockInfo block) {
		super.add(pos.offset(orientation, -initialExtensionProgress), block);
	}

	@Override
	public void readNBT(CompoundNBT nbt) {
		super.readNBT(nbt);
		extensionLength = nbt.getInt("ExtensionLength");
		if (nbt.contains("BoundsBack"))
			pistonCollisionBox = readAABB(nbt.getList("BoundsBack", 5));
	}

	@Override
	public CompoundNBT writeNBT() {
		CompoundNBT nbt = super.writeNBT();

		if (pistonCollisionBox != null) {
			ListNBT bb = writeAABB(pistonCollisionBox);
			nbt.put("BoundsBack", bb);
		}
		nbt.putInt("ExtensionLength", extensionLength);

		return nbt;
	}

	public AxisAlignedBB getCollisionBoxBack() {
		return pistonCollisionBox;
	}
}
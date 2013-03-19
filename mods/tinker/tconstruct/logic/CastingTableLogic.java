package mods.tinker.tconstruct.logic;

import mods.tinker.common.IPattern;
import mods.tinker.common.InventoryLogic;
import mods.tinker.tconstruct.TConstruct;
import mods.tinker.tconstruct.crafting.CastingRecipe;
import mods.tinker.tconstruct.crafting.LiquidCasting;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.liquids.ILiquidTank;
import net.minecraftforge.liquids.ITankContainer;
import net.minecraftforge.liquids.LiquidStack;

public class CastingTableLogic extends InventoryLogic implements ILiquidTank, ITankContainer
{
	public LiquidStack liquid;
	float materialRedux = 0;
	int castingDelay = 0;
	int renderOffset = 0;

	public CastingTableLogic()
	{
		super(2);
	}

	@Override
	public String getInvName () //Not a gui block
	{
		return null;
	}

	@Override
	protected String getDefaultName () //Still not a gui block
	{
		return null;
	}

	@Override
	public Container getGuiContainer (InventoryPlayer inventoryplayer, World world, int x, int y, int z) //Definitely not a gui block
	{
		return null;
	}

	/* Tank */
	@Override
	public LiquidStack getLiquid ()
	{
		return liquid;
	}

	@Override
	public int getCapacity ()
	{
		int ret = TConstruct.ingotLiquidValue;
		if (inventory[0] != null && inventory[0].getItem() instanceof IPattern)
			ret *= ((IPattern) inventory[0].getItem()).getPatternCost(inventory[0].getItemDamage()) * 0.5;
		if (materialRedux > 0)
			ret *= materialRedux;
		return ret;
	}

	@Override
	public int fill (LiquidStack resource, boolean doFill)
	{
		if (resource == null)
			return 0;

		if (liquid == null)
		{
			if (inventory[1] == null && LiquidCasting.instance.getCastingRecipe(resource, inventory[0]) != null)
			{
				liquid = resource.copy();
				materialRedux = LiquidCasting.instance.getMaterialReduction(resource);
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				renderOffset = liquid.amount;
				return liquid.amount;
			}
			else
				return 0;
		}
		else if (resource.itemID != liquid.itemID || resource.itemMeta != liquid.itemMeta)
		{
			return 0;
		}
		else if (resource.amount + liquid.amount >= getCapacity()) //Start timer here
		{
			int total = getCapacity();
			int cap = total - liquid.amount;
			if (doFill && cap != total)
			{
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				liquid.amount = getCapacity();
				if (castingDelay <= 0)
				{
					castingDelay = LiquidCasting.instance.getCastingDelay(liquid, inventory[0]);
				}
			}
			renderOffset = cap;
			return cap;
		}
		else
		{
			if (doFill)
			{
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				liquid.amount += resource.amount;
			}
			return resource.amount;
		}
	}

	@Override
	public LiquidStack drain (int maxDrain, boolean doDrain) //Doesn't actually drain
	{
		return null;
	}

	@Override
	public int getTankPressure ()
	{
		return 0;
	}

	/* Tank Container */

	@Override
	public int fill (ForgeDirection from, LiquidStack resource, boolean doFill)
	{
		if (from == ForgeDirection.UP)
			return fill(0, resource, doFill);
		return 0;
	}

	@Override
	public int fill (int tankIndex, LiquidStack resource, boolean doFill)
	{
		return fill(resource, doFill);
	}

	@Override
	public LiquidStack drain (ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public LiquidStack drain (int tankIndex, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public ILiquidTank[] getTanks (ForgeDirection direction)
	{
		return new ILiquidTank[] { this };
	}

	@Override
	public ILiquidTank getTank (ForgeDirection direction, LiquidStack type)
	{
		return this;
	}
	
	public int getLiquidAmount()
	{
		return liquid.amount - renderOffset;
	}

	/* Updating */
	@Override
	public void updateEntity ()
	{
		if (castingDelay > 0)
		{
			castingDelay--;
			if (castingDelay == 0)
				castLiquid();
		}
		if (renderOffset > 0)
		{
			renderOffset -= 6;
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
		}
	}

	public void castLiquid ()
	{
		CastingRecipe recipe = LiquidCasting.instance.getCastingRecipe(liquid, inventory[0]);
		if (recipe != null)
		{
			materialRedux = 0;
			inventory[1] = recipe.getResult();
			if (recipe.consumeCast)
				inventory[0] = null;
			liquid = null;
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	/* NBT */

	@Override
	public void readFromNBT (NBTTagCompound tags)
	{
		super.readFromNBT(tags);
		readCustomNBT(tags);
	}

	public void readCustomNBT (NBTTagCompound tags)
	{
		if (tags.getBoolean("hasLiquid"))
			this.liquid = new LiquidStack(tags.getInteger("itemID"), tags.getInteger("amount"), tags.getInteger("itemMeta"));
		else
			this.liquid = null;
	}

	@Override
	public void writeToNBT (NBTTagCompound tags)
	{
		super.writeToNBT(tags);
		writeCustomNBT(tags);
	}

	public void writeCustomNBT (NBTTagCompound tags)
	{
		tags.setBoolean("hasLiquid", liquid != null);
		if (liquid != null)
		{
			tags.setInteger("itemID", liquid.itemID);
			tags.setInteger("amount", liquid.amount);
			tags.setInteger("itemMeta", liquid.itemMeta);
		}
	}

	/* Packets */
	@Override
	public Packet getDescriptionPacket ()
	{
		NBTTagCompound tag = new NBTTagCompound();
		writeToNBT(tag);
		return new Packet132TileEntityData(xCoord, yCoord, zCoord, 1, tag);
	}

	@Override
	public void onDataPacket (INetworkManager net, Packet132TileEntityData packet)
	{
		readFromNBT(packet.customParam1);
		worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
	}

}

package net.runelite.mixins;

import net.runelite.api.mixins.Inject;
import net.runelite.api.mixins.Mixin;
import net.runelite.rs.api.RSRunException;

@Mixin(RSRunException.class)
public abstract class RSRunExceptionMixin extends RuntimeException implements RSRunException
{
	@Override
	@Inject
	public Throwable getCause()
	{
		return getParent();
	}
}

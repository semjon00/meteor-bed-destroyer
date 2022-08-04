package semjon00.meteorbeddestroyer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import semjon00.meteorbeddestroyer.modules.*;

import java.lang.invoke.MethodHandles;
import java.time.Instant;

public class MeteorBedDestroyerAddon extends MeteorAddon {
	public static final Logger LOGGER = LoggerFactory.getLogger("Meteor Bed Destroyer");
	public static final Category CATEGORY = new Category("BedWars", Items.RED_BED.getDefaultStack());

	@Override
	public void onInitialize() {
		LOGGER.info("Bed Destroyer is enabled! Make these pesky cheaters pay!");

		// No idea what this does. Some trickery to capture events with our code?
		MeteorClient.EVENT_BUS.registerLambdaFactory("semjon00.meteorbeddestroyer",
				(lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(
						null, klass, MethodHandles.lookup()));

		Modules modules = Modules.get();
		modules.add(new BedDestroyer());
	}

	@Override
	public void onRegisterCategories() {
		Modules.registerCategory(CATEGORY);
	}

	public static boolean shouldDebug = false;

	public static void debugLog(String msg, Object... objs) {
		if (!shouldDebug) return;
		// We do not use debug level, because it is such a pain to set up
		var timedMsg = Instant.now().toString().split("T")[1] + ": " + msg;
		LOGGER.info(timedMsg, objs);
	}
}

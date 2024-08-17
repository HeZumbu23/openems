package io.openems.edge.pvinverter.growatt;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "PV-Inverter Growatt", //
		description = "Implements the Growatt PV inverter.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "pvInverter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Growatt Cloud API Email", description = "Your email address to log in into Growatt Cloud API")
	String email() default "";

	@AttributeDefinition(name = "Growatt Cloud API Password", description = "Your password to log in into Growatt Cloud API")
	String password() default "";

	@AttributeDefinition(name = "Growatt plant ID", description = "The plantId of your plant. You can find it when logging in into Growatt Cloud")
	String plantId() default "";

	String webconsole_configurationFactory_nameHint() default "PV-Inverter Growatt [{id}]";

}
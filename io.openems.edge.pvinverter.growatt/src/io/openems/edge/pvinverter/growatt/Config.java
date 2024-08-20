package io.openems.edge.pvinverter.growatt;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
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

	@AttributeDefinition(name = "Growatt Cloud API Password", description = "Your password to log in into Growatt Cloud API", type = AttributeType.PASSWORD)
	String password() default "";

	@AttributeDefinition(name = "Growatt plant index", description = "If you have only one plant, keep this value empty. If you have multiple plants, specify the plant index (starting with 0) of the plant you want to use for this inverter instance.")
	int plantIndex() default 0;

	String webconsole_configurationFactory_nameHint() default "PV-Inverter Growatt [{id}]";

}
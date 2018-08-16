package io.openems.edge.fenecon.mini.ess;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.EventConstants;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.fenecon.mini.core.api.FeneconMiniCore;

@Designate(ocd = Config.class, factory = true)
@Component( //
		name = "Fenecon.Mini.Ess", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = EventConstants.EVENT_TOPIC + "=" + EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE //
)
public class FeneconMiniEss extends AbstractOpenemsComponent implements SymmetricEss, OpenemsComponent {

	@Reference
	protected ConfigurationAdmin cm;

	private AtomicReference<FeneconMiniCore> core = new AtomicReference<>();

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setCore(FeneconMiniCore core) {
		this.core.set(core);
		core.setEss(this);
	}

	protected void unsetCore(FeneconMiniCore core) {
		this.core.compareAndSet(core, null);
		core.unsetEss(this);
	}

	public FeneconMiniEss() {
		Utils.initializeChannels(this).forEach(channel -> this.addChannel(channel));
	}

	@Activate
	void activate(ComponentContext context, Config config) {
		super.activate(context, config.service_pid(), config.id(), config.enabled());
		// update filter for 'Core'
		if (OpenemsComponent.updateReferenceFilter(cm, config.service_pid(), "Core", config.core_id())) {
			return;
		}
	}

	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}
}

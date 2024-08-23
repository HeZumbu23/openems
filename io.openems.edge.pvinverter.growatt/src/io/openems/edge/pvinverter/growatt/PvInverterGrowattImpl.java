package io.openems.edge.pvinverter.growatt;

import java.io.IOException;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import com.google.gson.JsonObject;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.api.MeterType;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "PV-Inverter.Growatt", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE, //
		property = { //
				"type=PRODUCTION" //
		})
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE
})
public class PvInverterGrowattImpl extends AbstractOpenemsComponent implements PvInverterGrowatt, ManagedSymmetricPvInverter, 
		ElectricityMeter, OpenemsComponent, EventHandler {

	private final Logger log = LoggerFactory.getLogger(PvInverterGrowattImpl.class);
	
	@Reference
	private ConfigurationAdmin cm;
	
	private GrowattApi api;
		
	public PvInverterGrowattImpl() {
		super(
				OpenemsComponent.ChannelId.values(), //				
				ElectricityMeter.ChannelId.values(), //
				ManagedSymmetricPvInverter.ChannelId.values(), //				
				PvInverterGrowatt.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException, IOException, InterruptedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
							
		this.api = new GrowattApi(config.email(), config.password(), config.plantIndex());        
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		
		if (!this.isEnabled()) {
			return;
		}
		
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
		
			double power = -1;
			try {
				power = this.api.getPowerOfPlant();
				this.channel(PvInverterGrowatt.ChannelId.GROWATT_API_FAILED).setNextValue(false);
	        }
			catch(GrowattApiException ex) {
				this.channel(PvInverterGrowatt.ChannelId.GROWATT_API_FAILED).setNextValue(true);
				this.log.error("Could not get power of plant from Growatt API: " + ex.getMessage());				
				return;		
			}
			catch(Exception ex) {
				this.channel(PvInverterGrowatt.ChannelId.GROWATT_API_FAILED).setNextValue(true);
				this.log.error("Unexpected exception when getting power of plant from Growatt API: " + ex.getMessage());
				return;
			}	
			
			int roundedPower = (int) Math.round(power);
			this._setActivePower(roundedPower);
			this.channel(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY).setNextValue(roundedPower);
			this.channel(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY).setNextValue(0);
			break;		
		}
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.PRODUCTION;
	}
	
	@Override
	public String debugLog() {
		return this.getActivePower().asString();
	}
	
	@Override
	public void retryModbusCommunication() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode), //
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
	}
}
package ru.iris.noolite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.bus.Event;
import reactor.bus.EventBus;
import reactor.fn.Consumer;
import ru.iris.commons.bus.devices.DeviceChangeEvent;
import ru.iris.commons.bus.devices.DeviceProtocolEvent;
import ru.iris.commons.config.ConfigLoader;
import ru.iris.commons.protocol.ProtocolServiceLayer;
import ru.iris.commons.protocol.enums.DeviceType;
import ru.iris.commons.protocol.enums.SourceProtocol;
import ru.iris.commons.protocol.enums.State;
import ru.iris.commons.protocol.enums.ValueType;
import ru.iris.commons.registry.DeviceRegistry;
import ru.iris.commons.service.AbstractProtocolService;
import ru.iris.noolite.protocol.model.NooliteDevice;
import ru.iris.noolite.protocol.model.NooliteDeviceValue;
import ru.iris.noolite4j.receiver.RX2164;
import ru.iris.noolite4j.watchers.BatteryState;
import ru.iris.noolite4j.watchers.Notification;
import ru.iris.noolite4j.watchers.SensorType;
import ru.iris.noolite4j.watchers.Watcher;

@Component
@Profile("noolite")
@Qualifier("nooliterx")
@Scope("singleton")
public class NooliteRXController extends AbstractProtocolService<NooliteDevice> {

	private final EventBus r;
	private final ConfigLoader config;
	private final DeviceRegistry registry;
	private final ProtocolServiceLayer<NooliteDevice, NooliteDeviceValue> service;
	private RX2164 rx;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	public NooliteRXController(@Qualifier("nooliteDeviceService") ProtocolServiceLayer service,
	                           EventBus r,
	                           ConfigLoader config,
	                           DeviceRegistry registry) {
		this.service = service;
		this.r = r;
		this.config = config;
		this.registry = registry;
	}

	@Override
	public void onStartup() {
		logger.info("NooliteRXController started");
		if(!config.loadPropertiesFormCfgDirectory("noolite"))
			logger.error("Cant load noolite-specific configs. Check noolite.property if exists");
	}

	@Override
	public void onShutdown() {
		logger.info("NooliteRXController stopping");
		logger.info("Saving Noolite devices state into database");
		service.saveIntoDatabase();
		logger.info("Saved");
	}

	@Override
	public void subscribe() throws Exception  {
		addSubscription("command.device.noolite.rx");
		addSubscription("event.device.noolite.rx");
	}

	@Override
	public NooliteDevice getDeviceByChannel(Short channel) {
		return (NooliteDevice) registry.getDevice(SourceProtocol.NOOLITE, channel);
	}

	@Override
	public Consumer<Event<?>> handleMessage() {
		return event -> {
			if (event.getData() instanceof DeviceProtocolEvent) {
				DeviceProtocolEvent n = (DeviceProtocolEvent) event.getData();

				switch (n.getLabel()) {
					case "BindRX":
						logger.debug("Get BindRXChannel advertisement (channel {})", n.getChannel());
						logger.info("Binding device to RX channel {}", n.getChannel());
						rx.bindChannel(n.getChannel().byteValue());
						break;
					case "UnbindRX":
						logger.debug("Get UnbindRXChannel advertisement (channel {})", n.getChannel());
						logger.info("Unbinding device from RX channel {}", n.getChannel());
						rx.unbindChannel(n.getChannel().byteValue());
						break;
					case "UnbindAllRX":
						logger.debug("Get UnbindAllRXChannel advertisement");
						logger.info("Unbinding all RX channels");
						rx.unbindAllChannels();
						break;
					default:
						break;
				}
			} else if (event.getData() instanceof DeviceChangeEvent) {

				DeviceChangeEvent n = (DeviceChangeEvent) event.getData();
				logger.debug("Get ValueChange advertisement (channel {}, from: {}, to: {})", n.getChannel(), n.getFrom(), n.getTo());
				logger.info("Change device value event from TX, channel {}", n.getChannel());

				NooliteDevice device = getDeviceByChannel(n.getChannel());

				if (device != null && device.getDeviceValues().get("level") != null) {
					device.getDeviceValues().get("level").setCurrentValue(n.getTo());
					registry.addOrUpdateDevice(device);
				}
			} else {
				// We received unknown request message. Lets make generic log entry.
				logger.info("Received unknown request for nooliterx service! Class: {}", event.getData().getClass());
			}
		};
	}

	@Override
	@Async
	public void run() {

		try {
			rx = new RX2164();
			rx.open();

			Watcher watcher = this::doWork;

			rx.addWatcher(watcher);
			rx.start();
		} catch (Throwable t) {
			logger.error("Noolite RX error!");
			t.printStackTrace();
		}
	}

	private void doWork(Notification notification) {

		boolean isNew = false;
		Short channel = (short) notification.getChannel();
		SensorType sensor = (SensorType) notification.getValue("sensortype");

		logger.debug("Message to RX from channel " + channel);

		NooliteDevice device = getDeviceByChannel(channel);

		if (device == null) {
			device = new NooliteDevice();
			device.setSource(SourceProtocol.NOOLITE);
			device.setHumanReadable("noolite/channel/" + channel);
			device.setState(State.ACTIVE);
			device.setType(DeviceType.UNKNOWN);
			device.setManufacturer("Nootechnika");
			device.setChannel(channel);

			// device is sensor
			if (sensor != null) {
				switch (sensor) {
					case PT111:
						device.setType(DeviceType.TEMP_HUMI_SENSOR);
						device.setProductName("PT111");
						break;
					case PT112:
						device.setType(DeviceType.TEMP_SENSOR);
						device.setProductName("PT112");
						break;
					default:
						device.setType(DeviceType.UNKNOWN_SENSOR);
				}
			}

			isNew = true;
			device = service.saveIntoDatabase(device);
		}

		// turn off
		switch (notification.getType()) {
			case TURN_OFF:
				logger.info("Channel {}: Got OFF command", channel);
				service.updateValue(device, "level", 0, ValueType.BYTE);

				// device product name unkown
				if (device.getProductName().isEmpty()) {
					device.setProductName("Generic Switch");
					device.setType(DeviceType.BINARY_SWITCH);
				}

				broadcast("event.device.noolite.off", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "level", 0, ValueType.INT));
				break;

			case SLOW_TURN_OFF:
				logger.info("Channel {}: Got DIM command", channel);
				// we only know, that the user hold OFF button
				service.updateValue(device, "level", 0, ValueType.INT);

				broadcast("event.device.noolite.dim", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "level", 0, ValueType.INT));
				break;

			case TURN_ON:
				logger.info("Channel {}: Got ON command", channel);
				service.updateValue(device, "level", 255, ValueType.INT);

				// device product name unkown
				if (device.getType().equals(DeviceType.UNKNOWN)) {
					device.setProductName("Generic Switch");
					device.setType(DeviceType.BINARY_SWITCH);
				}

				broadcast("event.device.noolite.on", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "level", 255, ValueType.INT));
				break;

			case SLOW_TURN_ON:
				logger.info("Channel {}: Got BRIGHT command", channel);
				// we only know, that the user hold ON button
				service.updateValue(device, "level", 255, ValueType.INT);

				broadcast("event.device.noolite.bright", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "level", 255, ValueType.INT));
				break;

			case SET_LEVEL:
				logger.info("Channel {}: Got SETLEVEL command.", channel);
				service.updateValue(device, "level", notification.getValue("level"), ValueType.INT);

				// device product name unkown
				if (device.getProductName().isEmpty() || device.getType().equals(DeviceType.BINARY_SWITCH)) {
					device.setProductName("Generic Dimmer");
					device.setType(DeviceType.MULTILEVEL_SWITCH);
				}

				broadcast("event.device.noolite.setlevel", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "level", notification.getValue("level"), ValueType.INT));
				break;

			case STOP_DIM_BRIGHT:
				logger.info("Channel {}: Got STOPDIMBRIGHT command.", channel);

				broadcast("event.device.noolite.stopdimbright", new DeviceProtocolEvent(channel, SourceProtocol.NOOLITE, "StopDimBright"));
				break;

			case TEMP_HUMI:
				BatteryState battery = (BatteryState) notification.getValue("battery");
				logger.info("Channel {}: Got TEMP_HUMI command.", channel);

				ru.iris.commons.protocol.enums.BatteryState batteryState;

				switch (battery) {
					case OK:
						batteryState = ru.iris.commons.protocol.enums.BatteryState.OK;
						break;
					case REPLACE:
						batteryState = ru.iris.commons.protocol.enums.BatteryState.LOW;
						break;
					default:
						batteryState = ru.iris.commons.protocol.enums.BatteryState.UNKNOWN;
				}

				service.updateValue(device, "temperature", notification.getValue("temp"), ValueType.DOUBLE);
				service.updateValue(device, "humidity", notification.getValue("humi"), ValueType.BYTE);
				service.updateValue(device, "battery", batteryState, ValueType.STRING);

				broadcast("event.device.noolite.temperature", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "temperature", notification.getValue("temp"), ValueType.DOUBLE));
				broadcast("event.device.noolite.humidity", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "humidity", notification.getValue("humi"), ValueType.BYTE));
				broadcast("event.device.noolite.battery", new DeviceChangeEvent(channel, SourceProtocol.NOOLITE, "battery", batteryState, ValueType.STRING));

				break;

			case BATTERY_LOW:
				logger.info("Channel {}: Got BATTERYLOW command.", channel);

				if (device.getType().equals(DeviceType.BINARY_SWITCH)) {
					device.setType(DeviceType.MOTION_SENSOR);
					device.setProductName("PM111");
				}

				broadcast("event.device.noolite.battery", new DeviceProtocolEvent(channel, SourceProtocol.NOOLITE, "BatteryLow"));
				break;

			default:
				logger.info("Unknown command: {}", notification.getType().name());
		}

		// save/replace device in devices pool
		if (isNew)
			broadcast("event.device.noolite.added", new DeviceProtocolEvent(channel, SourceProtocol.NOOLITE, "NooliteDeviceAdded"));

		registry.addOrUpdateDevice(device);
	}
}

package ru.iris.zwave.protocol.events;

import ru.iris.commons.bus.AbstractEvent;

public class ZWaveSetValue extends AbstractEvent {

	private Byte node;
	private String label;
	private String value;

	public ZWaveSetValue(Byte node, String label, String value) {
		this.node = node;
		this.label = label;
		this.value = value;
	}

	public Byte getNode() {
		return node;
	}

	public void setNode(Byte node) {
		this.node = node;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "ZWaveSetValue{" +
				"node=" + node +
				", label='" + label + '\'' +
				", value=" + value +
				'}';
	}
}
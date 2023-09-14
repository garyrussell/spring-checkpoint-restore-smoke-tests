package com.example.kafka;

import org.springframework.context.ApplicationEvent;

public class CheckpointReadyEvent extends ApplicationEvent {

	public CheckpointReadyEvent(Object source) {
		super(source);
	}

}

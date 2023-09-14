package com.example.kafka;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@SpringBootApplication
@EnableScheduling
public class KafkaApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaApplication.class, args);
	}

	@Bean
	NewTopic topic() {
		return TopicBuilder.name("crac").partitions(1).replicas(1).build();
	}

	public static class Greeting {

		private final String message;

		@JsonCreator
		public Greeting(@JsonProperty("message") String message) {
			this.message = message;
		}

		public String getMessage() {
			return this.message;
		}

		@Override
		public String toString() {
			return "Greeting{" + "message='" + this.message + '\'' + '}';
		}

	}

}

@Component
class SenderAndReceiver implements SmartLifecycle {

	private final CountDownLatch latch = new CountDownLatch(1);

	private final ApplicationEventPublisher publisher;

	private final KafkaTemplate<Object, Object> template;

	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	private final Semaphore taskCompletionSemaphore = new Semaphore(1);

	private final AtomicBoolean first = new AtomicBoolean(true);

	SenderAndReceiver(ApplicationEventPublisher publisher, KafkaTemplate<Object, Object> template) {
		this.publisher = publisher;
		this.template = template;
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
	public void doSendMessage() {
		this.taskCompletionSemaphore.acquireUninterruptibly();
		if (this.isRunning.get()) {
			KafkaApplication.Greeting data = new KafkaApplication.Greeting(
					"Hello from Coordinated Restore at Checkpoint!");
			template.send("crac", data);
			System.out.println("++++++Sent: " + data);
			try {
				this.latch.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			System.out.println(this.first.get());
			if (this.first.getAndSet(false)) {
				System.out.println("publishing");
				System.out.println(this.first.get());
				publisher.publishEvent(new CheckpointReadyEvent(this));
			}
		}
		this.taskCompletionSemaphore.release();

	}

	@KafkaListener(id = "crac", topics = "crac")
	void listen(KafkaApplication.Greeting in) {
		System.out.println("++++++Received: " + in);
		this.latch.countDown();
	}

	// Use the SmartLifecycle to synch the message sending with the checkpoint/restore.
	@Override
	public void start() {
		this.isRunning.set(true);
	}

	@Override
	public void stop() {
		this.isRunning.set(false);
		// wait until the running task complete.
		this.taskCompletionSemaphore.acquireUninterruptibly();
		this.taskCompletionSemaphore.release();
	}

	@Override
	public boolean isRunning() {
		return this.isRunning.get();
	}

}

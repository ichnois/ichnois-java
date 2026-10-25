package io.ichnois.samples.console.kafka;

import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import io.ichnois.kafka.IchnoisCoreKafkaConsumerTracing;
import io.ichnois.kafka.IchnoisCoreKafkaProducer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaShowcase {

  public static final String TOPIC = "ichnois-topic-console-sample";

  private KafkaShowcase() {}

  public static void runKafkaWithIchnoisCoreTracing(final String bootstrapServers) {
    final CountDownLatch consumedLatch = new CountDownLatch(1);
    final Thread consumerThread = startConsumerWithIchnoisCoreTracing(bootstrapServers, consumedLatch);
    final Properties producerProperties = createProducerProperties(bootstrapServers);

    final ITransaction transaction = IchnoisCore.startTransaction("kafka-demo", "demo");
    try (IIchnoisCoreLifecycleToken ignored = transaction.makeCurrent()) {
      // 1. Create the raw Kafka producer as you normally would.
      final KafkaProducer<String, String> rawProducer = new KafkaProducer<>(producerProperties);

      // 2. >>> IchnoisCore instrumentation <<<
      //    Wrap it with IchnoisCoreKafkaProducer.wrap() so every send is captured as a
      //    `queue.publish` span that closes when the broker ack callback fires.
      final Producer<String, String> producer = IchnoisCoreKafkaProducer.wrap(rawProducer);

      try (producer) {
        Thread.sleep(500);
        producer.send(new ProducerRecord<>(TOPIC, "ichnois-kafka sample message")).get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignoredException) {
        // local broker may not be available when running the sample
      }

      try {
        consumedLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } finally {
      consumerThread.interrupt();
      try {
        consumerThread.join(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      transaction.finish();
    }
  }

  public static Properties createProducerProperties(final String bootstrapServers) {
    final Properties producerProperties = new Properties();
    producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    producerProperties.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProperties.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

    // Optional tuning for sample stability in CI/local runs.
    producerProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
    producerProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
    producerProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 3000);

    return producerProperties;
  }

  public static Properties createConsumerProperties(final String bootstrapServers) {
    final Properties consumerProperties = new Properties();
    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    consumerProperties.put(
        ConsumerConfig.GROUP_ID_CONFIG, "ichnois-console-sample-" + UUID.randomUUID());
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProperties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProperties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    // Optional tuning for sample stability in CI/local runs.
    consumerProperties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
    consumerProperties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);

    return consumerProperties;
  }

  private static Thread startConsumerWithIchnoisCoreTracing(
      final String bootstrapServers, final CountDownLatch consumedLatch) {
    final Thread consumerThread =
        new Thread(
            () -> {
              final Properties consumerProperties = createConsumerProperties(bootstrapServers);

              try (KafkaConsumer<String, String> consumer =
                  new KafkaConsumer<>(consumerProperties)) {
                consumer.subscribe(Collections.singletonList(TOPIC));

                while (!Thread.currentThread().isInterrupted() && consumedLatch.getCount() > 0) {
                  final ConsumerRecords<String, String> records =
                      consumer.poll(Duration.ofMillis(500));
                  for (final ConsumerRecord<String, String> record : records) {
                    IchnoisCoreKafkaConsumerTracing.withTracing(
                        record,
                        () -> {
                          System.out.println(
                              "Consumed Kafka message from "
                                  + record.topic()
                                  + ": "
                                  + record.value());
                          consumedLatch.countDown();
                        });
                    if (consumedLatch.getCount() == 0) {
                      break;
                    }
                  }
                }
              } catch (Exception ignored) {
                // local broker may not be available when running the sample
              }
            },
            "ichnois-kafka-sample-consumer");
    consumerThread.start();
    return consumerThread;
  }
}

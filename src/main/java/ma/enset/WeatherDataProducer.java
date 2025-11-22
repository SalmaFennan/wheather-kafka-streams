package ma.enset;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.Random;

public class WeatherDataProducer {

    public static void main(String[] args) throws InterruptedException {

        // Configuration du producer Kafka
        Properties prop = new Properties();
        prop.put("bootstrap.servers", "localhost:9092");
        prop.put("key.serializer", StringSerializer.class.getName());
        prop.put("value.serializer", StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);
        Random random = new Random();

        String[] stations = {"Station1", "Station2", "Station3", "Station4"};

        System.out.println("Envoi de données météo vers le topic 'weather-data'...");

        try {
            // Envoi de données en continu
            for (int i = 0; i < 100; i++) {
                String station = stations[random.nextInt(stations.length)];

                // Génération de températures entre 20°C et 45°C
                double temperature = 20 + (random.nextDouble() * 25);

                // Génération d'humidité entre 30% et 90%
                int humidity = 30 + random.nextInt(61);

                String message = String.format("%s,%.1f,%d", station, temperature, humidity);

                ProducerRecord<String, String> record =
                        new ProducerRecord<>("weather-data", station, message);

                producer.send(record, (metadata, exception) -> {
                    if (exception == null) {
                        System.out.println("Envoyé: " + message +
                                " | Partition: " + metadata.partition() +
                                " | Offset: " + metadata.offset());
                    } else {
                        System.err.println("Erreur: " + exception.getMessage());
                    }
                });

                // Attendre 2 secondes entre chaque envoi
                Thread.sleep(2000);
            }
        } finally {
            producer.close();
            System.out.println("Producer fermé.");
        }
    }
}
package ma.enset;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;

import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import java.io.IOException;
import java.util.Properties;

public class WeatherAnalysisApp {

    // Métriques Prometheus pour température et humidité moyennes par station
    private static final Gauge avgTemperatureGauge = Gauge.build()
            .name("station_avg_temperature_fahrenheit")
            .help("Température moyenne par station en Fahrenheit")
            .labelNames("station")
            .register();

    private static final Gauge avgHumidityGauge = Gauge.build()
            .name("station_avg_humidity_percent")
            .help("Humidité moyenne par station en pourcentage")
            .labelNames("station")
            .register();

    // Classe pour stocker les valeurs cumulées
    static class WeatherAggregate {
        double totalTemp;
        double totalHumidity;
        long count;

        public WeatherAggregate() {
            this.totalTemp = 0.0;
            this.totalHumidity = 0.0;
            this.count = 0;
        }

        public WeatherAggregate(double totalTemp, double totalHumidity, long count) {
            this.totalTemp = totalTemp;
            this.totalHumidity = totalHumidity;
            this.count = count;
        }
    }

    public static void main(String[] args) throws IOException {

        // ========= Configuration Kafka Streams =========
        Properties prop = new Properties();
        prop.put("bootstrap.servers", "localhost:9092");
        prop.put("application.id", "weather-analysis-app");
        prop.put("default.key.serde", Serdes.String().getClass().getName());
        prop.put("default.value.serde", Serdes.String().getClass().getName());

        StreamsBuilder builder = new StreamsBuilder();

        // 1. Lire les données météorologiques depuis 'weather-data'
        KStream<String, String> weatherData = builder.stream("weather-data");

        // 2. Filtrer les données avec température > 30°C
        KStream<String, String> highTempData = weatherData.filter((key, value) -> {
            if (value == null || value.isEmpty()) return false;
            String[] parts = value.split(",");
            if (parts.length < 3) return false;
            try {
                double temperature = Double.parseDouble(parts[1]);
                return temperature > 30.0;
            } catch (NumberFormatException e) {
                return false;
            }
        });

        // 3. Convertir température en Fahrenheit
        KStream<String, String> fahrenheitData = highTempData.mapValues(value -> {
            String[] parts = value.split(",");
            String station = parts[0];
            double celsius = Double.parseDouble(parts[1]);
            double humidity = Double.parseDouble(parts[2]);

            // Conversion Celsius -> Fahrenheit
            double fahrenheit = (celsius * 9.0 / 5.0) + 32.0;

            return station + "," + fahrenheit + "," + humidity;
        });

        // 4. Grouper par station et calculer les moyennes
        KGroupedStream<String, String> groupedByStation = fahrenheitData.groupBy(
                (key, value) -> value.split(",")[0]  // Grouper par station
        );

        // Agrégation pour calculer température et humidité moyennes
        KTable<String, String> stationAverages = groupedByStation.aggregate(
                // Initializer
                () -> "0.0,0.0,0",  // totalTemp,totalHumidity,count

                // Aggregator
                (station, newValue, aggregate) -> {
                    String[] newParts = newValue.split(",");
                    double newTemp = Double.parseDouble(newParts[1]);
                    double newHumidity = Double.parseDouble(newParts[2]);

                    String[] aggParts = aggregate.split(",");
                    double totalTemp = Double.parseDouble(aggParts[0]);
                    double totalHumidity = Double.parseDouble(aggParts[1]);
                    long count = Long.parseLong(aggParts[2]);

                    // Mise à jour des totaux
                    totalTemp += newTemp;
                    totalHumidity += newHumidity;
                    count++;

                    // Calcul des moyennes
                    double avgTemp = totalTemp / count;
                    double avgHumidity = totalHumidity / count;

                    // Mise à jour des métriques Prometheus
                    avgTemperatureGauge.labels(station).set(avgTemp);
                    avgHumidityGauge.labels(station).set(avgHumidity);

                    // Log pour debug
                    System.out.printf("Station: %s | Temp Moyenne: %.2f°F | Humidité Moyenne: %.2f%%%n",
                            station, avgTemp, avgHumidity);

                    return totalTemp + "," + totalHumidity + "," + count;
                },
                Materialized.with(Serdes.String(), Serdes.String())
        );

        // 5. Formater et publier les résultats dans 'station-averages'
        stationAverages.toStream().mapValues((station, aggregate) -> {
            String[] parts = aggregate.split(",");
            double totalTemp = Double.parseDouble(parts[0]);
            double totalHumidity = Double.parseDouble(parts[1]);
            long count = Long.parseLong(parts[2]);

            double avgTemp = totalTemp / count;
            double avgHumidity = totalHumidity / count;

            return String.format("Température Moyenne = %.2f°F, Humidité Moyenne = %.2f%%",
                    avgTemp, avgHumidity);
        }).to("station-averages");

        // ========= Démarrer le serveur HTTP Prometheus =========
        DefaultExports.initialize();
        HTTPServer metricsServer = new HTTPServer(1234);
        System.out.println("Serveur Prometheus démarré sur http://localhost:1234/metrics");

        // ========= Lancement Kafka Streams =========
        KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), prop);
        kafkaStreams.start();

        System.out.println("Application Kafka Streams démarrée...");
        System.out.println("En attente de données sur le topic 'weather-data'");

        // Hook pour arrêt propre
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arrêt de l'application...");
            kafkaStreams.close();
            metricsServer.stop();
            System.out.println("Application arrêtée proprement.");
        }));
    }
}
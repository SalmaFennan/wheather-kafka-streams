Exercice 2 : Analyse de Données Météorologiques avec Kafka Streams
📋 Description
Application Kafka Streams qui traite des données météorologiques en temps réel provenant de plusieurs stations. L'application filtre les températures élevées, convertit en Fahrenheit, calcule des moyennes par station et expose les résultats via Grafana.

🎯 Objectifs

✅ Filtrer les relevés avec température > 30°C
✅ Convertir Celsius → Fahrenheit
✅ Calculer température et humidité moyennes par station
✅ Publier les résultats agrégés
✅ Visualiser avec Grafana et Prometheus


# Topics Kafka
TopicDescriptionFormatweather-dataDonnées brutes des stationsstation,temperature,humiditystation-averagesMoyennes par stationStation: Temp=XX°F, Humidity=YY%

# Format des Données
Entrée (weather-data)
Station1,25.3,60
Station2,35.0,50
Station2,40.0,45
Station1,32.0,70
Format : station,temperature,humidity

station : Identifiant (ex: Station1, Station2)
temperature : Température en °C (ex: 25.3)
humidity : Humidité en % (ex: 60)

Sortie (station-averages)
Station2 : Température Moyenne = 96.80°F, Humidité Moyenne = 47.50%
Station1 : Température Moyenne = 89.60°F, Humidité Moyenne = 70.00%

# Transformations Appliquées
1. Filtrage
   Règle : Température > 30°C
   Exemple :
   Input:
   Station1,25.3,60  ❌ Rejeté (25.3 < 30)
   Station2,35.0,50  ✅ Accepté (35.0 > 30)
   Station2,40.0,45  ✅ Accepté (40.0 > 30)
   Station1,32.0,70  ✅ Accepté (32.0 > 30)

Output:
Station2,35.0,50
Station2,40.0,45
Station1,32.0,70
2. Conversion Celsius → Fahrenheit
   Formule : F = (C × 9/5) + 32
   Exemple :
   Input:
   Station2,35.0,50

Calcul:
35.0 × 9/5 + 32 = 95.0

Output:
Station2,95.0,50
3. Agrégation par Station
   Calcul des moyennes :
   Input (Station2):
   Station2,95.0,50
   Station2,98.6,40

Calcul:
Temp moyenne = (95.0 + 98.6) / 2 = 96.8°F
Humidity moyenne = (50 + 40) / 2 = 45.0%

Output:
Station2 : Température Moyenne = 96.80°F, Humidité Moyenne = 45.00%

# Installation et Démarrage
Prérequis

Java 21
Maven 3.8+
Docker Desktop
IntelliJ IDEA

Étape 1 : Démarrer l'infrastructure
bashcd kafka-infrastructure
docker-compose up -d
Vérifier :
bashdocker ps
Vous devriez voir : broker, prometheus, grafana
Étape 2 : Créer les topics Kafka
bash# Topic weather-data
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--create --topic weather-data \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

# Topic station-averages
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--create --topic station-averages \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

# Vérifier
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--list --bootstrap-server localhost:9092
Étape 3 : Lancer l'application Kafka Streams
bashcd weather-analysis-app
mvn clean install
mvn spring-boot:run
Attendez le message :
✅ Kafka Streams démarré avec succès !
📥 En écoute sur le topic: weather-data
📤 Publication sur le topic: station-averages
Serveur Prometheus démarré sur http://localhost:1234/metrics
Étape 4 : Lancer le producer de données
Dans un autre terminal :
bashcd weather-producer
mvn spring-boot:run

🧪 Tests
Test 1 : Envoyer des données manuellement
Producer Kafka :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-producer.sh \
--topic weather-data \
--bootstrap-server localhost:9092
Tapez ces messages :
Station1,25.3,60
Station2,35.0,50
Station2,40.0,45
Station1,32.0,70
Station3,28.0,55
Station2,38.0,48
Test 2 : Vérifier les messages filtrés
Consumer weather-data :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
--topic weather-data \
--bootstrap-server localhost:9092 \
--from-beginning
Résultat : Tous les messages
Console de l'application : Seulement les messages > 30°C
Station2,35.0,50  ✅
Station2,40.0,45  ✅
Station1,32.0,70  ✅
Station2,38.0,48  ✅
Test 3 : Vérifier les résultats agrégés
Consumer station-averages :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
--topic station-averages \
--bootstrap-server localhost:9092 \
--from-beginning
Résultat attendu :
Station2 : Température Moyenne = 95.00°F, Humidité Moyenne = 50.00%
Station1 : Température Moyenne = 89.60°F, Humidité Moyenne = 70.00%
Station2 : Température Moyenne = 96.80°F, Humidité Moyenne = 47.50%
Station2 : Température Moyenne = 97.07°F, Humidité Moyenne = 47.67%

📊 Visualisation avec Grafana
Étape 1 : Accéder à Grafana
Ouvrez : http://localhost:3000

Username: admin
Password: admin

Étape 2 : Ajouter Prometheus comme Data Source

⚙️ Connections → Data sources
Add data source
Sélectionnez Prometheus
URL : http://prometheus:9090

# Résultats
![docker-compose.png](screenshots/docker-compose.png)
![topics.png](screenshots/topics.png)
![app_demaree.png](screenshots/app_demaree.png)
![envoie_donnee_vers_weather_data.png](screenshots/envoie_donnee_vers_weather_data.png)
![grafana.png](screenshots/grafana.png)
![grafana2.png](screenshots/grafana2.png)
![grafana3.png](screenshots/grafana3.png)
![grafana4.png](screenshots/grafana4.png)
![grafana5.png](screenshots/grafana5.png)
![grafana6.png](screenshots/grafana6.png)

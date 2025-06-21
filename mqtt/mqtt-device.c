#include "contiki.h"
#include "mqtt.h"
#include "net/ipv6/uip.h"
#include "net/routing/routing.h"
#include "sys/etimer.h"
#include "dev/leds.h"
#include "os/sys/log.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define LOG_MODULE "mqtt-device"
#define LOG_LEVEL LOG_LEVEL_DBG

#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
#define DEFAULT_BROKER_PORT 1883

#define PUBLISH_INTERVAL_TEMP (30 * CLOCK_SECOND)
#define PUBLISH_INTERVAL_HUM  (45 * CLOCK_SECOND)
#define PUBLISH_INTERVAL_FILL (60 * CLOCK_SECOND)

extern int current_temperature;

static struct mqtt_connection conn;
static char client_id[32];
static char pub_topic[64];
static char msg[128];

PROCESS(mqtt_device_process, "MQTT Device Process");
PROCESS(temp_process, "Temperature Publisher");
PROCESS(hum_process, "Humidity Publisher");
PROCESS(fill_process, "Fill Level Publisher");
AUTOSTART_PROCESSES(&mqtt_device_process);

static int temperature = 25;
static int humidity = 60;
static int fill_level = 40;

static bool connected = false;

static void mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data) {
  switch(event) {
    case MQTT_EVENT_CONNECTED:
      LOG_INFO("MQTT connected\n");
      connected = true;
      break;
    case MQTT_EVENT_DISCONNECTED:
      LOG_INFO("MQTT disconnected\n");
      connected = false;
      break;
    default:
      break;
  }
}

PROCESS_THREAD(mqtt_device_process, ev, data) {
  PROCESS_BEGIN();

  snprintf(client_id, sizeof(client_id), "device-%02x%02x", linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);
  mqtt_register(&conn, &mqtt_device_process, client_id, mqtt_event, 64);

  if(uip_ds6_get_global(ADDR_PREFERRED) != NULL) {
    mqtt_connect(&conn, MQTT_CLIENT_BROKER_IP_ADDR, DEFAULT_BROKER_PORT, 60 * CLOCK_SECOND, MQTT_CLEAN_SESSION_ON);
  }

  while(!connected) {
    PROCESS_PAUSE();
  }

  process_start(&temp_process, NULL);
  process_start(&hum_process, NULL);
  process_start(&fill_process, NULL);

  PROCESS_END();
}

PROCESS_THREAD(temp_process, ev, data) {
  static struct etimer et;
  PROCESS_BEGIN();

  etimer_set(&et, 5 * CLOCK_SECOND); // Offset
  PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

  while(1) {
    temperature += (rand() % 3 - 1); // +-1
    snprintf(pub_topic, sizeof(pub_topic), "temperature");
    snprintf(msg, sizeof(msg), "{\"temperature\":%d}", temperature);
    mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)msg, strlen(msg), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
    LOG_INFO("Published temperature: %s\n", msg);

    etimer_set(&et, PUBLISH_INTERVAL_TEMP);
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
  }
  PROCESS_END();
}

PROCESS_THREAD(hum_process, ev, data) {
  static struct etimer et;
  PROCESS_BEGIN();

  etimer_set(&et, 10 * CLOCK_SECOND); // Offset
  PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

  while(1) {
    humidity += (rand() % 3 - 1); // +-1
    snprintf(pub_topic, sizeof(pub_topic), "humidity");
    snprintf(msg, sizeof(msg), "{\"humidity\":%d}", humidity);
    mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)msg, strlen(msg), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
    LOG_INFO("Published humidity: %s\n", msg);

    etimer_set(&et, PUBLISH_INTERVAL_HUM);
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
  }
  PROCESS_END();
}

PROCESS_THREAD(fill_process, ev, data) {
  static struct etimer et;
  PROCESS_BEGIN();

  etimer_set(&et, 15 * CLOCK_SECOND); // Offset
  PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

  while(1) {
    fill_level = (fill_level + 10) % 110; // Cycle 0 -> 100
    snprintf(pub_topic, sizeof(pub_topic), "fill_level");
    snprintf(msg, sizeof(msg), "{\"fill_level\":%d}", fill_level);
    mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)msg, strlen(msg), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
    LOG_INFO("Published fill_level: %s\n", msg);

    etimer_set(&et, PUBLISH_INTERVAL_FILL);
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
  }
  PROCESS_END();
}

#include "contiki.h"
#include "net/routing/routing.h"
#include "mqtt.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "lib/sensors.h"
#include "dev/button-hal.h"
#include "dev/leds.h"
#include "os/sys/log.h"
#include "mqtt-client.h"
#include <string.h>

#define LOG_MODULE "mqtt-client"
#ifdef MQTT_CLIENT_CONF_LOG_LEVEL
#define LOG_LEVEL MQTT_CLIENT_CONF_LOG_LEVEL
#else
#define LOG_LEVEL LOG_LEVEL_DBG
#endif

/* MQTT broker address (hardcoded here for simplicity) */
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"
static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

/* Default MQTT config values */
#define DEFAULT_BROKER_PORT 1883
#define DEFAULT_PUBLISH_INTERVAL (30 * CLOCK_SECOND)

/* MQTT settings */
#define MAX_TCP_SEGMENT_SIZE 32
#define CONFIG_IP_ADDR_STR_LEN 64

/* Buffers */
#define BUFFER_SIZE 64
static char client_id[BUFFER_SIZE];
static char pub_topic[BUFFER_SIZE];
static char sub_topic[BUFFER_SIZE];

#define SENSOR_PERIOD 10
static struct ctimer temp_timer;

#define APP_BUFFER_SIZE 128
static char app_buffer[APP_BUFFER_SIZE];

static struct mqtt_connection conn;
mqtt_status_t status;
char broker_address[CONFIG_IP_ADDR_STR_LEN];

/* Periodic timer for state machine */
#define STATE_MACHINE_PERIODIC (CLOCK_SECOND >> 1)
static struct etimer periodic_timer;

/* MQTT client states */
static uint8_t state;
#define STATE_INIT          0
#define STATE_NET_OK        1
#define STATE_CONNECTING    2
#define STATE_CONNECTED     3
#define STATE_SUBSCRIBED    4
#define STATE_DISCONNECTED  5

PROCESS(mqtt_client_temp, "MQTT Temperature Sensor");
AUTOSTART_PROCESSES(&mqtt_client_temp);

/* Temperature simulation vars */
static uint8_t min_temperature = 18;
static uint8_t max_temperature = 28;
static uint8_t start_temperature = 23;
static int temperature = 23;
static bool actuator_on = false;
static bool warming = true;
static bool first_sensing = true;

/* Simulated sensing function */
static void
sense_temperature(void *ptr)
{
  if(!actuator_on) {
    if(warming) {
      temperature += 2;
      if(temperature > max_temperature) {
        actuator_on = true;
      }
    } else {
      temperature -= 2;
      if(temperature < min_temperature) {
        actuator_on = true;
      }
    }
  } else {
    if(warming) {
      temperature -= 2;
      if(temperature == start_temperature) {
        actuator_on = false;
        warming = !warming;
      }
    } else {
      temperature += 2;
      if(temperature == start_temperature) {
        actuator_on = false;
        warming = !warming;
      }
    }
  }

  snprintf(pub_topic, sizeof(pub_topic), "temperature");
  snprintf(app_buffer, sizeof(app_buffer), "{ \"value\": %d }", temperature);

  mqtt_publish(&conn, NULL, pub_topic,
               (uint8_t *)app_buffer,
               strlen(app_buffer),
               MQTT_QOS_LEVEL_0,
               MQTT_RETAIN_OFF);

  ctimer_set(&temp_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_temperature, NULL);
}

/* Callback for MQTT messages on subscribed topics */
static void
pub_handler(const char *topic, uint16_t topic_len,
            const uint8_t *chunk, uint16_t chunk_len)
{
  LOG_INFO("Temperature Pub Handler: topic=%.*s\n", topic_len, topic);
  // Here you could parse chunk to act on commands, e.g., adjust min/max temperature
}

/* MQTT event callback */
static void
mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data)
{
  switch(event) {
  case MQTT_EVENT_CONNECTED:
    LOG_INFO("MQTT connected\n");
    state = STATE_CONNECTED;
    break;

  case MQTT_EVENT_DISCONNECTED:
    LOG_ERR("MQTT disconnected (reason %u)\n", *((mqtt_event_t *)data));
    state = STATE_DISCONNECTED;
    process_poll(&mqtt_client_temp);
    break;

  case MQTT_EVENT_PUBLISH: {
    struct mqtt_message *msg = data;
    pub_handler(msg->topic, strlen(msg->topic), msg->payload_chunk, msg->payload_length);
    break;
  }

  case MQTT_EVENT_SUBACK:
    LOG_INFO("MQTT subscription acknowledged\n");
    break;

  case MQTT_EVENT_UNSUBACK:
    LOG_INFO("MQTT unsubscribe acknowledged\n");
    break;

  case MQTT_EVENT_PUBACK:
    LOG_INFO("MQTT publish acknowledged\n");
    break;

  default:
    LOG_WARN("Unhandled MQTT event %d\n", event);
    break;
  }
}

/* Check network connectivity */
static bool
have_connectivity(void)
{
  return (uip_ds6_get_global(ADDR_PREFERRED) != NULL &&
          uip_ds6_defrt_choose() != NULL);
}

PROCESS_THREAD(mqtt_client_temp, ev, data)
{
  PROCESS_BEGIN();

  LOG_INFO("Starting MQTT temperature sensor client\n");

  /* Generate client ID from MAC */
  snprintf(client_id, sizeof(client_id), "%02x%02x%02x%02x%02x%02x",
           linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
           linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
           linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

  mqtt_register(&conn, &mqtt_client_temp, client_id, mqtt_event, MAX_TCP_SEGMENT_SIZE);

  state = STATE_INIT;
  etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);

  while(1) {
    PROCESS_YIELD();

    if((ev == PROCESS_EVENT_TIMER && data == &periodic_timer) || ev == PROCESS_EVENT_POLL) {

      switch(state) {
      case STATE_INIT:
        if(have_connectivity()) {
          state = STATE_NET_OK;
        }
        break;

      case STATE_NET_OK:
        LOG_INFO("Connecting to MQTT broker at %s\n", broker_ip);
        strncpy(broker_address, broker_ip, sizeof(broker_address));
        mqtt_connect(&conn, broker_address, DEFAULT_BROKER_PORT,
                     (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
                     MQTT_CLEAN_SESSION_ON);
        state = STATE_CONNECTING;
        break;

      case STATE_CONNECTED:
        strcpy(sub_topic, "actuator_temp");
        status = mqtt_subscribe(&conn, NULL, sub_topic, MQTT_QOS_LEVEL_0);
        if(status == MQTT_STATUS_OUT_QUEUE_FULL) {
          LOG_ERR("MQTT command queue full when subscribing\n");
          PROCESS_EXIT();
        }
        LOG_INFO("Subscribed to topic: %s\n", sub_topic);
        state = STATE_SUBSCRIBED;
        break;

      case STATE_SUBSCRIBED:
        if(first_sensing) {
          ctimer_set(&temp_timer, SENSOR_PERIOD * CLOCK_SECOND, sense_temperature, NULL);
          first_sensing = false;
        }
        break;

      case STATE_DISCONNECTED:
        LOG_ERR("MQTT disconnected, restarting...\n");
        state = STATE_INIT;
        break;

      default:
        break;
      }

      etimer_set(&periodic_timer, STATE_MACHINE_PERIODIC);
    }
  }

  PROCESS_END();
}

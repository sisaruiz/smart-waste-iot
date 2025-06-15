#include "contiki.h"
#include "mqtt.h"
#include "dev/button-hal.h"
#include "os/sys/log.h"
#include <string.h>

#define LOG_MODULE "mqtt-button"
#define LOG_LEVEL LOG_LEVEL_DBG

#define MQTT_BROKER_IP "fd00::1"
#define BROKER_PORT 1883
#define BUFFER_SIZE 64

// MQTT connection object
static struct mqtt_connection conn;

// Buffers for client ID, topic, and message payload
static char client_id[BUFFER_SIZE];
static char pub_topic[BUFFER_SIZE];
static char app_buffer[BUFFER_SIZE];

// MQTT connection state machine
static uint8_t state;
#define STATE_INIT          0
#define STATE_NET_OK        1
#define STATE_CONNECTING    2
#define STATE_CONNECTED     3
#define STATE_DISCONNECTED  4

PROCESS(mqtt_client_button, "MQTT Button Sensor");
AUTOSTART_PROCESSES(&mqtt_client_button);

// Check if the node has IPv6 network connectivity
static bool have_connectivity(void) {
  return uip_ds6_get_global(ADDR_PREFERRED) != NULL && uip_ds6_defrt_choose() != NULL;
}

// MQTT event callback function
static void mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data) {
  switch(event) {
    case MQTT_EVENT_CONNECTED:
      LOG_INFO("MQTT connected\n");
      state = STATE_CONNECTED;
      break;

    case MQTT_EVENT_DISCONNECTED:
      LOG_ERR("MQTT disconnected\n");
      state = STATE_DISCONNECTED;
      process_poll(&mqtt_client_button); // trigger reconnect attempt
      break;

    case MQTT_EVENT_PUBLISH:
      // Not subscribing to any topics here, so ignore
      break;

    default:
      LOG_INFO("Unhandled MQTT event: %d\n", event);
      break;
  }
}

PROCESS_THREAD(mqtt_client_button, ev, data) {
  PROCESS_BEGIN();

  // Prepare a unique client ID based on MAC address
  snprintf(client_id, sizeof(client_id), "%02x%02x%02x%02x%02x%02x",
           linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
           linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
           linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

  // Register MQTT connection with callback
  mqtt_register(&conn, &mqtt_client_button, client_id, mqtt_event, 32);

  state = STATE_INIT;

  while(1) {
    PROCESS_YIELD();

    // Handle periodic connection management and reconnection
    if(ev == PROCESS_EVENT_POLL || ev == PROCESS_EVENT_TIMER) {
      if(state == STATE_INIT) {
        if(have_connectivity()) {
          state = STATE_NET_OK;
          LOG_INFO("Network connectivity ready\n");
        }
      }
      if(state == STATE_NET_OK) {
        LOG_INFO("Connecting to MQTT broker at %s\n", MQTT_BROKER_IP);
        mqtt_connect(&conn, MQTT_BROKER_IP, BROKER_PORT, 60, MQTT_CLEAN_SESSION_ON);
        state = STATE_CONNECTING;
      }
      if(state == STATE_DISCONNECTED) {
        LOG_WARN("Reconnecting MQTT...\n");
        state = STATE_INIT;
      }
    }

    // Handle button press events
    if(ev == button_hal_press_event) {
      if(state == STATE_CONNECTED) {
        snprintf(pub_topic, sizeof(pub_topic), "button");
        snprintf(app_buffer, sizeof(app_buffer), "{ \"button\": \"pressed\" }");
        mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)app_buffer, strlen(app_buffer),
                     MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
        LOG_INFO("Published button press event\n");
      } else {
        LOG_WARN("Button pressed but MQTT not connected yet\n");
      }
    }
  }

  PROCESS_END();
}
